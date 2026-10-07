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

## 2. P4 re-test consumption — **CONSUMED TICKET 35**

Ticket 35 (`35-release-perf-compare.md`, status closed) completed the policy-conformant
re-test in its parallel worktree. Its landing directory is
`docs/architecture-refactor/baseline/2026-09-29-release-perf/`.

The pack-close snapshot below is retained for auditability: the directory was absent when this
pack was first authored and was consumed after ticket 35 landed and closed its ticket:

- **startup: PASS** — 5 formal samples, `wall_done_ms` mean **16512.2 ms ≤ 41173 ms** (margin
  24660.8 ms; per-sample 12889/17446/17435/17097/17694; baseline mean 21979.2 → −24.9%).
- **reload: PASS** — 5 formal samples, `marker_ms` mean **278.0 ms ≤ 285.3 ms** (margin 7.3 ms —
  thin; per-sample 353/266/266/268/237, first-reload dominates; steady-4 mean 234.3 ms).
- All 21 sessions valid (no forced_kill/timeout, RCON stops); nothing culled (probe's 1393 ms
  outlier retained). Observe-only deltas vs P0 baseline recorded in 35's REPORT §4.3–4.7
  (tick identical; adapter within the baseline's own 1.4× cross-session band; eval tighter;
  heap same band; probe 390 vs 389 files). Environment drift recorded in 35's REPORT §2
  (isolated GRADLE_USER_HOME recreated cold; Docker stopped — quieter than baseline's noise
  profile; favorable, no protocol change).
- **Historical verdict: GREEN for the ticket35 candidate at `28283cc4`.** The original
  F-perf entry was resolved for that revision. This is **not** a PASS for later runtime code:
  current candidate remeasurement is required after subsequent lifecycle/runtime changes.
  Ticket37 keeps its final performance gate open; no threshold or failed-sample policy changes.
  The reload margin was only7.3ms, and35 REPORT §8 records the remeasurement triggers.

## 3. What is already green around the perf gate

- The perf harness location (`bench/perf/`) and the P0 baseline artifacts are intact in-tree.
- Ticket 04's policy record is complete and directly citable by release handoff (ticket 37).
- No performance-affecting production changes were made by this pack (evidence-only; see README).
