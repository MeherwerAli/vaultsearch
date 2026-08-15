package dev.meherwerali.vaultsearch.sync;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import dev.meherwerali.vaultsearch.source.VaultSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.LINUX)
class VaultWatcherTest {

    @TempDir
    Path vaultRoot;

    @Test
    void debouncesNativeCreateAndDeleteEventsIntoReconciliationHints() throws Exception {
        VaultSearchProperties properties = new VaultSearchProperties();
        properties.setWatcherEnabled(true);
        properties.setWatcherDebounce(Duration.ofMillis(100));
        VaultSource source = mock(VaultSource.class);
        when(source.root()).thenReturn(vaultRoot);
        ReconciliationCoordinator coordinator = mock(ReconciliationCoordinator.class);
        VaultWatcher watcher = new VaultWatcher(properties, source, coordinator);

        try {
            watcher.start();
            Thread.sleep(500);
            Path note = vaultRoot.resolve("watcher.md");
            Files.writeString(note, "# Watcher");
            verify(coordinator, timeout(3_000).atLeastOnce()).hint();

            clearInvocations(coordinator);
            Files.delete(note);
            verify(coordinator, timeout(3_000).atLeastOnce()).hint();
        } finally {
            watcher.shutdown();
        }
    }
}
