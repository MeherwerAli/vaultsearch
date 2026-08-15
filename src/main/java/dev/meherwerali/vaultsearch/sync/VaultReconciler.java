package dev.meherwerali.vaultsearch.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import dev.meherwerali.vaultsearch.domain.ParsedNote;
import dev.meherwerali.vaultsearch.domain.SourceNote;
import dev.meherwerali.vaultsearch.parser.NoteParser;
import dev.meherwerali.vaultsearch.persistence.IndexState;
import dev.meherwerali.vaultsearch.persistence.IndexTaskEntity;
import dev.meherwerali.vaultsearch.persistence.IndexTaskRepository;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataEntity;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataRepository;
import dev.meherwerali.vaultsearch.persistence.TaskOperation;
import dev.meherwerali.vaultsearch.persistence.TaskStatus;
import dev.meherwerali.vaultsearch.persistence.VaultEntity;
import dev.meherwerali.vaultsearch.persistence.VaultRepository;
import dev.meherwerali.vaultsearch.search.NoteSearchDocument;
import dev.meherwerali.vaultsearch.source.VaultSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaultReconciler {

    private final VaultSearchProperties properties;
    private final VaultSource source;
    private final NoteParser parser;
    private final LinkResolver linkResolver;
    private final VaultRepository vaults;
    private final NoteMetadataRepository notes;
    private final IndexTaskRepository tasks;
    private final ObjectMapper objectMapper;
    private final IndexRuntimeState runtimeState;
    private final Timer scanTimer;
    private final Counter parseFailures;
    private final Counter unresolvedLinks;

    public VaultReconciler(VaultSearchProperties properties, VaultSource source, NoteParser parser,
                           LinkResolver linkResolver, VaultRepository vaults, NoteMetadataRepository notes,
                           IndexTaskRepository tasks, ObjectMapper objectMapper, IndexRuntimeState runtimeState,
                           MeterRegistry meterRegistry) {
        this.properties = properties;
        this.source = source;
        this.parser = parser;
        this.linkResolver = linkResolver;
        this.vaults = vaults;
        this.notes = notes;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
        this.runtimeState = runtimeState;
        this.scanTimer = meterRegistry.timer("vaultsearch.reconciliation.duration");
        this.parseFailures = meterRegistry.counter("vaultsearch.parse.failures");
        this.unresolvedLinks = meterRegistry.counter("vaultsearch.links.unresolved");
    }

    @Transactional
    public ReconciliationResult reconcile() throws IOException {
        Timer.Sample sample = Timer.start();
        try {
            return reconcileWithinTransaction();
        } finally {
            sample.stop(scanTimer);
        }
    }

    private ReconciliationResult reconcileWithinTransaction() throws IOException {
        Instant now = Instant.now();
        VaultEntity vault = upsertVault(now);
        List<SourceNote> discovered = source.scan();
        Set<String> seenPaths = new HashSet<>();
        List<ParsedNote> parsed = new ArrayList<>();
        int skipped = 0;
        for (SourceNote sourceNote : discovered) {
            seenPaths.add(sourceNote.relativePath());
            try {
                parsed.add(parser.parse(sourceNote));
            } catch (RuntimeException | IOException exception) {
                parseFailures.increment();
                skipped++;
            }
        }

        List<ResolvedNote> resolvedNotes = linkResolver.resolve(properties.getVaultKey(), parsed);
        int upserts = 0;
        for (ResolvedNote resolved : resolvedNotes) {
            unresolvedLinks.increment(resolved.unresolvedLinks().size());
            NoteMetadataEntity metadata = notes.findById(resolved.noteId()).orElse(null);
            boolean projectionChanged = metadata == null
                    || !resolved.projectionHash().equals(metadata.getProjectionHash())
                    || metadata.isDeleted()
                    || metadata.getIndexState() == IndexState.FAILED;
            if (metadata == null) {
                metadata = new NoteMetadataEntity(
                        resolved.noteId(), vault.getId(), resolved.note().relativePath(), resolved.note().title(),
                        resolved.note().sourceHash(), resolved.projectionHash(), resolved.note().modifiedAt(), now);
                notes.save(metadata);
            } else if (projectionChanged) {
                metadata.refresh(resolved.note().title(), resolved.note().sourceHash(), resolved.projectionHash(),
                        resolved.note().modifiedAt(), now);
            }
            IndexTaskEntity existingTask = tasks.findById(resolved.noteId()).orElse(null);
            if (projectionChanged || existingTask == null || existingTask.getStatus() == TaskStatus.DEAD) {
                String payload = serialize(NoteSearchDocument.from(vault.getId().toString(), resolved));
                enqueue(existingTask, resolved.noteId(), TaskOperation.UPSERT, resolved.projectionHash(), payload, now);
                upserts++;
            }
        }

        int deletes = 0;
        for (NoteMetadataEntity existing : notes.findAllByVaultId(vault.getId())) {
            if (!existing.isDeleted() && !seenPaths.contains(existing.getRelativePath())) {
                existing.markDeleted(now);
                IndexTaskEntity existingTask = tasks.findById(existing.getId()).orElse(null);
                enqueue(existingTask, existing.getId(), TaskOperation.DELETE, existing.getProjectionHash(), null, now);
                deletes++;
            }
        }

        runtimeState.reconciliationSucceeded(now);
        return new ReconciliationResult(discovered.size(), parsed.size(), skipped, upserts, deletes);
    }

    private VaultEntity upsertVault(Instant now) {
        String rootFingerprint = Hashing.sha256(source.root().toString());
        VaultEntity vault = vaults.findByVaultKey(properties.getVaultKey()).orElseGet(() ->
                new VaultEntity(
                        UUID.nameUUIDFromBytes(("vault:" + properties.getVaultKey()).getBytes(StandardCharsets.UTF_8)),
                        properties.getVaultKey(), properties.getDisplayName(), rootFingerprint, now));
        vault.refresh(properties.getDisplayName(), rootFingerprint, now);
        return vaults.save(vault);
    }

    private void enqueue(IndexTaskEntity existingTask, String noteId, TaskOperation operation,
                         String expectedHash, String payload, Instant now) {
        if (existingTask == null) {
            tasks.save(new IndexTaskEntity(noteId, operation, expectedHash, payload, now));
        } else {
            existingTask.requeue(operation, expectedHash, payload, now);
        }
    }

    private String serialize(NoteSearchDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize search projection", exception);
        }
    }
}
