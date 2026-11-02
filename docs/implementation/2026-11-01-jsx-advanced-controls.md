# Advanced JSX controls delivery — 2026-11-01

## Objective and scope

Build the next UI capabilities before human acceptance: caller-controlled Tabs, a genuine modal Dialog, synchronous form validation, and read-only Minecraft item visuals/tooltips. Native support remains NeoForge 26.x; this change does not add Fabric/1.21.1 screens, inventory Slots, server-authoritative transactions, networking, browser CSS, or virtualization. No version bump, commit, or push is included.

## Contracts

- The canonical `nekojs/jsx-runtime` module exposes named `Tabs`, `Dialog`, `validateForm`, and `Validators`; these are not added as `UI` aliases. Its primitive catalog has fourteen entries, including raw `dialog` and `item`.
- Tabs retain state in the caller, render only the selected pane, reject ambiguous values, and skip disabled tabs during keyboard selection.
- Dialog is a viewport portal, excluded from ancestor flow and clipping. Only the top modal receives input; common dispatch and native routing both reject background events. Escape/backdrop request `onOpenChange(false)` rather than mutating caller state. Focus and capture follow modal lifecycle and generation ownership.
- `validateForm(values, rules)` accepts plain scalar records and synchronous validators; ordered error lists and results are frozen. Required permits false/zero; numeric ranges accept nonempty finite numeric strings. Async/nonstring validator results are rejected. Validation does not persist data or authorize a server operation.
- Item visuals accept a controlled item resource id, count 1..99, optional boolean `itemTooltip`, and optional string tooltip override. Native staged resolution caches a detached non-air stack. They are not inventory slots. Invalid native item configuration uses `NEKO-6012`.

## Implementation evidence

- Canonical source: [jsx-runtime.ts](../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts).
- Complete bundled example: [forms-and-dialogs.tsx](../../common/src/main/resources/nekojs/authoring/examples/client/forms-and-dialogs.tsx). External signals preserve form values across tabs; validation precedes local confirmation; main and dialog contents use explicit scroll containers.
- Real engine integration: [AuthoringExamplesEngineTest.java](../../common/src/test/java/com/tkisor/nekojs/authoring/AuthoringExamplesEngineTest.java), with only the native host replaced. The new flow checks selected-pane mounting, disabled tabs, required/range/integer errors, Java Map event payloads, modal background rejection, cancel/dismiss/confirm and teardown. This does not prove native rendering or OS input.
- Focused runtime regressions: [JsxAdvancedControlsRuntimeTest.java](../../common/src/test/java/com/tkisor/nekojs/core/module/JsxAdvancedControlsRuntimeTest.java). Eight real-engine/extractor methods cover frozen form snapshots, validator failures, lazy controlled Tabs, portal geometry, top-modal manual/queued dispatch, item schemas, and wrapper defaults. The strict TypeScript fixture has twenty-two expected negative cases.
- Native regressions: [NativeDialogModalTest.java](../../src/test/java/com/tkisor/nekojs/client/ui/NativeDialogModalTest.java), [NativeItemVisualTest.java](../../src/test/java/com/tkisor/nekojs/client/ui/NativeItemVisualTest.java), and [JsxItemVisualTest.java](../../src/test/java/com/tkisor/nekojs/client/ui/JsxItemVisualTest.java). These exercise the bundled runtime/native adapter without a window, staged item cache replacement, tooltip routing and failed-candidate preservation. Two helper tests require actual vanilla registry/component availability and must be reported as skipped when those assumptions are unavailable.
- Generated declaration changes intentionally add the two primitives and named advanced helpers, form/tab/dialog contracts and dismiss reasons. The current working-tree diff also contains the previous delivery's Toggle/Slider/Select and appearance changes; these are not new advanced-delivery changes. Declarations were regenerated only through the filtered canonical golden test, then checked without regeneration.
- Independent shared review found a new-item `tooltip:null` hole. A narrow item-only string check and real-engine negative regression fix it without changing older primitives' null-as-omitted behavior. The first integrated example failure was a test-host lookup before screen creation; moving the lookup after the actual module entry load fixed the fixture, not production.
- Native two-node regressions exposed invalid fixture construction (children passed as the JSX key; explicitly supplied undefined optional props; unsupported narration/label props; missing staged root order) and an unrelated nonmodal focus change introduced by modal synchronization. Fixture corrections use the existing strict contracts. Modal synchronization now leaves a modal-free, unchanged tree alone, with an explicit focus/capture regression; the previous control appearance assertion remains unchanged. Verification of the corrected native fixtures is recorded below.

## Verification status

| Check | Status |
|---|---|
| First validation tracer before implementation | Failed as expected: `TypeError: Cannot read property required of undefined`; one test, no compilation failure. |
| Focused common/native regressions | Passed: shared seven-class rerun includes all eight advanced runtime methods and the actual bundled forms flow. Each native UI node ran 38 selected tests (10 modal, 5 item adapter, 9 item helper, 14 previous-control regressions): 36 passed, two explicitly unavailable vanilla registry/component assumptions skipped, and no failures/errors. |
| Generated declaration regeneration and strict TypeScript fixtures | Passed: filtered canonical golden generation, subsequent read-only assertion and full `npm run test:probe-types`, including the twenty-two negative advanced JSX cases. |
| Common isolation/check/assemble | Passed: 2702 tests in 368 suites, zero failures/errors, four skips; processor tests and isolation checks passed. `guardLint`: 394 guards, 593 files, zero warnings. |
| Five-node production builds, NBT and source-root gates | Passed: all five builds; three NeoForge NBT smoke tasks; fresh source-root traces on all five nodes; all four CI gates, zero failures. Node suites report 472/753/753/438/438 tests, zero failures/errors, and 33/85/85/39/39 skips respectively. |
| Wiki links, examples and anchors | Passed: 30 topics, eight tooling tests, 60 pages/284 syntax-only code blocks, 52 existing anchor sequences and 234 evidence targets; 30 topics remain human-review pending. |
| Source/artifact bundle consistency | Both common JARs passed: 35 source-matching payloads, identical manifests and hashes; stdio class isolation and Java 21 bytecode ceiling passed. All five production node artifacts also passed payload/source/manifest hashes, canonical JSX runtime identity, and NeoForge-26-only native adapter/modal/item helper inclusion. |
| Live NeoForge 26.1/26.2 UI and maintainer acceptance | Not run; explicit pending checklist in bundled documentation/wiki. |

The common and five production node bundle id is `0b53b6c3c8fef6704e657e78517efb0cc5d26a8c123b5678bf89451f18d60e32`. No release version was changed. The source-matching bundle includes the new complete forms/dialogs example; existing Skills and AGENTS routers already reach the updated handbook, so no new template layer was introduced.

## Reproduction commands

Use the repository test toolchains (common tests run on Java 21); the local Gradle daemon used Zulu 25. Run Gradle and the source-root driver serially. Ordinary verification below does not regenerate goldens.

```sh
./gradlew.bat "-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25" :common:check :common-api-processor:test :common:assemble guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest --console=plain
npm run test:probe-types
for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do
  python tools/nekojs-ci-gates.py source-roots --node "$node" || exit 1
done
python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
node scripts/wiki-docs.mjs sync
node scripts/wiki-docs.mjs stamp --all
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
git diff --check
```

## Remaining acceptance boundaries

GPU item rendering, real item hover tooltips, live window resizing/GUI scale, physical keyboard/mouse interaction, modal focus narration, OS clipboard/IME integration and actual reload behavior require live client acceptance. Fake native adapters and no-window fixtures are scoped evidence, not that acceptance. Previous completed JSX enhancements remain separate historical evidence; this report will record only checks rerun for this delivery.
