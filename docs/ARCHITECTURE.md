# Architecture

## Components and ownership

- `VaultSource` owns bounded discovery beneath the configured root. The filesystem implementation ignores hidden directories, oversized files, and symbolic links.
- `NoteParser` converts one source note into normalized metadata and outgoing link references. It does not resolve cross-note identity.
- `LinkResolver` builds a vault-wide catalog and resolves exact paths before unique stems or aliases. Missing and ambiguous links stay observable.
- `VaultReconciler` is the MySQL transaction boundary. It updates note metadata and one coalesced outbox task per note.
- `IndexTaskWorker` projects UPSERT and DELETE tasks into Elasticsearch. It stores only error class and message hash on failure.
- `VaultSearchService` reads the Elasticsearch projection and reports projection freshness from the outbox state.

## Data model

MySQL is the synchronization authority, not the full-text read store. `vaults` retains a stable vault ID. `notes` retains vault-relative paths, source/projection hashes, timestamps, and projection state. `index_tasks` retains the idempotent Elasticsearch operation and serialized projection needed for crash recovery.

Elasticsearch stores searchable bodies and link projections. A document ID is `SHA-256(vault_key + NUL + normalized_relative_path)`. A rename is therefore a delete plus a new document in v0.1.

## Failure semantics

- A malformed note is skipped without deleting its last successfully indexed projection.
- An Elasticsearch failure does not roll back the MySQL source observation. The task retries with exponential backoff capped at five minutes.
- Eight failed attempts produce a visible dead task and degraded status. The next manual or scheduled reconciliation can requeue it.
- Duplicate or reordered watcher events coalesce into a full reconciliation.
- A process crash after the MySQL commit leaves a pending task that the next worker run can replay.

## Scaling boundary

The supplied Kubernetes deployment has one replica. Multiple indexer replicas need database row claiming plus leader election or a split read/index architecture. That work is intentionally outside v0.1; increasing `replicas` without it is unsupported.
