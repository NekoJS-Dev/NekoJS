# Ticket 43: profile measurements and host layout parity audit

Scope: independent evidence preparation on `mult`, following `47dad5ae`. Only this new evidence directory and the new [live fixture](../../../../src/test/resources/nekojs/client/ui/ticket43-profiles-flow.tsx) are authored. Existing runtime, Adapter, ticket documents and goldens are unchanged by this audit. No staging, commits, Gradle process, game launch, golden regeneration or maintainer sign-off was performed here.

## Audit baseline

The finding sections below describe the pre-integration state at `47dad5ae`. The lead subsequently replaced the separate host arrangement with common snapshot projection and staged Inspector publication. They are historical root-cause evidence, not a claim that those defects remain in the integrated working tree.

## Lead integration and actual client verification

The source-built 26.2 client ran the canonical fixture through Graal and the real Adapter. `client-observations.log` and `live-measurements.json` preserve eight measured records and the native click markers.

- Explicit profiles: `1,2,3,4,5,6`; all kept `renderCount=1` and `inspectorMatchesCommon=true`.
- Human clicked the visibly right-aligned HIT button, pressed F11, then clicked again; two `target-hit` callbacks were recorded at the measured target geometry.
- The human confirmed native resize preserved the UI and operation, then Escape returned to the main menu.
- `live-screen.png` shows the right-aligned target, responsive blue panel, percentage/min-max brown panel and bottom-right anchor.
- Layout projection regressions verify resolved dimensions/clip, failed-candidate retention, interaction state on resize, unmanaged scroll offset, horizontal axis and design-scale units.

The public record parity alone is not used as render evidence: the real native clicks and screenshot supply that separate leg. Native F11 smoke and six explicit logical-profile measurements are different observations. AC2's extended golden/review gap remains as documented below; no whole-ticket sign-off is authored.

## Finding: measurement and rendering use different layout owners

The current NeoForge Adapter can report the correct common measurement while painting and routing input using different coordinates.

| Flow | Source observed | Consequence |
|---|---|---|
| Common resolve/arrange | [runtime:529](../../../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L529), `layoutNode` at 605 | Responsive values, percentage dimensions, min/max, padding, spacing, align/justify, stack anchors, safe area and design scale are resolved into snapshot rect/clip/style. |
| Layout offer | [runtime:1028](../../../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1028) | `adapter.layout(candidate, viewport, snapshot)` runs before transaction creation/commit. |
| Adapter offer handling | [Adapter:171](../../../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L171) | The candidate is read only for materialization; its geometry is discarded. `lastSnapshot` is immediately assigned the offered common snapshot. |
| Retained transaction | [runtime:838](../../../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L838) and [Adapter:873](../../../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L873) | Create/update receives raw candidate props, and commit runs the separate Java `layoutRoots` algorithm. |
| Host arrangement | [Adapter:751](../../../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L751) | Number/`fill` dimensions are understood; responsive objects, percentages, min/max, `spacing`, `direction`, `justify`, anchors and design coordinates are ignored. Child coordinates use those raw/default values. |
| Paint/input | [Adapter:315](../../../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L315) and its hit testing | Rendering and hit testing use retained node coordinates, not the Inspector rects. |
| Inspector | [Adapter:159](../../../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L159) | `inspect()` decorates the same stored common snapshot. Agreement between `root.layout()` and `host.inspect()` is expected even when actual rendering is wrong. |
| Resize | [runtime:1073](../../../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1073) | `root.resize` offers a new snapshot but does not transact/update retained props. Native Adapter resize runs the separate host layout first. |

This violates the [Inspector source-of-truth contract](../../ui-inspector-contract.md#L77) and the [ticket 43 profile/resize contract](../../implementation-tickets/43-jsx-ui-viewport-profiles.md#L3). The root issue is the duplicate layout owner, not missing profile thresholds in common.

## Executed reproduction and limits

Command, from the repository root:

```powershell
node docs/architecture-refactor/evidence/2026-10-05-ticket43-profiles/common-layout-repro.cjs
node docs/architecture-refactor/evidence/2026-10-05-ticket43-profiles/fixture-contract-check.cjs
```

Both returned exit 0. [Common output](common-layout-output.txt) and [fixture output](fixture-contract-output.txt) preserve actual results.

The common reproduction uses the repository's actual runtime source, transpiled by the already-installed TypeScript package, and executes public `UI.createRoot`, `root.resize`, `root.layout` and a fake transaction capture under Node. It does not execute the repository's Graal compiler or the Minecraft Adapter.

- Profiles selected: `1,2,3,4,5,6`.
- Responsive widths: `80,80,80,120,120,160` for `base:80`, overrides `4:120` and `6:160`; profile 5 inherits profile 4.
- A 40-pixel button in a 200-pixel `justify:'end'` row measures at `x=160`.
- The transaction still receives the responsive width object, rather than 80/120/160.
- Across five resize calls, render count stays 1 and commit count stays 1.
- The inspected Java algorithm would place that target at `x=0` and use fallback width 100 for the responsive panel. Those two figures are a source-derived projection, explicitly not an executed Minecraft result.

The executable live fixture's public measurement/serialization logic also ran against a fake host that returns Java-shaped Inspector records. It produced eight records: live initial, six explicit profiles and restored live viewport. Every explicit resize kept `renderCount=1`; the fixture's `parity-target` center measured `(188,46)`.

## Additional finding: rejected measurements can become public

The common reproduction also offers a button width change from 40 to 80 and then rejects transaction commit:

- `root.refresh()` returns false.
- `root.layout()` retains active target `x=160,width=40`.
- The snapshot already passed to `adapter.layout()` contains rejected target `x=120,width=80`.

Because current `JsxHostAdapter.layout()` publishes `lastSnapshot` before commit, `host.inspect()` can expose rejected geometry while the visible host tree remains old. This is a second consequence of the same incorrect publication boundary. The runtime failure path was executed; the specific Java `inspect()` result is proven by source flow, not a live Java invocation.

## Six-profile live protocol for the lead

Stage only [ticket43-profiles-flow.tsx](../../../../src/test/resources/nekojs/client/ui/ticket43-profiles-flow.tsx) in the confirmed launcher CLIENT script directory, isolating other auto-open fixtures and backing up user files. Use the source-built 26.2 jar and preserve its checksum.

The fixture creates its root on `ClientEvents.tickPost`, opens it once, then samples all six explicit logical viewports and restores `host.viewport()`. It logs only bounded measurements and click actions; it does not poll/re-render every frame.

1. Collect eight `[ticket43] measurement` records. Each contains public common nodes, public Inspector nodes, profile, resolved widths, rects/clips and render count. Preserve the initial and restored records.
2. Check six `[ticket43] resize` records: requested and measured profiles must agree; `renderUnchanged` must be true.
3. At the restored live viewport, click the **measured logical center** from the log, normally `(188,46)`. Account for the actual screenshot-to-logical coordinate scale; a downscaled MCP image is not the GUI coordinate system. A correct host produces `[ticket43] target-hit` at that center. The unfixed host paints `HIT` near logical `(8,28)` instead of measured `(168,36)`; its button center is source-projected `(28,38)`.
4. Click the visibly rendered HIT button too. If only this displaced location triggers the marker, it is direct geometry/hit parity failure even though `inspectorMatchesCommon` is true.
5. Press F11 or physically resize the window, click Measure, and preserve the new profile/rects alongside a screenshot. This is the actual client resize smoke. The six explicit `root.resize` samples are contract measurements, not six physical-window smoke passes.
6. Observe the responsive blue panel, 50%-with-min/max brown panel and bottom-right green stack child against logged measurements. Click Next to make an explicit profile change; profile 1 may clip the action controls, so native window resize/CLIENT reload is the recovery path.
7. Press Escape and verify no layout/host failure occurred. Restore staged user files and jar afterward.

Use options to pause for these actual human actions. `inspectorMatchesCommon` deliberately tests the public record projection only; it must never be used as a render-parity verdict.

## Existing golden and ticket 43 AC2

The [six-profile golden](../../../../common/src/test/resources/nekojs/inspector/six-profiles-golden.txt) already exists, introduced by `1136f30a9`. [Ticket45InspectorOutputTest:136](../../../../common/src/test/java/com/tkisor/nekojs/core/module/Ticket45InspectorOutputTest.java#L136) produces records through the real Graal/common path and performs an exact read-only comparison for all six profiles. The existing XML inspected during this audit records 5 tests, zero failures/errors, timestamp `2026-10-03T16:54:56.263Z`; this is prior execution evidence, not a fresh Gradle run.

| AC2 aspect | Evidence already present | Remaining gap |
|---|---|---|
| Six profile IDs, viewport/design scale and deterministic record shape | Six-profile golden, Inspector test, ticket 45 evidence | Present; claiming no golden exists is incorrect. |
| Exact override/lower-profile/base precedence | Runtime implementation; executable `ui-core.tsx`; this new public reproduction demonstrates lower-profile fallback | Existing golden uses scalar props and does not pin responsive override/fallback output. |
| Percentages and min/max | `ui-core.tsx` asserts widths and rejects inconsistent bounds | Those assertions are not represented in the existing six-profile golden. |
| Invalid profile/ratio/viewport behavior | `ui-core.tsx` failure fixtures and common validation | Existing golden includes one fixed invalid min/max error; it does not pin the required invalid-profile/ratio cases. |
| Direction/visibility/anchor/profile coverage | `ui-core.tsx` exercises these features | Existing golden's profile sections repeat the same scalar tree, not responsive per-profile changes. |
| Golden review/update workflow | [REGENERATE.md](../../baseline/2026-09-12-managed-surface/REGENERATE.md) requires reasons, old/new diff and maintainer review | No ticket43-specific coverage extension or corresponding maintainer conclusion is in this evidence. |

Therefore AC2 is **partially evidenced**, not wholly missing and not complete merely because a six-profile file exists. No regeneration is needed just to repeat an unchanged test. If the intended golden coverage is extended, update its canonical fixture/collector first and use an explicit reviewed generation workflow.

A concrete workflow gap exists: [common/build.gradle:174](../../../../common/build.gradle#L174) limits `regenerateGoldens` to `com.tkisor.nekojs.probe.*`; the Inspector test is in `core.module` and contains no regeneration branch. That task cannot regenerate `six-profiles-golden.txt`, and setting `nekojs.golden.regenerate` on this Inspector test does not add such a branch. Do not advertise either command as a working Inspector generator. Lead may wire a narrowly scoped explicit generation path before extending canonical inputs; no golden is hand-patched here.

## Minimal correction for integration

Lead owns existing sources; this audit intentionally leaves them untouched.

1. Apply **committed** common snapshot rect/clip/resolved style to retained host nodes in the same structural order as transaction roots/children; do not reimplement profile resolution or match only by script id (ids are optional). Preserve input/capture identity state.
2. Make profile-only resize update those measured host facts without guest render or recreating the whole node tree.
3. Keep a candidate snapshot separate until the host transaction succeeds; rollback retains the old Inspector snapshot. Resize publication needs its own successful apply boundary because current resize does not begin a host transaction.
4. Paint and hit test against the same committed rect/clip, respecting ancestral clipping. Keep host-owned scrolling reconciled with the common measurement contract instead of silently relabeling raw props.

This is a staged-measurement/application seam at the existing Adapter owner. A Java unit test that reflects private layout methods or duplicates this algorithm would not prove the public root/Adapter contract. No such test was added. The prepared public client fixture provides the decisive regression loop for the lead; actual NeoForge hit/render execution remains **NOT RUN by this subagent**.
