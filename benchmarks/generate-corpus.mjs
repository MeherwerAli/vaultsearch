import { mkdir, rm, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const count = Number.parseInt(process.argv[2] ?? "1000", 10);
const output = resolve(process.argv[3] ?? `.benchmark-work/corpus-${count}`);

if (!Number.isInteger(count) || count < 1 || count > 100_000) {
  throw new Error("note count must be an integer between 1 and 100000");
}

await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });

for (let index = 0; index < count; index += 1) {
  const next = (index + 1) % count;
  const body = `---
tags: [synthetic, service-${index % 20}]
aliases: [Synthetic Note ${index}]
owner: benchmark
---
# Reliability Runbook ${index}

Synthetic incident response guidance for service ${index % 20}.
See [[note-${next}|the next runbook]] for the recovery sequence.

## Checks

- inspect dependency latency
- validate queue depth
- confirm circuit breaker state
`;
  await writeFile(resolve(output, `note-${index}.md`), body, "utf8");
}

process.stdout.write(`${output}\n`);
