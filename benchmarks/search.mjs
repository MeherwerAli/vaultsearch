import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { performance } from "node:perf_hooks";
import { cpus, platform, release, totalmem } from "node:os";

const baseUrl = process.env.VAULTSEARCH_URL ?? "http://127.0.0.1:8080";
const apiKey = process.env.VAULTSEARCH_API_KEY;
const output = resolve(process.argv[2] ?? "reports/raw/search-latest.json");
const concurrencies = [1, 16, 64];
const requestsPerScenario = Number.parseInt(process.env.REQUESTS_PER_SCENARIO ?? "200", 10);
const warmupRequests = Number.parseInt(process.env.WARMUP_REQUESTS ?? "100", 10);
const expectedNoteCount = Number.parseInt(process.env.EXPECTED_NOTE_COUNT ?? "0", 10);

if (!apiKey) {
  throw new Error("VAULTSEARCH_API_KEY is required");
}
if (!Number.isInteger(requestsPerScenario) || requestsPerScenario < 1) {
  throw new Error("REQUESTS_PER_SCENARIO must be a positive integer");
}
if (!Number.isInteger(warmupRequests) || warmupRequests < 0) {
  throw new Error("WARMUP_REQUESTS must be a non-negative integer");
}
if (!Number.isInteger(expectedNoteCount) || expectedNoteCount < 1) {
  throw new Error("EXPECTED_NOTE_COUNT must be a positive integer");
}

function percentile(sorted, quantile) {
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * quantile) - 1)];
}

async function runScenario(concurrency) {
  const latencies = [];
  let failures = 0;
  let issued = 0;
  const started = performance.now();

  async function worker() {
    while (issued < requestsPerScenario) {
      const requestNumber = issued;
      issued += 1;
      const requestStarted = performance.now();
      try {
        const query = encodeURIComponent(`service ${requestNumber % 20}`);
        const response = await fetch(`${baseUrl}/api/v1/search?q=${query}&limit=20`, {
          headers: { Authorization: `Bearer ${apiKey}` },
        });
        if (!response.ok) {
          failures += 1;
        }
        await response.arrayBuffer();
      } catch {
        failures += 1;
      } finally {
        latencies.push(performance.now() - requestStarted);
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  const elapsedMs = performance.now() - started;
  latencies.sort((left, right) => left - right);
  return {
    concurrency,
    requests: requestsPerScenario,
    p50LatencyMs: Number(percentile(latencies, 0.5).toFixed(3)),
    p95LatencyMs: Number(percentile(latencies, 0.95).toFixed(3)),
    throughputRequestsPerSecond: Number(((requestsPerScenario * 1000) / elapsedMs).toFixed(3)),
    failureRate: Number((failures / requestsPerScenario).toFixed(5)),
  };
}

async function warmUp() {
  for (let index = 0; index < warmupRequests; index += 1) {
    const response = await fetch(`${baseUrl}/api/v1/search?q=service%20${index % 20}&limit=20`, {
      headers: { Authorization: `Bearer ${apiKey}` },
    });
    if (!response.ok) {
      throw new Error(`warm-up request returned ${response.status}`);
    }
    await response.arrayBuffer();
  }
}

const statusResponse = await fetch(`${baseUrl}/api/v1/index/status`, {
  headers: { Authorization: `Bearer ${apiKey}` },
});
if (!statusResponse.ok) {
  throw new Error(`status endpoint returned ${statusResponse.status}`);
}
const indexStatus = await statusResponse.json();
if (indexStatus.state !== "ready"
    || indexStatus.indexedNotes !== expectedNoteCount
    || indexStatus.pendingTasks !== 0
    || indexStatus.deadTasks !== 0) {
  throw new Error(`index did not reach the expected clean state: ${JSON.stringify(indexStatus)}`);
}

const report = {
  generatedAt: new Date().toISOString(),
  snapshot: process.env.BENCHMARK_SNAPSHOT ?? "uncommitted-local-snapshot",
  environment: {
    runtime: process.version,
    platform: `${platform()}-${process.arch}`,
    osRelease: release(),
    cpu: cpus()[0]?.model ?? "unknown",
    hostMemoryBytes: totalmem(),
    docker: process.env.DOCKER_VERSION ?? "not-recorded",
    dockerAssignedCpus: Number.parseInt(process.env.DOCKER_ASSIGNED_CPUS ?? "0", 10) || null,
    dockerAssignedMemoryBytes: Number.parseInt(process.env.DOCKER_ASSIGNED_MEMORY_BYTES ?? "0", 10) || null,
    serverJava: process.env.SERVER_JAVA_VERSION ?? "not-recorded",
    mysqlImage: "mysql:8.4",
    elasticsearchImage: "docker.elastic.co/elasticsearch/elasticsearch:8.18.1",
  },
  target: baseUrl,
  corpus: {
    generator: "benchmarks/generate-corpus.mjs",
    expectedNotes: expectedNoteCount,
    indexedNotes: indexStatus.indexedNotes,
  },
  indexStatus,
  indexing: {
    measurement: "application-container-start-to-ready",
    startedAt: process.env.INDEX_STARTED_AT ?? "not-recorded",
    readyAt: process.env.INDEX_READY_AT ?? "not-recorded",
    durationMs: Number.parseInt(process.env.INDEX_DURATION_MS ?? "0", 10) || null,
  },
  warmup: { requests: warmupRequests, policy: "sequential queries before timed scenarios" },
  scenarios: [],
};

await warmUp();
for (const concurrency of concurrencies) {
  report.scenarios.push(await runScenario(concurrency));
}

await mkdir(dirname(output), { recursive: true });
await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
process.stdout.write(`${output}\n`);
