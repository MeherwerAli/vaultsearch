# VaultSearch

VaultSearch is a headless, read-only search projection for Obsidian-style Markdown vaults. It runs independently of the Obsidian desktop application, preserves link metadata, and exposes authenticated full-text search and backlink APIs.

It is deliberately not a replacement for [Obsidian Local REST API](https://github.com/coddingtonbear/obsidian-local-rest-api). That plugin exposes the live Obsidian application. VaultSearch is for a server-side projection that can reconcile a read-only vault mount, recover from missed filesystem events, and serve search without a desktop process.

## What it demonstrates

- Java 17 and Spring Boot 3.5 service design
- MySQL-to-Elasticsearch eventual consistency through a transactional outbox
- Obsidian frontmatter, tag, alias, wikilink, heading, block, and Markdown-link parsing
- Deterministic note identity and backlink resolution
- Bounded retry, dead-letter visibility, readiness, metrics, and explicit degraded state
- Read-only filesystem, bearer authentication, container, and Kubernetes boundaries

```mermaid
flowchart LR
    V["Read-only Markdown vault"] --> S["Full scan + WatchService hints"]
    S --> P["Parser and link resolver"]
    P --> M[("MySQL metadata + index outbox")]
    M --> W["Idempotent index worker"]
    W --> E[("Elasticsearch projection")]
    E --> A["Authenticated search and backlinks API"]
```

## Run locally

Requirements: Docker with at least 4 GB available to the Compose stack.

```bash
cp .env.example .env
docker compose up --build
```

Compose binds the API to `127.0.0.1:8080` and mounts the sibling public-safe `engineering-knowledge-vault` read-only. Replace the development credentials before using another environment.

```bash
curl -H 'Authorization: Bearer replace-with-a-long-random-value' \
  'http://127.0.0.1:8080/api/v1/search?q=circuit%20breaker&limit=10'

curl -H 'Authorization: Bearer replace-with-a-long-random-value' \
  'http://127.0.0.1:8080/api/v1/index/status'
```

Trigger an idempotent reconciliation:

```bash
curl -X POST -H 'Authorization: Bearer replace-with-a-long-random-value' \
  'http://127.0.0.1:8080/api/v1/index/reconcile'
```

## API behavior

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/search?q=&limit=` | Multi-field lexical search with vault-relative paths and excerpts |
| `GET /api/v1/notes/{noteId}/backlinks` | Notes whose resolved outgoing links target the note |
| `GET /api/v1/index/status` | Initializing, ready, or degraded projection status |
| `POST /api/v1/index/reconcile` | Queues one coalesced full reconciliation |
| `GET /actuator/health/liveness` | Process liveness without content or dependency detail |
| `GET /actuator/health/readiness` | Dependency-aware readiness |

Application endpoints and Prometheus metrics require `Authorization: Bearer <VAULTSEARCH_API_KEY>`. Health probes do not.

## Correctness model

`WatchService` reduces update latency but is never the correctness authority. Startup and periodic scans compare source and projection hashes. MySQL commits metadata and an outbox task together; Elasticsearch operations are idempotent by deterministic note ID. Failed tasks retry eight times with bounded backoff, then remain visible as dead tasks until a reconciliation reopens them.

## Verification

```bash
mvn --batch-mode --no-transfer-progress verify
docker compose config --quiet
kubectl kustomize k8s/base >/dev/null
kubectl kustomize k8s/base | docker run --rm -i \
  ghcr.io/yannh/kubeconform:v0.7.0 -strict -summary
```

See [architecture](docs/ARCHITECTURE.md), [threats and limitations](docs/THREAT-LIMITATIONS.md), [demo](docs/DEMO.md), [benchmark procedure](reports/README.md), and [local measurement results](reports/results/README.md).

## Scope

v0.1 supports one active filesystem vault and one indexer replica. It does not write notes, run OCR, embed content, provide semantic search, or guarantee Obsidian's full shortest-path resolution behavior. Multi-vault registration and connector implementations remain extension points rather than implied capabilities.

## License

MIT
