package dev.meherwerali.vaultsearch.sync;

public record ReconciliationResult(int discovered, int parsed, int skipped, int queuedUpserts, int queuedDeletes) {
}
