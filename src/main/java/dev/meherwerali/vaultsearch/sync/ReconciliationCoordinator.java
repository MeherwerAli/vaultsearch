package dev.meherwerali.vaultsearch.sync;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationCoordinator {

    private final VaultReconciler reconciler;
    private final IndexRuntimeState runtimeState;
    private final VaultSearchProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vaultsearch-reconciler");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();
    private boolean running;
    private boolean rerunRequested;
    private UUID activeRun;

    public ReconciliationCoordinator(VaultReconciler reconciler, IndexRuntimeState runtimeState,
                                     VaultSearchProperties properties) {
        this.reconciler = reconciler;
        this.runtimeState = runtimeState;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (properties.isStartupReconcileEnabled()) {
            queue();
        }
    }

    @Scheduled(
            fixedDelayString = "${vaultsearch.reconcile-interval:60s}",
            initialDelayString = "${vaultsearch.reconcile-interval:60s}")
    public void scheduledReconciliation() {
        hint();
    }

    public UUID queue() {
        synchronized (stateLock) {
            if (running) {
                return activeRun;
            }
            return startRunLocked();
        }
    }

    public void hint() {
        synchronized (stateLock) {
            if (running) {
                rerunRequested = true;
            } else {
                startRunLocked();
            }
        }
    }

    private UUID startRunLocked() {
        UUID runId = UUID.randomUUID();
        activeRun = runId;
        running = true;
        executor.submit(() -> runReconciliationLoop(runId));
        return runId;
    }

    private void runReconciliationLoop(UUID runId) {
        while (true) {
            try {
                reconciler.reconcile();
            } catch (Exception exception) {
                runtimeState.reconciliationFailed(exception);
            }
            synchronized (stateLock) {
                if (rerunRequested) {
                    rerunRequested = false;
                    continue;
                }
                running = false;
                if (runId.equals(activeRun)) {
                    activeRun = null;
                }
                return;
            }
        }
    }

    public boolean isRunning() {
        synchronized (stateLock) {
            return running;
        }
    }

    public Instant lastReconciledAt() {
        return runtimeState.lastReconciledAt();
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
