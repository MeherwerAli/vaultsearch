# Threats and limitations

## Trust boundaries

1. An API caller crosses a bearer-authenticated HTTP boundary into search and reconciliation endpoints.
2. The process reads an operator-mounted vault that may contain malformed or adversarial Markdown.
3. The process sends projections to MySQL and Elasticsearch using operator-supplied credentials and network policy.

## Material abuse paths and controls

| Abuse path | Impact | v0.1 control | Remaining limitation |
| --- | --- | --- | --- |
| Read another host path through traversal or a symlink | Note disclosure | Canonical root check, no link following, vault-relative API paths, read-only mount | The operator still controls the mounted volume and container permissions |
| Exhaust memory with large or malformed notes | Availability loss | `.md` allowlist, 2 MiB default cap, safe YAML constructor, parse failure isolation | A large number of valid small files can still create load |
| Recover an API key from source or logs | Vault disclosure | Environment/Secret injection, constant-time comparison, no key logging | Static keys have no scopes or rotation protocol |
| Poison search with misleading note content | Integrity loss | Search treats note text as data and exposes source paths; it executes no note instructions | Search relevance is not a trust or factuality signal |
| Hide stale Elasticsearch state after partial failure | Incorrect results | Durable outbox, explicit degraded state, timestamps, retries and dead tasks | Reads can be stale while the projection remains reachable |
| Enumerate operational capacity from metrics | Reconnaissance | Metrics require the same bearer key | A shared key grants all read and reconcile capabilities |

## Deployment limitations

- The API is intended for a private network or authenticated reverse proxy, not direct public Internet exposure.
- TLS termination, API-key rotation, tenant authorization, backups, MySQL/Elasticsearch hardening, and audit-log retention belong to the deployment environment.
- The example NetworkPolicy permits database and Elasticsearch egress by port so externally hosted dependencies remain reachable; a deployment must narrow those destinations to its actual CIDRs.
- The service does not write the vault, execute Markdown, render HTML, run plugins, or protect sensitive content accidentally placed in the mounted vault.
- `WatchService` delivery depends on the filesystem implementation. VM-backed Docker bind mounts may not forward host events into the container even though files become visible. Scheduled full reconciliation remains the correctness backstop; deployments needing sub-minute freshness must verify event delivery on their storage driver or use an external change feed.
- Flyway is pinned to 12.8.1 instead of Spring Boot 3.5.16's older managed version because the managed engine warns that MySQL 8.4 is newer than its tested range. The MySQL 8.4 Testcontainers migration test is the compatibility gate for this override.
