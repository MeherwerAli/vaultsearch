# Local measurement results

These files record reproducible observations from the uncommitted `codex/v0.1.0-implementation` snapshot on 2026-08-15. They are local engineering evidence, not an SLA, a production capacity claim, or release evidence. Repeat the measurements against the eventual tagged commit before quoting them in the portfolio.

## Indexing and search

Apple M4 Pro host; Docker/Colima limited to 2 CPUs and about 8.3 GB RAM; application on Java 17.0.19; MySQL 8.4 and Elasticsearch 8.18.1. Each search scenario used 100 warm-up requests and 200 measured requests.

| Corpus | Container start to ready | Concurrency | p50 | p95 | Throughput | Failure rate |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 notes | 37.373 s | 1 | 7.485 ms | 15.227 ms | 116.492 req/s | 0% |
| 1,000 notes | 37.373 s | 16 | 39.451 ms | 61.003 ms | 386.385 req/s | 0% |
| 1,000 notes | 37.373 s | 64 | 144.976 ms | 287.412 ms | 372.697 req/s | 0% |
| 10,000 notes | 174.501 s | 1 | 6.832 ms | 14.882 ms | 129.901 req/s | 0% |
| 10,000 notes | 174.501 s | 16 | 38.483 ms | 59.962 ms | 402.922 req/s | 0% |
| 10,000 notes | 174.501 s | 64 | 103.412 ms | 208.425 ms | 500.711 req/s | 0% |

“Container start to ready” includes application boot, migration, complete reconciliation, and outbox drain. It is not a parser-only measurement.

## Recovery and watcher observations

- Elasticsearch outage: the API reported `degraded`; MySQL retained one failed-attempt UPSERT as `PENDING`; restarting Elasticsearch drained it and restoration ended at 1,000 indexed notes with zero pending/dead tasks.
- Docker bind-mounted watcher probe: creation became searchable in 1,475.026 ms; deletion left the projection in 26,232.278 ms. This is classified as compatible with the periodic reconciliation backstop, not proof of sub-second native watch delivery on Docker Desktop/Colima storage.
- The Linux-native watcher unit test separately observed both create and delete events and emitted coalescing reconciliation hints.

## Raw evidence

- [`vaultsearch-1000-local-2026-08-15.json`](./vaultsearch-1000-local-2026-08-15.json)
- [`vaultsearch-10000-local-2026-08-15.json`](./vaultsearch-10000-local-2026-08-15.json)
- [`vaultsearch-watcher-local-2026-08-15.json`](./vaultsearch-watcher-local-2026-08-15.json)
- [`vaultsearch-outbox-recovery-local-2026-08-15.json`](./vaultsearch-outbox-recovery-local-2026-08-15.json)
