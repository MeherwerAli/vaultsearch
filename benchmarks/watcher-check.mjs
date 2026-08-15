import { mkdir, unlink, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { performance } from "node:perf_hooks";

const baseUrl = process.env.VAULTSEARCH_URL ?? "http://127.0.0.1:8080";
const apiKey = process.env.VAULTSEARCH_API_KEY;
const vaultRoot = process.env.VAULT_ROOT_HOST;
const expectedBaseCount = Number.parseInt(process.env.EXPECTED_BASE_COUNT ?? "0", 10);
const timeoutMs = Number.parseInt(process.env.WATCHER_TIMEOUT_MS ?? "75000", 10);
const output = resolve(process.argv[2] ?? "reports/raw/watcher-latest.json");
const probe = vaultRoot ? resolve(vaultRoot, "watcher-acceptance-probe.md") : null;

if (!apiKey || !vaultRoot || !probe || !Number.isInteger(expectedBaseCount) || expectedBaseCount < 1) {
  throw new Error("VAULTSEARCH_API_KEY, VAULT_ROOT_HOST, and positive EXPECTED_BASE_COUNT are required");
}
if (!probe.startsWith(`${resolve(vaultRoot)}/`)) {
  throw new Error("probe path escaped the configured vault root");
}

async function searchProbe() {
  const response = await fetch(`${baseUrl}/api/v1/search?q=watcher%20acceptance%20evidence&limit=20`, {
    headers: { Authorization: `Bearer ${apiKey}` },
  });
  if (!response.ok) throw new Error(`search returned ${response.status}`);
  return response.json();
}

async function indexStatus() {
  const response = await fetch(`${baseUrl}/api/v1/index/status`, {
    headers: { Authorization: `Bearer ${apiKey}` },
  });
  if (!response.ok) throw new Error(`status returned ${response.status}`);
  return response.json();
}

async function waitUntil(predicate, description) {
  const deadline = performance.now() + timeoutMs;
  while (performance.now() < deadline) {
    const value = await predicate();
    if (value) return value;
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 50));
  }
  throw new Error(`timed out waiting for ${description}`);
}

let created = false;
try {
  const createStarted = performance.now();
  await writeFile(probe, `---\ntags: [synthetic, watcher]\n---\n# Watcher Acceptance Probe\n\nwatcher acceptance evidence\nSee [[note-0]].\n`, "utf8");
  created = true;
  await waitUntil(async () => {
    const result = await searchProbe();
    return result.hits.some((hit) => hit.relativePath === "watcher-acceptance-probe.md");
  }, "watcher-created note to become searchable");
  const createVisibleMs = performance.now() - createStarted;

  const deleteStarted = performance.now();
  await unlink(probe);
  created = false;
  await waitUntil(async () => {
    const [result, status] = await Promise.all([searchProbe(), indexStatus()]);
    return !result.hits.some((hit) => hit.relativePath === "watcher-acceptance-probe.md")
      && status.indexedNotes === expectedBaseCount
      && status.pendingTasks === 0
      && status.deadTasks === 0;
  }, "watcher-deleted note to leave the projection");
  const deleteInvisibleMs = performance.now() - deleteStarted;

  const report = {
    generatedAt: new Date().toISOString(),
    snapshot: process.env.BENCHMARK_SNAPSHOT ?? "uncommitted-local-snapshot",
    expectedDebounceMs: 500,
    configuredPeriodicReconciliationMs: 60_000,
    timeoutMs,
    createVisibleMs: Number(createVisibleMs.toFixed(3)),
    deleteInvisibleMs: Number(deleteInvisibleMs.toFixed(3)),
    observation: createVisibleMs < 5_000 && deleteInvisibleMs < 5_000
      ? "timing-compatible-with-watch-events"
      : "timing-compatible-with-periodic-reconciliation-backstop",
    finalStatus: await indexStatus(),
  };
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  process.stdout.write(`${output}\n`);
} finally {
  if (created) await unlink(probe).catch(() => undefined);
}
