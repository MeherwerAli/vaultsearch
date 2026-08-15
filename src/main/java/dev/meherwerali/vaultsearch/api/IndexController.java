package dev.meherwerali.vaultsearch.api;

import dev.meherwerali.vaultsearch.persistence.IndexTaskRepository;
import dev.meherwerali.vaultsearch.persistence.NoteMetadataRepository;
import dev.meherwerali.vaultsearch.persistence.TaskStatus;
import dev.meherwerali.vaultsearch.search.VaultSearchService;
import dev.meherwerali.vaultsearch.sync.IndexRuntimeState;
import dev.meherwerali.vaultsearch.sync.ReconciliationCoordinator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/index")
public class IndexController {

    private final VaultSearchService searchService;
    private final NoteMetadataRepository notes;
    private final IndexTaskRepository tasks;
    private final IndexRuntimeState runtimeState;
    private final ReconciliationCoordinator coordinator;

    public IndexController(VaultSearchService searchService, NoteMetadataRepository notes,
                           IndexTaskRepository tasks, IndexRuntimeState runtimeState,
                           ReconciliationCoordinator coordinator) {
        this.searchService = searchService;
        this.notes = notes;
        this.tasks = tasks;
        this.runtimeState = runtimeState;
        this.coordinator = coordinator;
    }

    @GetMapping("/status")
    IndexStatus status() {
        return new IndexStatus(
                searchService.currentState(),
                notes.countByDeletedFalse(),
                tasks.countByStatusIn(Set.of(TaskStatus.PENDING, TaskStatus.PROCESSING)),
                tasks.countByStatus(TaskStatus.DEAD),
                runtimeState.lastReconciledAt(),
                runtimeState.lastSuccessfulIndexAt()
        );
    }

    @PostMapping("/reconcile")
    ResponseEntity<QueuedRun> reconcile() {
        UUID runId = coordinator.queue();
        return ResponseEntity.accepted().body(new QueuedRun(runId, "queued"));
    }

    public record IndexStatus(String state, long indexedNotes, long pendingTasks, long deadTasks,
                              Instant lastReconciledAt, Instant lastSuccessfulIndexAt) {
    }

    public record QueuedRun(UUID runId, String state) {
    }
}
