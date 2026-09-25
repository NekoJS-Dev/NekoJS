# JSX UI Inspector Contract

**Status:** Implemented by ticket 45. This document is the frozen contract for the UI
Inspector: the read-only measurement tool for JSX UI roots. It is a runtime fact tool —
it depends on no AI authoring documentation and no web conversion rules; later AI
workflows (46/47) only consume its output.

## Shape

All types live in `com.tkisor.nekojs.api.ui` (common, no Minecraft/loader deps):

- `UiInspector` — port: `InspectorSnapshot inspect()`; null before the first frame.
- `InspectorSnapshot` — `rootId`, `source` (collecting environment label), `viewport`,
  `nodes`, `diagnostics` (layout), `errors` (phase-tagged `PhaseError`), `screenshot`.
- `InspectorViewport` — width/height, `SafeArea`, content size, `profile` (1-6),
  `guiScale`, `designScale`.
- `InspectorNode` — id/type/key, `visible`, `focused`, `rect`, `clip`, `overflow`
  (per edge), `scrollOffset`, `style` (final resolved props — responsive values already
  flattened; this is both the final props and the resolved style), `bindings` (sorted
  event names bound on the node, never callbacks), `resources` (ticket 44
  `ResourceStatus` for the node's `resource`/`icon` ids), `children`.
- `InspectorScreenshot` — `source`, `width`, `height` metadata only. No pixels,
  buffers, or screenshot objects cross the contract; capture evidence is auxiliary and
  must accompany measurement/behavior assertions, never replace them.
- `SnapshotDiffer.diff(reference, actual, referenceImage)` → `SnapshotDiff`: pure data
  comparison, no host state.

Every record is frozen data: a snapshot never holds live runtime references, so it stays
valid while the UI keeps changing.

## Collection path (double output)

Both hosts collect through one implementation, `InspectorSnapshots`:

1. `read(rootId, source, snapshot)` converts the common JSX runtime's public layout
   snapshot — the frozen object already delivered to `NekoUiHostAdapter.layout` — into
   records. It accepts a guest `Value` or plain host data (`Map`/`List`/scalars in the
   same shape); the fake host and the NeoForge host therefore produce isomorphic records
   by construction. A shape mismatch fails with `NEKO-8001` (runtime/host skew), keeping
   the last good frame.
2. `decorate(base, focusedIds, resourceResolver, errors, screenshot)` fills host-only
   facts: focus (by node id), controlled-resource statuses, retained phase errors, and
   capture metadata. The same decoration rules run on every host.
3. `canonical(snapshot)` renders the deterministic text form used by golden files.

Host wiring:

- **Fake host** (`ui-inspector.tsx` fixture + common tests): the runtime's snapshot is
  read through the same `InspectorSnapshots` path; the six-profile golden
  (`common/src/test/resources/nekojs/inspector/six-profiles-golden.txt`) pins the
  decorated output for all six viewport profiles. Golden files are read-only for normal
  tests; updates must go through an old/new diff and maintainer review.
- **NeoForge host** (`JsxHostAdapter` implements `UiInspector`): `layout()` retains the
  converted snapshot; `reportDiagnostic()` retains the last 8 phase errors;
  `inspect()` decorates with focused ids from the retained host tree and statuses from
  the disk-pack resolver, and names the retained frame `neoforge-viewport-meta <w>x<h>`
  (the measured frame's own viewport, not the live one). Real pixel capture is not
  wired; when a live client captures frames, only the metadata record changes.

## Difference report

`SnapshotDiff` ranks entries by deviation (absolute for numbers, 1 for discrete
changes, `Double.MAX_VALUE` for structural breaks — a node missing or added outranks any
measurement drift), so `largest()` names the node/property that drifted most. Provenance
is retained on the report: both snapshot sources, both profiles and viewports, the
reference image id, and the actual capture metadata. Screenshots and the reference image
never produce entries — only public measurements and behavior facts are compared.

## Viewport profile ownership

`InspectorViewport` is the inspector's minimal own contract. Its values come from the
common runtime's public `resolveViewport` — ticket 43's six-tier profile rule
(delivered, in-review): selection by logical viewport, capped by
`capabilities.maxProfile`. A future change to 43's derivation replaces only where these
values come from, not the inspector field shape.

## Sources of truth

- Layout/rect/clip/overflow/style/bindings/diagnostics: the frozen
  `NekoUiLayoutSnapshot` the runtime publishes (and hands to `adapter.layout`) — never
  reconciler internals, never `GuiGraphics`, never host node identity.
- Resource statuses: ticket 44 `UiResourceResolver`/`ResourceStatus`.
- Diagnostics text: layout diagnostics strings (`<id>:overflow-<edge>`) plus retained
  phase errors; visual diagnostics keep using ticket 44 `UiDiagnostic.logLine()`.
