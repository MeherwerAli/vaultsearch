package dev.meherwerali.vaultsearch.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import dev.meherwerali.vaultsearch.persistence.IndexState;
import dev.meherwerali.vaultsearch.persistence.IndexTaskEntity;
import dev.meherwerali.vaultsearch.persistence.IndexTaskRepository;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataEntity;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataRepository;
import dev.meherwerali.vaultsearch.persistence.TaskOperation;
import dev.meherwerali.vaultsearch.persistence.TaskStatus;
import dev.meherwerali.vaultsearch.search.NoteSearchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexTaskWorkerTest {

    @Test
    void retriesDependencyFailuresAndMovesTheTaskToDeadStateAtTheConfiguredBound() {
        VaultSearchProperties properties = new VaultSearchProperties();
        properties.setMaxIndexAttempts(3);
        IndexTaskRepository tasks = mock(IndexTaskRepository.class);
        NoteMetadataRepository notes = mock(NoteMetadataRepository.class);
        NoteSearchRepository search = mock(NoteSearchRepository.class);
        Instant now = Instant.now();
        String noteId = "a".repeat(64);
        NoteMetadataEntity metadata = new NoteMetadataEntity(noteId, UUID.randomUUID(), "note.md", "Note",
                "b".repeat(64), "c".repeat(64), now, now);
        IndexTaskEntity task = new IndexTaskEntity(noteId, TaskOperation.UPSERT, "c".repeat(64), "{}", now);
        when(tasks.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(task));
        when(notes.findById(noteId)).thenReturn(Optional.of(metadata));
        when(search.save(any())).thenThrow(new IllegalStateException("sensitive dependency detail"));

        IndexTaskWorker worker = new IndexTaskWorker(properties, tasks, notes, search, new ObjectMapper(),
                new IndexRuntimeState(), new SimpleMeterRegistry());

        worker.processDueTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(metadata.getIndexState()).isEqualTo(IndexState.FAILED);

        worker.processDueTasks();
        worker.processDueTasks();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(task.getAttempts()).isEqualTo(3);
    }
}
