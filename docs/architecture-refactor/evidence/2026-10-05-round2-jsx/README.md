# Round 2 JSX implementation and acceptance

Baseline: `fd958988`, branch `mult`. The maintainer confirmed the public UI testing seams through the option tool: `UI.profileFor` / root resize, Fake Adapter commit/layout outputs, and native Screen mouse input/paint output. The lead owned visual painting and delivery records; parallel children owned common profile golden fixtures/tooling and native input handling. Unrelated working changes are outside this scope.

## Delivered behavior

- Ticket 43: exact/lower/base responsive precedence, fallback, six-profile percentage min/max, invalid input diagnostics and resize retention are covered by a dedicated generated public golden. A regression found constraints using a child's already allocated dimensions instead of the parent inner space. Constraints now precede row/column budget, alignment, justification and stack anchors. Nine public tests cover the final behavior. The maintainer accepted the concrete [golden review](golden-review.md), completing AC2 and closing ticket 43.
- Ticket 41: click caret placement uses measured glyph widths; Shift-click, outside-input dragging and final release coordinates preserve codepoint boundaries. Capture follows retained identity and button; disable, removal, replacement and close cancel it. Focus is reacquired after blur callbacks so guest reconciliation cannot focus a stale node or a same-id replacement. Hidden subtrees are skipped in Tab order.
- Ticket 44: prepared rounded backgrounds and requested-width borders are clipped to the measured and physical viewport bounds. Borders composite over the background before element opacity, with no overlap/double-alpha painting. Seven tests submit through the public Adapter layout/transaction and observe actual Screen paint output. Font-selection and reload facts are recorded in the [read-only audit](font-reload-audit.md); custom font remains unimplemented.

## Verification

- `:common:regenerateUiProfileGolden`: PASS through the explicit task; ordinary missing-baseline comparison was observed failing first. The new 200-line golden complements the unchanged 84-line Inspector golden. Normal comparison never updates either baseline.
- `:common:check :common-api-processor:test :26.2.0:build :26.1.2:test :1.21.1:test :26.1.2-fabric:test :26.2.0-fabric:test guardLint`: PASS after reviewed fixes.
- `npm.cmd run test:probe-types`: PASS; no declaration shape changed.
- After the live button-clipping report, `:26.2.0:build :26.1.2:test`: PASS. Each affected node executes 16 native mouse/button tests and 7 native box painting tests with zero skips/failures/errors.
- `:1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build`: PASS. `tools/nekojs-ci-gates.py source-roots --node <each node>` and `all --out build/nekojs-gates-report.json`: PASS; [persisted report](platform-gates.json) has four checks with zero failed entries. Node test counts are 363 / 569 / 569 / 293 / 293, each with zero failures; skips remain skips.

One earlier full common run failed in untouched `ServerPackCacheTest.physicalRestorePropagatesDeleteFailureAndRetainsStaging` with a Windows AccessDenied during temporary-file replacement. Isolated cache rerun passed, and the subsequent complete common check passed. No cache test or gate was altered.

## Two-axis review

Standards initially reported one P2: internal-only box tests did not cover the agreed Screen seam. All four geometry cases were moved into the public native Screen tests, together with alpha/clip wiring.

Spec initially reported two P2s: focus retained a stale target after blur callback reconciliation, and transparent borders lost the background beneath them. Both were reproduced through public native tests, corrected and rerun. Follow-up source review reports Standards 0 unresolved scoped findings and Spec 0 unresolved scoped findings. The later button position fix also has a red/green native drawing regression.

## Real client evidence

Environment: Minecraft 26.2, NeoForge 26.2.0.75, Zulu Java 25.0.3, MCP 0.2.1, game directory `%APPDATA%/.minecraft/mcp_launcher/game`. Project compilation targets NeoForge 26.2.0.57; live launcher drift is retained as an environment fact.

Initial tested jar: SHA-256 `FF35111891CEEFFC466B88C9CFA88B2D7C523FAB93E8A077B63D7EBF25D9131F`, client PID 23304. The [combined fixture](../../../../src/test/resources/nekojs/client/ui/round2-input-visual-repair.tsx) showed square, rounded and half-opacity rounded panels and a corrupt image placeholder. [Initial screenshot](initial-screen.png) and [mouse result](after-mouse.png) pair with [first-client-observations.txt](first-client-observations.txt). The maintainer confirmed mouse selection, outside-input dragging and all three panel visuals. Actual changes include `X mouse selection` and final `Y`.

The owned corrupt PNG was replaced with [the green/white-cross pattern](repaired-pattern.png). The maintainer confirmed F3+T then visibly repaired pixels, a working healthy button and Escape. The log records ResourceManager reload, CLIENT script reload, a recreated Screen with renderCount=1 and its healthy callback. This is new-generation readback, not same-root/no-rerender resource repair. Full Inspector log text is truncated by the runtime logger, so this pack does not claim an independently logged RESOLVED status or a post-reload image screenshot; the visible-image conclusion is the actual maintainer observation.

The [advanced mouse fixture](../../../../src/test/resources/nekojs/client/ui/ticket41-mouse-selection-flow.tsx) first exposed partly clipped button text in [this screenshot](mouse-fixture-overflow.png). Native measurement gave an intrinsic 9-pixel button, while drawing used a fixed 6-pixel top inset. The native regression failed (y=26 vs expected20), then drawing was changed to center text within the committed height.

The second live jar includes that fix: SHA-256 `64CEE042FB00089CE006B4229C2168CCAEC50B177336ABC41D37414C57E9CCB6`, PID 25948. [Fixed buttons](mouse-buttons-fixed.png) display complete text. The maintainer confirmed selection survives two-second reconciliation, disabling and same-id/new-key replacement cancel capture, and Escape closes. [Mouse log](mouse-client-observations.txt) records `change=WX`, arm/application of disable and replacement, and no subsequent stale release callback. Holding across the exact action instant is the maintainer's observation, not independently timed telemetry.

Actual selections, including the initial button failure, are saved in [maintainer-options.json](maintainer-options.json). Original example script hash is unchanged (`D7D7EBEB82CD5676F5788883B7C69AEA62F3FACFEEA370220DD2E5C3EB562A72`). Both test clients were stopped; the newly added jar, fixture and owned PNG were removed. Jar removal initially raced the exiting process's file handle, then completed after process exit with explicit absence checks. No original game file was overwritten.

## Remaining scope

Ticket 41 stays in-review for full narration/tooltip and remaining owner-thread/generation matrices. Ticket 44 stays in-review for controlled custom-font selection and whole-ticket maintainer conclusion; this round covers radius and new-generation repaired-image readback only. Ticket 39 separate-client synchronization, ticket 36 maintenance trials, ticket 37 handoff and ticket 48 combined performance/end-to-end proof remain outside this round's completed evidence. The project goal remains active.
