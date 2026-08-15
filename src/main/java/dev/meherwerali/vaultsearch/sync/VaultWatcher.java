package dev.meherwerali.vaultsearch.sync;

import dev.meherwerali.vaultsearch.config.VaultSearchProperties;
import dev.meherwerali.vaultsearch.source.VaultSource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class VaultWatcher implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(VaultWatcher.class);

    private final VaultSearchProperties properties;
    private final VaultSource source;
    private final ReconciliationCoordinator coordinator;
    private final ExecutorService watchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vaultsearch-watcher");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vaultsearch-watcher-debounce");
        thread.setDaemon(true);
        return thread;
    });
    private volatile WatchService watchService;
    private volatile ScheduledFuture<?> debounce;
    private volatile boolean running;

    public VaultWatcher(VaultSearchProperties properties, VaultSource source,
                        ReconciliationCoordinator coordinator) {
        this.properties = properties;
        this.source = source;
        this.coordinator = coordinator;
    }

    @Override
    public void start() {
        if (!properties.isWatcherEnabled() || running) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerRecursively(source.root());
            running = true;
            watchExecutor.execute(this::watchLoop);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Filesystem watching is unavailable; periodic reconciliation remains active ({})",
                    exception.getClass().getSimpleName());
            closeWatchService();
        }
    }

    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                Path directory = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        scheduleReconciliation();
                        continue;
                    }
                    Path changed = directory.resolve((Path) event.context());
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                            && Files.isDirectory(changed, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(changed)) {
                        registerRecursively(changed);
                    }
                    scheduleReconciliation();
                }
                if (!key.reset()) {
                    scheduleReconciliation();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException exception) {
                return;
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Filesystem watch event could not be processed ({})",
                        exception.getClass().getSimpleName());
                scheduleReconciliation();
            }
        }
    }

    private void registerRecursively(Path root) throws IOException {
        try (var directories = Files.walk(root)) {
            for (Path directory : directories
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> root.equals(path) || !containsHiddenSegment(root.relativize(path)))
                    .toList()) {
                directory.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            }
        }
    }

    private boolean containsHiddenSegment(Path relative) {
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private synchronized void scheduleReconciliation() {
        if (debounce != null) {
            debounce.cancel(false);
        }
        debounce = debounceExecutor.schedule(coordinator::hint,
                properties.getWatcherDebounce().toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        running = false;
        closeWatchService();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @PreDestroy
    void shutdown() {
        stop();
        watchExecutor.shutdownNow();
        debounceExecutor.shutdownNow();
    }

    private void closeWatchService() {
        WatchService current = watchService;
        watchService = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // The service is already unavailable; periodic reconciliation remains the correctness backstop.
            }
        }
    }
}
