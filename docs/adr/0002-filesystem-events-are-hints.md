# ADR 0002: Filesystem events are latency hints

- Status: Accepted
- Date: 2026-08-15

## Context

Filesystem watchers can coalesce, reorder, overflow, or omit events across operating systems and mounted volumes.

## Decision

Use watcher events only to debounce and queue a full reconciliation. Run the same reconciliation at startup and every 60 seconds.

## Consequences

- One deterministic path handles startup, recovery, and live changes.
- Large vaults pay an O(n) scan cost; projection hashes avoid unnecessary indexing work.
- Future versions may add incremental catalogs after benchmarks establish the need.
