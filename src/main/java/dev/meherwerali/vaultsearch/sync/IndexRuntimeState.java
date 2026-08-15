package dev.meherwerali.vaultsearch.sync;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class IndexRuntimeState {

    private final AtomicReference<Instant> lastReconciledAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulIndexAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailureCode = new AtomicReference<>();

    public void reconciliationSucceeded(Instant at) {
        lastReconciledAt.set(at);
        lastFailureCode.set(null);
    }

    public void reconciliationFailed(Throwable failure) {
        lastFailureCode.set(failure.getClass().getSimpleName());
    }

    public void indexingSucceeded(Instant at) {
        lastSuccessfulIndexAt.set(at);
    }

    public Instant lastReconciledAt() {
        return lastReconciledAt.get();
    }

    public Instant lastSuccessfulIndexAt() {
        return lastSuccessfulIndexAt.get();
    }

    public String lastFailureCode() {
        return lastFailureCode.get();
    }
}
