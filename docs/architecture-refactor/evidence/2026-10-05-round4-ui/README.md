# Round 4 JSX UI follow-up

Baseline: `551ad922`. The round uses the existing public seams: canonical JSX runtime and Fake Adapter layout, native Screen input/paint, Inspector decoration, and native narration collector output.

## Implemented

- Ticket 44 now accepts controlled `label.font` responsive ids. Common keeps the authored selector for measurement/Inspector status while `VisualSpec.font` strips one `.json` suffix for the native `FontDescription.Resource`; default labels retain the three-argument measurement contract. Resource lookup uses native singular `font/`, validates bounded JSON through Minecraft's provider codec, preserves causes, reports missing `NEKO-6004` and decode `NEKO-6007`, and does not claim provider load success from file presence alone.
- NeoForge host measurement and painting use the same selected Style. Text width, cached line layout and `GuiGraphicsExtractor.text(Font, Component, ...)` therefore agree. Inspector font statuses use a separate resolver from textures.
- Ticket 41 now preserves hidden descendants in canonical snapshots, rejects input/focus/capture through hidden ancestors, revalidates escaped host transactions on every operation, and skips invisible focus subtrees. Native regressions cover hidden-parent publication, owner-thread rejection, closed-generation rejection, retained focus after callback reentry, tooltip clipping/disabled/hidden behavior and text painting.

## Verification

Focused common font/runtime/resolver/Inspector suites: 106 tests, zero skips/failures. Probe declaration regeneration changed only optional `measureText(..., font?)` and label `font?: NekoUiResponsive<string>`; `npm.cmd run test:probe-types` passes. The full common, API processor, five-node tests/builds and `guardLint` pass.

The native 26.2 focused run passes native font measurement/painting, font resource loading, hidden subtree, transaction boundary and prior mouse tests. Plain JVM narration trigger cannot initialize `SharedConstants` without an FML loader; the public collector/title path is tested, but automatic narrator audio is not claimed.

## Live acceptance

Environment: Minecraft 26.2.0.75 / NeoForge / Zulu Java 25 / MCP 0.2.1, project jar built for 26.2.0.57. Initial screenshot showed default and custom bitmap fonts, larger font size, missing and corrupt fallback. Log evidence records selected width `68` versus default `24`, `NEKO-6004` for the missing font, and CLIENT resource reload. The first bitmap was visually an arch; it was replaced with a crossbar version and the maintainer confirmed the repaired font screen, tooltip and healthy callback through the option tool. Automatic narration was reported as incorrect/not accepted and remains unverified. Final screenshots and logs are `initial-font.png`, `after-font-repair.png`, `final-font.png`, `font-client-final.log`.

The test client, jar, fixture and `ticket44_round4` resource directory were stopped/removed after capture. The original game setup remains intact.

## Remaining limits

Ticket 41 remains in-review for managed-constructor candidate scheduling, full owner-thread/generation matrix and truthful native narration conclusion. Ticket 44 remains in-review for whole-ticket maintainer conclusion and robust custom provider authoring; this round validates controlled selection, bounded definition parsing and fallback, not arbitrary provider compatibility. Ticket 39 independent-client synchronization, ticket 36 maintenance trials, ticket 37 handoff and ticket 48 combined proof remain open.
