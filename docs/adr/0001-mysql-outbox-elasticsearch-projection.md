# ADR 0001: MySQL outbox for the Elasticsearch projection

- Status: Accepted
- Date: 2026-08-15

## Context

Filesystem observation, relational metadata, and Elasticsearch indexing cannot share one atomic transaction. Writing MySQL and Elasticsearch inline would lose or hide work when the second operation fails.

## Decision

Commit note metadata and one coalesced `index_tasks` row in the same MySQL transaction. A separate worker applies the serialized UPSERT or DELETE projection idempotently and records bounded retry state.

## Consequences

- A crash after the MySQL commit is recoverable.
- Search is explicitly eventually consistent.
- The outbox temporarily duplicates searchable content in MySQL until a successful projection clears the payload.
- Multiple workers need row-claiming semantics before horizontal indexing is supported.
