# Ticket 48 complete NeoForge proof

Result: **complete technical evidence for NeoForge 26.2**. The final registered client session used the converted login form through resource failure/recovery, six explicit root profiles, native input/submit, deliberate event/render errors, healthy recovery, keyed-list changes, candidate rejection, physical resize and Escape cleanup. Other nodes are not promoted to real-client UI support.

## Reproduce and inspect

- Input/output/report: [HTML](../../../ui-conversion/fixtures/login-form.html), [TSX](../../../ui-conversion/fixtures/login-form.output.tsx), [conversion report](../../../ui-conversion/fixtures/login-form.conversion-report.json).
- Native fixture and controls: [combined-session.tsx](combined-session.tsx), [native-input.ps1](native-input.ps1).
- Raw final log: [combined-final-session.log](combined-final-session.log); 764 `[ticket48-auto]` records.
- Assert the raw data: `node docs/architecture-refactor/evidence/2026-10-06-ticket48-e2e/verify-combined-session.mjs docs/architecture-refactor/evidence/2026-10-06-ticket48-e2e/combined-final-session.log` → PASS.
- Earlier red/green retention runs: [before fix](combined-before-fix.log), [after fix](combined-after-fix.log), [retained screen](after-failed-reload.png). The verifier's default earlier log also passes (201 records).
- Final corrected visual proof: [1258×703 screenshot](final-complete.png). Credentials shown are synthetic test data.

## Environment

Minecraft 26.2, NeoForge 26.2.0.75, Zulu Java 25, MCP 0.4.2; owned profile `build/ticket48-autonomous`, final PID 4272, discovered port 9874. Exact production jar SHA256: `32309A66568C6E2EB57CF01CAA5A0D5B4201E01A6555F229EC10EAE107E0CDB8`. The final UI run was over the title-screen background, not in a world. Native window dimensions are screenshot readbacks, not an assumption from CLI width/height arguments.

## Observed paths

| Path | Real observation |
|---|---|
| Missing font | `NEKO-6004`, root/node/resource/generation attribution; fallback `AAAA` width 24 |
| Recover and revise provider | Same JVM, native F3+T: [A=4](font-a.json) → width 16; [A=9](font-b.json) → width 36 |
| Six profiles | Successful explicit root resize records for 100×100, 320×180, 480×240, 640×360, 854×480, 1280×720, followed by restoring the actual viewport |
| Input and submit | Native controlled input `NekoTester`, password length 7, `lastSubmitted=NekoTester`, submitting=true |
| Candidate failure | `NEKO-1008`, phase EXECUTION, domain script-execution; same active state, initialBuild and reconcile retained while paint continued |
| Render/event failures | Both deliberate diagnostic causes recorded; last committed tree retained; healthy Recover handler restored rendering |
| Keyed list | Committed node text `A/B` → `AX/B` → `B`; reconcile counters 3 → 6 → 8, initialBuild remains 1 |
| Physical window resize | Native resize counter reached 3; actual screenshot 1258×703, profile 3; typed state and list retained |
| Close | Escape record: disposed=true, cleanup=1, retained typed state |

The font space provider proves native resource selection/advance readback, not drawn custom glyph appearance. Successful resource reload recreated CLIENT generations/roots; it is **not** same-root/no-rerender resource recovery. Explicit profile resizes are distinct from the recorded OS window resize. The 100×100 probe is not a claim that the fixed-width form is fully visible in that tiny viewport.

## Statistics and Inspector corrections

Counters are actual host operation counts, not timings or a release benchmark. At final close: initialBuild=1, layout=19, reconcile=8, resize=3, paint=61884, diagnostics=21, cleanup=1. The 21 local diagnostics include repeated deliberate render failures while input signals changed under renderFault; resource and global reload diagnostics are separate. Idle samples show paint increasing without additional reconcile. No new release-blocking numeric threshold is introduced.

Native layout snapshots first exposed narrow intrinsic input groups, action overlap and clipping. Focused regressions went red before explicit fill/group/row/control geometry was corrected. Screenshots then exposed 12-unit glyph clipping and light-card text/color mismatch; 20-unit inputs, explicit dark text and six-digit opaque RGB colors corrected them. The canonical fake-host proof and final native screenshot verify those changes. The conversion report retains typography, browser semantics, native button palette, fixed layout and missing per-profile reference limitations; visual equivalence is downgraded, not pixel parity. Proof-only font/error/list controls are not misrepresented as source HTML content.

The audible narration result is [separate and explicitly maintainer-confirmed](native-narration.md). CLIENT generation/closed-handle/old-listener behavior also uses existing common/native lifecycle regressions; raw logs are not claimed to exercise every inaccessible stale handle directly.

## Historical attempts

Earlier failures remain [MCP attempt](mcp-attempt-2026-10-06.md), [stale-MCP follow-up](mcp-cli-follow-up-2026-10-06.md) and [initial world smoke](mcp-smoke-2026-10-06.md). They are historical records, not substitutes for the complete final session.

## Capability boundary

NeoForge 26.2 native Screen paths above are verified. NeoForge 26.1.2 and both Fabric nodes retain `not verified` for real-client JSX UI, even where build/fake/native unit checks pass. Minecraft 1.21.1 item smoke is separate from UI capability. This proof does not change tickets 34–37 Blocked by or authorize publication.
