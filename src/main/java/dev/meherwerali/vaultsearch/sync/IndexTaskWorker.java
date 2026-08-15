package dev.meherwerali.vaultsearch.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import dev.meherwerali.vaultsearch.persistence.IndexTaskEntity;
import dev.meherwerali.vaultsearch.persistence.IndexTaskRepository;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataEntity;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataRepository;
import dev.meherwerali.vaultsearch.persistence.TaskOperation;
import dev.meherwerali.vaultsearch.persistence.TaskStatus;
import dev.meherwerali.vaultsearch.search.NoteSearchDocument;
import dev.meherwerali.vaultsearch.search.NoteSearchRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IndexTaskWorker {

    private final VaultSearchProperties properties;
    private final IndexTaskRepository tasks;
    private final NoteMetadataRepository notes;
    private final NoteSearchRepository searchRepository;
    private final ObjectMapper objectMapper;
    private final IndexRuntimeState runtimeState;
    private final Counter indexedCounter;
    private final Counter failedCounter;

    public IndexTaskWorker(VaultSearchProperties properties, IndexTaskRepository tasks,
                           NoteMetadataRepository notes, NoteSearchRepository searchRepository,
                           ObjectMapper objectMapper, IndexRuntimeState runtimeState, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.tasks = tasks;
        this.notes = notes;
        this.searchRepository = searchRepository;
        this.objectMapper = objectMapper;
        this.runtimeState = runtimeState;
        this.indexedCounter = meterRegistry.counter("vaultsearch.index.tasks.succeeded");
        this.failedCounter = meterRegistry.counter("vaultsearch.index.tasks.failed");
    }

    @Scheduled(fixedDelayString = "${vaultsearch.index-worker-delay:1s}")
    @Transactional
    public void scheduledProcessDueTasks() {
        if (properties.isScheduledIndexWorkerEnabled()) {
            processBatch();
        }
    }

    @Transactional
    public int processDueTasks() {
        return processBatch();
    }

    private int processBatch() {
        Instant now = Instant.now();
        List<IndexTaskEntity> due = tasks.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of(TaskStatus.PENDING), now);
        due.forEach(task -> process(task, now));
        return due.size();
    }

    private void process(IndexTaskEntity task, Instant now) {
        task.markProcessing(now);
        try {
            NoteMetadataEntity metadata = notes.findById(task.getNoteId())
                    .orElseThrow(() -> new IllegalStateException("Missing note metadata"));
            if (task.getOperation() == TaskOperation.DELETE) {
                searchRepository.deleteById(task.getNoteId());
                metadata.markDeleteProjected(now);
            } else {
                NoteSearchDocument document = deserialize(task.getPayloadJson());
                searchRepository.save(document);
                metadata.markIndexed(now);
            }
            task.markSucceeded(now);
            indexedCounter.increment();
            runtimeState.indexingSucceeded(now);
        } catch (RuntimeException exception) {
            failedCounter.increment();
            String code = exception.getClass().getSimpleName();
            String hash = Hashing.sha256(String.valueOf(exception.getMessage()));
            int nextAttempt = task.getAttempts() + 1;
            notes.findById(task.getNoteId()).ifPresent(note -> note.markFailed(now));
            if (nextAttempt >= properties.getMaxIndexAttempts()) {
                task.markDead(code, hash, now);
            } else {
                long backoffSeconds = Math.min(300, 1L << Math.min(8, task.getAttempts()));
                task.markRetry(code, hash, now.plus(Duration.ofSeconds(backoffSeconds)), now);
            }
        }
    }

    private NoteSearchDocument deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, NoteSearchDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid search projection payload", exception);
        }
    }
}
