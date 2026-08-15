# Two-minute demo

1. Start the Compose stack with a non-default API key.
2. Show `/api/v1/index/status` moving from `initializing` or `degraded` to `ready`.
3. Search for `circuit breaker` and point out the vault-relative path, excerpt, tags, and deterministic note ID.
4. Request backlinks for the result and trace the source note back to its Markdown wikilink.
5. Add a temporary public-safe note to a disposable benchmark vault, show watcher-triggered reconciliation, then delete it and show the projection deletion.
6. Stop Elasticsearch, trigger reconciliation, and show the pending/dead-task behavior without exposing the raw dependency error.

Do not modify the public demo vault during a recorded run. Use a generated disposable corpus for write/change demonstrations.
