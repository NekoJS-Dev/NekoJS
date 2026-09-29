# P4 perf gate status (AC10)

> Ticket 34 evidence pack, 2026-09-29. This section only **consumes** the maintainer-confirmed
> policy (ticket 04) and ticket 35's re-test result. No number is set, moved, or reinterpreted here.

## 1. Policy input (ticket 04, maintainer confirmation 2026-09-28)

- Source record: `implementation-tickets/04-perf-release-policy.md` (Maintainer confirmation section),
  based on the P0 baseline `baseline/2026-09-12-perf-baseline/REPORT.md` (`raw/formal/`, revision
  `3400e97e`, primary node 26.1.2, hot-cache + reused-world protocol).
- Blocking dimensions (primary 26.1.2 only, same protocol, mean of ≥5 formal samples):
  - startup `wall_done_ms` mean ≤ **41173 ms** (baseline mean 21979 ms; max(3σ = 19194 ms, +25% = 27474 ms) → wider = 3σ)
  - reload `marker_ms` mean ≤ **285.3 ms** (baseline mean 228.2 ms; max(3σ = 26.8 ms, +25% = 57.05 ms) → wider = +25%)
- Observe-only dimensions (recorded, not blocking): tick / Adapter query / eval throughput / heap / Probe.
- Secondary (26.2.0) / experimental (1.21.1, both fabric) and CLIENT/GC/long-stability/multi-player/
  cold-cache ranges are **unsampled** — no thresholds; extending scope requires new sampling and a
  new maintainer ruling.
- Failure handling: any blocking dimension over threshold → this release candidate is blocked until
  fixed or explicitly re-ruled; `forced_kill=true`/timeout samples are invalid; adverse samples must
  not be dropped.

## 2. P4 re-test consumption — **PENDING TICKET 35**

Ticket 35 (`35-release-perf-compare.md`, status in-progress) is running the policy-conformant
re-test in a parallel worktree. Its landing directory is
`docs/architecture-refactor/baseline/2026-09-29-release-perf/`.

**Status at this pack's close: that directory does not exist yet** (checked at authoring time and
again at commit time). Therefore:

- The perf gate verdict for this release candidate is **pending-35** and is carried as blocking
  (FAILURES-LEDGER F-perf) until ticket 35's report lands and is consumed per policy.
- When it lands, this section's consumer instruction is: record the two blocking means (startup,
  reload) against the thresholds above with the sample counts, `env-snapshot` comparison, and the
  observe-only deltas vs the P0 baseline; any over-threshold value blocks the candidate as-is.
- This pack did not run its own perf sampling (would duplicate 35's harness and violate the
  single-consumption intent), and did not touch any threshold or number.

## 3. What is already green around the perf gate

- The perf harness location (`bench/perf/`) and the P0 baseline artifacts are intact in-tree.
- Ticket 04's policy record is complete and directly citable by release handoff (ticket 37).
- No performance-affecting production changes were made by this pack (evidence-only; see README).
