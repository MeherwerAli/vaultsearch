package dev.meherwerali.vaultsearch.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ReconciliationCoordinatorTest {

    @Test
    void duplicateApiRequestsShareTheActiveRunWithoutAddingWork() throws Exception {
        VaultReconciler reconciler = mock(VaultReconciler.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(reconciler.reconcile()).thenAnswer(invocation -> {
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
            return new ReconciliationResult(0, 0, 0, 0, 0);
        });
        ReconciliationCoordinator coordinator = coordinator(reconciler);

        try {
            UUID first = coordinator.queue();
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.queue()).isEqualTo(first);
            release.countDown();
            verify(reconciler, timeout(3_000).times(1)).reconcile();
        } finally {
            release.countDown();
            coordinator.stop();
        }
    }

    @Test
    void watcherHintDuringAnActiveRunCoalescesOneFollowUpRun() throws Exception {
        VaultReconciler reconciler = mock(VaultReconciler.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(reconciler.reconcile()).thenAnswer(invocation -> {
            firstStarted.countDown();
            releaseFirst.await(3, TimeUnit.SECONDS);
            return new ReconciliationResult(0, 0, 0, 0, 0);
        });
        ReconciliationCoordinator coordinator = coordinator(reconciler);

        try {
            coordinator.queue();
            assertThat(firstStarted.await(3, TimeUnit.SECONDS)).isTrue();
            coordinator.hint();
            coordinator.hint();
            releaseFirst.countDown();
            verify(reconciler, timeout(3_000).times(2)).reconcile();
            verify(reconciler, atMost(2)).reconcile();
        } finally {
            releaseFirst.countDown();
            coordinator.stop();
        }
    }

    private ReconciliationCoordinator coordinator(VaultReconciler reconciler) {
        return new ReconciliationCoordinator(reconciler, new IndexRuntimeState(), new VaultSearchProperties());
    }
}
