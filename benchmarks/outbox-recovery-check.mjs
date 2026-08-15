import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { performance } from "node:perf_hooks";

const projectRoot = resolve(import.meta.dirname, "..");
const composeProject = process.env.COMPOSE_PROJECT_NAME;
const baseUrl = process.env.VAULTSEARCH_URL ?? "http://127.0.0.1:8080";
const apiKey = process.env.VAULTSEARCH_API_KEY;
const vaultRoot = process.env.VAULT_ROOT_HOST;
const databasePassword = process.env.DATABASE_PASSWORD ?? "vaultsearch-local";
const expectedNoteCount = Number.parseInt(process.env.EXPECTED_NOTE_COUNT ?? "0", 10);
const output = resolve(process.argv[2] ?? "reports/raw/outbox-recovery-latest.json");
const note = vaultRoot ? resolve(vaultRoot, "note-0.md") : null;

if (!composeProject || !apiKey || !vaultRoot || !note || expectedNoteCount < 1) {
  throw new Error("COMPOSE_PROJECT_NAME, VAULTSEARCH_API_KEY, VAULT_ROOT_HOST, and EXPECTED_NOTE_COUNT are required");
}
if (!note.startsWith(`${resolve(vaultRoot)}/`)) throw new Error("test note escaped the vault root");

function compose(...argumentsList) {
  return execFileSync("docker", ["compose", "-p", composeProject, ...argumentsList], {
    cwd: projectRoot,
    encoding: "utf8",
    env: process.env,
  });
}

async function requestJson(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${apiKey}`, ...(options.headers ?? {}) },
  });
  if (!response.ok) throw new Error(`${path} returned ${response.status}`);
  return response.json();
}

async function waitUntil(probe, description, timeoutMs = 90_000) {
  const deadline = performance.now() + timeoutMs;
  while (performance.now() < deadline) {
    const value = await probe().catch(() => null);
    if (value) return value;
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 250));
  }
  throw new Error(`timed out waiting for ${description}`);
}

async function reconcile() {
  return requestJson("/api/v1/index/reconcile", { method: "POST" });
}

function databaseEvidence() {
  const sql = "SELECT t.operation,t.status,t.attempts,t.last_error_code,CHAR_LENGTH(t.payload_json),n.index_state,n.deleted FROM index_tasks t JOIN notes n ON n.id=t.note_id ORDER BY t.updated_at DESC LIMIT 1";
  const outputText = compose("exec", "-T", "-e", `MYSQL_PWD=${databasePassword}`, "mysql",
    "mysql", "-N", "-B", "-uvaultsearch", "vaultsearch", "-e", sql).trim();
  const [operation, status, attempts, lastErrorCode, payloadBytes, noteIndexState, deleted] = outputText.split("\t");
  return {
    operation,
    status,
    attempts: Number.parseInt(attempts, 10),
    lastErrorCode: lastErrorCode === "NULL" ? null : lastErrorCode,
    payloadBytes: Number.parseInt(payloadBytes, 10),
    noteIndexState,
    deleted: deleted === "1",
  };
}

const original = await readFile(note, "utf8");
let elasticsearchStopped = false;
let failureStatus;
let persistedTask;
let recoveredStatus;
let recoveredSearch;
let run;
const started = performance.now();

try {
  compose("stop", "elasticsearch");
  elasticsearchStopped = true;
  await writeFile(note, `${original}\n\noutage recovery evidence\n`, "utf8");
  run = await reconcile();
  failureStatus = await waitUntil(async () => {
    const status = await requestJson("/api/v1/index/status");
    return status.state === "degraded" && status.pendingTasks > 0 ? status : null;
  }, "a persisted pending task while Elasticsearch is stopped");
  persistedTask = await waitUntil(async () => {
    const task = databaseEvidence();
    return task.status === "PENDING" && task.attempts > 0 ? task : null;
  }, "the failed Elasticsearch attempt to enter bounded retry state");

  compose("up", "-d", "--wait", "elasticsearch");
  elasticsearchStopped = false;
  recoveredStatus = await waitUntil(async () => {
    const status = await requestJson("/api/v1/index/status");
    return status.state === "ready" && status.pendingTasks === 0 && status.deadTasks === 0 ? status : null;
  }, "the persisted task to recover after Elasticsearch restart");
  recoveredSearch = await waitUntil(async () => {
    const result = await requestJson("/api/v1/search?q=outage%20recovery%20evidence&limit=20");
    return result.hits.some((hit) => hit.relativePath === "note-0.md") ? result : null;
  }, "the recovered projection to become searchable");
} finally {
  if (elasticsearchStopped) compose("up", "-d", "--wait", "elasticsearch");
  await writeFile(note, original, "utf8");
  await reconcile().catch(() => null);
  await waitUntil(async () => {
    const [status, result] = await Promise.all([
      requestJson("/api/v1/index/status"),
      requestJson("/api/v1/search?q=outage%20recovery%20evidence&limit=20"),
    ]);
    return status.state === "ready"
      && status.indexedNotes === expectedNoteCount
      && status.pendingTasks === 0
      && !result.hits.some((hit) => hit.relativePath === "note-0.md");
  }, "the restored fixture to be reconciled");
}

const report = {
  generatedAt: new Date().toISOString(),
  snapshot: process.env.BENCHMARK_SNAPSHOT ?? "uncommitted-local-snapshot",
  runId: run.runId,
  elapsedMs: Number((performance.now() - started).toFixed(3)),
  failureStatus,
  persistedTask,
  recoveredStatus,
  recoveredHit: recoveredSearch.hits.find((hit) => hit.relativePath === "note-0.md"),
  finalStatus: await requestJson("/api/v1/index/status"),
};
await mkdir(dirname(output), { recursive: true });
await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
process.stdout.write(`${output}\n`);
