# Ticket 48 end-to-end proof status

Prepared 2026-10-05/2026-10-06 from the current main worktree. This record consolidates existing public contract, fake-host, native NeoForge and resource evidence; it does not claim a single combined client run where one has not been captured.

## Input and conversion

- Representative input: `docs/ui-conversion/fixtures/login-form.html`.
- Conversion output: `docs/ui-conversion/fixtures/login-form.output.tsx`.
- Conversion report: `docs/ui-conversion/fixtures/login-form.conversion-report.json`.
- Authoring contract/cookbook: `docs/ui-conversion/web-to-jsx-cookbook.md` and tickets 46/47.
- Fake-host verification: `TypeScriptUiAuthoringDocsTest` verifies controlled inputs, submit/cancel state, error visibility and profile 6 title sizing.

## Evidence matrix

| Requirement | Current evidence | Result |
|---|---|---|
| Converted structure/state/resources/report | Login-form source/output/report and fake-host proof | PASS |
| Real NeoForge 26.2 open/input/resize/reload/close | Ticket 41 live screen, interaction, lifecycle and round-2 evidence; ticket 44 visual/font/resource smoke; ticket 43 profile smoke | PARTIAL: evidence is split across canonical fixtures, not one combined login-form run |
| Inspector-driven correction | Ticket 45 inspector contract and round-2 visual evidence record local correction and bounded pixel differences | PASS for recorded corrections |
| Reload/error/cleanup retention | Ticket 41 lifecycle evidence plus ticket 44 resource failure/recovery evidence | PASS for covered paths |
| Performance statistics | Existing node tests/build evidence; no single ticket-48 performance sample set using the converted screen | NOT RUN |
| NeoForge capability | NeoForge 26.2 native/live evidence exists for the component paths | PARTIAL: combined proof still pending |
| Other nodes | Builds and focused tests exist; no claim of real client parity | NOT VERIFIED |

## Remaining gate

Ticket 48 remains `ready-for-agent` until one registered NeoForge 26.2 client session runs the converted representative screen through open, input, profile resize, resource failure/recovery, reload and cleanup while recording the required performance counters. Existing evidence is linked above rather than promoted to a false combined proof.

## Latest attempt

A 2026-10-06 registered MCP client attempt is recorded in [mcp-attempt-2026-10-06.md](mcp-attempt-2026-10-06.md). The target 26.2 NeoForge MCP jar was selected after isolating duplicate mod-id jars, but NekoJS failed during mod construction with `ExceptionInInitializerError`; the MCP endpoint never connected. No ticket 48 acceptance criterion is promoted by this attempt.

A follow-up using the documented Zulu Java 25 CLI path is recorded in [mcp-cli-follow-up-2026-10-06.md](mcp-cli-follow-up-2026-10-06.md). It reached CLIENT script reload but used stale `mcpmod@0.3.0` runtime code and failed its HTTP bridge with `ControlModeHelper` missing; its imported-output fixture also reported a VNode identity error. It provides no acceptance evidence.

The retained NeoForge host now exposes a snapshot-only `performanceCounters()` surface for the proof fixture. It records successful `initialBuild`, `layout`, `reconcile`, `resize`, `paint`, `diagnostics`, and `cleanup` operations; failed transactions and teardown close transactions are excluded from build/reconcile counts, native resource relayouts are included, and guest/host diagnostic paths share the diagnostics count. `Ticket48PerformanceCountersTest` covers signal-driven incremental reconcile, rollback/retry, host resize, paint, host failure diagnostics, and generation cleanup. The common JSX runtime now invokes root render functions through the same `callGuest` bridge used for component renders, so imported/execute-backed TSX render functions are normalized before VNode expansion. `:common:check` passes after this fix; live confirmation remains pending a current MCP 0.4.2 client session.
