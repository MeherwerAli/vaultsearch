# Measurement procedure

No performance result is claimed until a dated raw report is produced from a disclosed environment. The harness uses only synthetic notes and native Node.js `fetch`.

Generate the required corpora:

```bash
node benchmarks/generate-corpus.mjs 1000
node benchmarks/generate-corpus.mjs 10000
```

Set `VAULT_ROOT_HOST` to one generated directory, start the stack, wait for `/api/v1/index/status` to report `ready`, then run:

```bash
VAULTSEARCH_API_KEY=replace-with-a-long-random-value \
  EXPECTED_NOTE_COUNT=1000 \
  INDEX_STARTED_AT=... INDEX_READY_AT=... INDEX_DURATION_MS=... \
  node benchmarks/search.mjs reports/raw/search-1000.json
```

Repeat with the 10,000-note corpus. Retain the raw JSON together with:

- code commit or explicit uncommitted snapshot identifier;
- CPU, memory, operating system, Docker, JVM, MySQL, and Elasticsearch versions;
- corpus size and generation command;
- p50/p95 latency, throughput, failure rate, and index-status payload;
- any warm-up or resource-limit changes.

`reports/raw/` is ignored during development so accidental local measurements are not presented as published evidence. Curated local evidence belongs in `reports/results/`; release reports must still be deliberately reviewed before publication.

The current uncommitted local observations are summarized in [`reports/results/README.md`](results/README.md).
