# Contributing

Small, evidence-backed changes are welcome. Start with a fixture and a failing test, keep connector-specific behavior behind `VaultSource` or `NoteParser`, and avoid adding semantic search or a UI to an unrelated change.

```bash
mvn --batch-mode --no-transfer-progress verify
docker compose config --quiet
kubectl kustomize k8s/base >/dev/null
```

Good first contributions include Markdown-link edge cases, alternate frontmatter fixtures, a Logseq filesystem adapter, and benchmark-corpus improvements. Notion and Roam connectors should convert exports into `SourceNote` inputs without embedding provider credentials in the core service.

Never add private notes, employer/customer material, credentials, absolute workstation paths, or benchmark conclusions without raw results.
