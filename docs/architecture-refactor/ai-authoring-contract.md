# AI UI Authoring Contract

**Status:** in-review (ticket 47 deliverable; examples verified executable 2026-09-25, awaiting maintainer review)
**Audience:** AI agents that generate, self-check, diagnose, and locally repair NekoJS JSX UI code, and the humans who review that output.

## What this document is — and is not

This is a contract for **AI-assisted authoring** of JSX UI on the NekoJS runtime. It defines what an
AI must receive before authoring, what it must produce, how it must verify its own output, how it
must read Inspector output to make **local** repairs, and how it must report everything it could not
convert.

It is **not**:

- an automatic browser compatibility layer — web input is converted case by case with explicit
  downgrades, never compiled generically;
- a general-purpose HTML/CSS compiler — there is no runtime HTML parser, no CSS parser, no DOM
  ([ticket 46 scope](../../architecture-refactor/implementation-tickets/46-jsx-ui-web-conversion-contract.md) forbids adding one);
- a one-shot generator that skips acceptance — every produced screen stays subject to fixture
  execution, Inspector measurement, and human review (see [Verification](#11-example-verification-map)).

Equivalence priority order, fixed: **structural > interaction > reactive > visual**. Here
*reactive* names signal/store state-propagation equivalence (state changes produce the same
observable behavior); the term is frozen by ticket 46's report schema (`reportVersion: 1`), and
responsive-profile equivalence is verified separately per profile through the verification record
(section 10) rather than folded into this axis. Visual
equivalence is bounded: Minecraft fonts, GUI scaling, safe areas, and rasterization differ from any
browser, so pixel-perfect output is never promised and never a repair goal.

## 1. Fact sources

Every capability, prop, event field, state API, resource rule, and diagnostic code cited in this
contract is backed by one of the sources below. **An AI authoring UI may only use capabilities that
appear in these sources.** Anything else must be reported as `unsupported` (section 10), never
invented.

| Fact source | Path | What it proves |
|---|---|---|
| JSX runtime implementation | `common/src/main/resources/nekojs/node/modules/jsx-runtime.ts` | Primitive registry, prop validation, layout engine, signal/store, events, root lifecycle |
| Managed declaration (generated) | `common/build/probe-ts/generated/jsx-runtime.d.ts` (extracted by `NodeModuleTypeDocs.extractTS`, asserted in `NodeModuleTypeDocsTest`) | Extraction output for inspection; identical content is frozen as the committed golden below |
| Probe golden + gate | `common/src/test/probe-ts/generated/index.d.ts`, `common/src/test/probe-ts/generated/jsx-runtime.d.ts` (kept identical to the extraction by `JsxRuntimeProbeDeclarationGoldenTest`), `common/src/test/probe-ts/jsx-primitive-props.tsx`, `npm run test:probe-types` | Frozen managed declaration the probe gate enforces; the jsx-runtime golden is the TypeScript surface the gate (and AI authoring) typechecks against, green on a fresh checkout |
| Fake-host runtime proof | `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-core.tsx`, executed by `NekoTypeScriptJsxRuntimeTest#automaticJsxUiRuntimePassesTheFakeHostContract` | Observable runtime behavior: profiles, keyed reorder, thread queueing, error retention |
| Authoring proof fixture | `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx`, executed by `TypeScriptUiAuthoringDocsTest` | Every section 5 catalog example in this document and both `docs/ui-conversion/` outputs run and pass |
| Color grammar | `common/src/main/java/com/tkisor/nekojs/api/ui/UiColor.java` | Controlled color forms |
| Resource id grammar | `common/src/main/java/com/tkisor/nekojs/api/ui/UiResourceId.java` | Controlled resource identifiers |
| Visual prop resolution | `common/src/main/java/com/tkisor/nekojs/api/ui/VisualStyleResolver.java`, `VisualSpec.java` | Host-side visual validation and NEKO-6001/6002/6006 diagnostics |
| Resource resolution | `common/src/main/java/com/tkisor/nekojs/api/ui/UiResourceResolver.java`, `ResourceStatus.java`, `DiskPackUiResourceResolver.java` | NEKO-6003..6007 resource diagnostics, single resource-root policy |
| Diagnostic record | `common/src/main/java/com/tkisor/nekojs/api/ui/UiDiagnostic.java`, `UiErrorCodes.java` | Locatable UI diagnostic shape and the 6xxx code registry |
| Generation lifecycle | `common/src/main/java/com/tkisor/nekojs/core/state/GenerationGlobals.java` | NEKO-7001/7005 UI-root/generation binding |
| Inspector contract (ticket 45, frozen) | `docs/architecture-refactor/ui-inspector-contract.md`; `common/src/main/java/com/tkisor/nekojs/api/ui/`: `UiInspector`, `InspectorSnapshot`, `InspectorNode`, `InspectorViewport`, `InspectorScreenshot`, `SnapshotDiffer`/`SnapshotDiff`, `InspectorSnapshots` | Inspector records, difference report, canonical golden text, NEKO-8001 |

Pending fact sources (fill in when the parallel tickets land — see [section 12](#12-pending-backfill)):

- **Ticket 46** (landed 2026-09-25): the conversion **input contract** and **report contract**.
  Section 3's checklist is formalized in
  [cookbook §1](../../ui-conversion/web-to-jsx-cookbook.md#1-input-contract-ticket-46)
  (completeness values, uncertainty linkage, per-profile reference requirement); section 10's
  report format gained the `verification` block and is machine-checked by
  `WebConversionReportContractTest` against the shipped fixture reports.

## 2. Runtime model in one page

- Authoring target is `.jsx`/`.tsx` using the automatic JSX runtime. Lowered code imports
  `jsx`, `jsxs`, `Fragment`, and `UI` from `'nekojs/jsx-runtime'`; scripts never configure a
  classic factory (asserted by `NekoCompilationPipelineJsxRuntimeTest`).
- Lowercase tags are the **frozen set of 11 host primitives** (section 5). Uppercase tags are plain
  function components returning VNodes. No Java class or browser tag ever becomes a host.
- A UI root is created with `UI.createRoot(renderFn, hostAdapter, options)` **on the owner
  thread**. The render function returns VNodes; the runtime validates, lays out, and commits them
  through the host adapter's transaction. Render runs on invalidation, not per paint frame.
- State is explicit: `UI.createSignal` / `UI.createStore`. Signals read inside a successful render
  are tracked automatically; writes mark affected roots dirty. `UI.batch(fn)` coalesces
  invalidation.
- Events are dispatched by id: `root.dispatch(id, eventName, payload)` delivers a **frozen** event
  object (section 6.3). A node without an `id` can never receive a dispatched event.
- Failures are phase-tagged (`render`, `component`, `layout`, `event`, `host-update`) and
  reported through `adapter.reportDiagnostic({rootId, phase, error})`. A failed reconcile keeps the
  last committed tree (transaction rollback); a failed initial reconcile throws.
- Thread discipline: off-owner-thread `signal.set`/`update`, `root.refresh/resize/dispatch/close`
  are enqueued (`'queued'` return) or throw when the adapter rejects the queue.

**Lowering constraints** (verified against the current pipeline by this ticket's proof fixture —
workarounds are mandatory until the compiler changes):

1. An **uppercase component tag inside a `{...}` expression child does not compile** (for example
   `{items.map(item => <Card ... />)}` fails with "Missing JSX element name"). Use
   `UI.element(Component, props, key)` inside the expression instead — the exact pattern
   `ui-core.tsx` uses. Lowercase primitives inside expression children are fine.
2. **`//` line comments are not JSX children**: a bare `//` inside an element body is parsed as
   content and derails the parse. Keep comments at statement level.

## 3. Input checklist (what the AI must hold before authoring)

Do not start authoring until every item is either present or explicitly recorded as an
`uncertainty` in the report. Missing input never gets invented. The normative, machine-readable
definition of this checklist — completeness values and the uncertainty linkage rules — is the
ticket-46 input contract:
[cookbook §1](../../ui-conversion/web-to-jsx-cookbook.md#1-input-contract-ticket-46).

1. **Structure** — semantic HTML (or equivalent description) with explicit hierarchy and element
   identity.
2. **Styles** — the layout and visual CSS that the structure actually uses (flex, sizes, colors,
   borders, spacing).
3. **Resources** — every image/icon/font with its intended source; each must map to a controlled
   `namespace:path` resource id or be reported unsupported.
4. **Fonts and text intent** — font sizes, emphasis, wrapping expectations (font *family* itself has
   no script prop; see cookbook).
5. **Interaction spec** — what each control does on click/release/key/input/focus/blur/scroll, and
   the state transitions (including disabled/loading states).
6. **Responsive intent** — target behavior across viewport profiles 1–6 plus per-profile reference
   sizes or screenshots (required input item; absence must be recorded as an `uncertainty`).
7. **Runtime constraints** — which generation/thread opens the screen, and whether the screen must
   pause the game or close on escape.

## 4. Output structure (what the AI must produce)

Every authoring deliverable consists of exactly three parts:

1. **A JSX/TSX module** — human-readable, human-modifiable: function components, explicit
   `UI.createSignal`/`UI.createStore` state, no root creation at module top level (roots bind the
   current generation and owner thread; a closed generation rejects registration with NEKO-7001).
   The module exports its render entry points and state for harness/testing access.
2. **A conversion report** — the machine-readable JSON of section 10, listing every mapping
   decision, downgrade, uncertainty, and needs-human item.
3. **Self-check results** — the completed checklist of section 7, with the verification fixture run
   recorded (which fixture, which checks passed).

## 5. Primitive catalog

Ground truth: the `PRIMITIVES` whitelist, the `NekoUiSharedProps` table, and the
`NekoUiPrimitivePropsByType` per-type prop tables in `jsx-runtime.ts`, validated by
`validateProps` and proved by `ui-core.tsx`. The registry is exactly:
`screen, panel, row, column, stack, scroll, label, button, input, image, spacer`.

### 5.0 Shared props (available on every primitive)

| Prop | Type | Default | Notes |
|---|---|---|---|
| `id` | string (non-empty, unique per root) | none | Required for event dispatch and Inspector addressing |
| `key` | string \| finite number | none | Sibling identity for reconciliation; normalized to string |
| `width`/`height` | `number \| 'auto' \| 'fill' \| \`N%\`` | `auto` (roots fill) | `fill` takes the available main-axis share; `%` of available space |
| `minWidth`/`maxWidth`/`minHeight`/`maxHeight` | same as width/height | none | min must not exceed max (layout failure otherwise) |
| `gap`, `spacing` | number ≥ 0 | 0 | Child spacing; `spacing` wins when both are set |
| `padding` | number ≥ 0 | 0 | Uniform inner padding |
| `align` | `'start' \| 'center' \| 'end' \| 'stretch'` | `start` | Cross-axis child alignment; per-child `align` overrides the parent's |
| `justify` | `'start' \| 'center' \| 'end' \| 'spaceBetween' \| 'spaceAround'` | `start` | Main-axis distribution |
| `direction` | `'row' \| 'column'` | per type (row→row, else column) | Explicit override allowed on any container |
| `anchor` | 9 values (`topLeft`…`bottomRight`) | `topLeft` | Positioning inside `stack` |
| `coordinateSpace` | `'logical' \| 'design'` | `logical` | `design` scales sizes by `viewport.designScale` |
| `visible` | boolean | `true` | `false` collapses to 0×0 and skips children |
| events | see 6.3 | none | 9 callbacks, function-or-null |

**Responsive form**: every layout/visual prop above (and `fontSize`, `scrollOffset`) accepts
`{ base?: T, profiles?: Partial<Record<1|2|3|4|5|6, T>> }`. Resolution picks the highest profile
key ≤ the current profile that has an entry, falling back to `base`. Profile keys outside 1–6 are
rejected at validation.

**Viewport profiles** (from `PROFILE_WIDTHS`/`PROFILE_HEIGHTS` and `resolveViewport`): the
profile is `min(tier(contentWidth), tier(contentHeight), capabilities.maxProfile ?? 6)` with tiers
P2 = 320×180, P3 = 480×240, P4 = 640×360, P5 = 854×480, P6 = 1280×720 (content area = viewport minus
safe area). P1 is anything below P2. `guiScale` does not change logical selection.

**Common errors (all primitives)**: unknown prop → `layout` failure "Unsupported prop for
<type>: <name>"; invalid `align`/`justify`/`direction`/`anchor` value; negative `gap`/
`spacing`/`padding`/`fontSize`/`scrollOffset`; `min` > `max`; malformed `%` string;
`id` empty or duplicated; duplicate sibling `key`; props containing host objects or cycles
(TypeError at element creation); responsive `profiles` key outside 1–6.

### 5.1 `screen`

- **Purpose**: the root container of one Minecraft Screen.
- **Children**: element children (normally one layout container that fills the viewport).
- **Props**: `title?: string`, `pausesGame?: boolean`, `closeOnEscape?: boolean` (+ shared).
- **Layout**: root-level nodes fill the viewport content area; default direction `column`.
- **Profile behavior**: shared responsive props apply.
- **Events**: the shared nine (section 6.3); dispatch requires `id`.
- **Common errors**: shared set. (`title`/`pausesGame`/`closeOnEscape` are declared types; the
  common runtime validates the shared set and prop names, and passes these through to the host.)
- **Minimal example** (`screen-1` in the proof fixture):

```tsx
<screen id="catalog-screen" title="Catalog" pausesGame={false} closeOnEscape={true}>
  <column id="catalog-body" width="100%" height="100%" padding={8} gap={4}>{body}</column>
</screen>
```

### 5.2 `panel`

- **Purpose**: the visual container (background, border, corner radius).
- **Children**: element children; default direction `column`.
- **Props**: `background?: string | number`, `borderColor?: string | number`,
  `borderWidth?: number`, `radius?: number`, `opacity?: number` (+ shared).
- **Visual behavior**: colors follow the [controlled color grammar](#color-grammar);
  `borderWidth`/`radius` are non-negative integers and `opacity` a fraction in `[0, 1]` (host
  visual resolver reports NEKO-6002 for out-of-range values, NEKO-6001 for unparseable colors).
- **Profile behavior**: shared responsive props apply; visual props are not responsive.
- **Events**: the shared nine (section 6.3).
- **Common errors**: NEKO-6001 color forms (`rgb(...)`, `hsl(...)`, named colors outside the CSS
  basic set); NEKO-6002 negative or fractional `borderWidth`/`radius`, or `opacity` outside
  `[0, 1]`.
- **Minimal example** (`panel-1`):

```tsx
<panel id="catalog-card" width={120} padding={8} background="#F0F0F080"
       borderColor="navy" borderWidth={1} radius={4}>
  <label id="card-title" fontSize={10}>{'Panel'}</label>
</panel>
```

### 5.3 `row`

- **Purpose**: horizontal linear container.
- **Children**: elements laid out left→right.
- **Props**: shared only.
- **Layout**: main axis horizontal; `fill` children share the remaining width equally; cross axis
  uses `align` (`stretch` fills cross size when the child does not pin it); `justify` distributes
  leftover main-axis space.
- **Events**: the shared nine (section 6.3).
- **Profile behavior**: shared responsive props apply.
- **Common errors**: shared set; expecting CSS `margin` (use `gap`/parent `padding`).
- **Minimal example** (`row-1`):

```tsx
<row id="catalog-actions" gap={8} justify="center">
  <button id="catalog-ok" onClick={() => clicked.set(true)}>{'OK'}</button>
  <button id="catalog-cancel">{'Cancel'}</button>
</row>
```

### 5.4 `column`

- **Purpose**: vertical linear container; the default container direction for every non-`row`
  container.
- **Children**: elements laid out top→bottom.
- **Props**: shared only.
- **Layout**: same rules as `row` with axes swapped.
- **Events**: the shared nine (section 6.3).
- **Profile behavior**: shared responsive props apply.
- **Common errors**: shared set (section 5.0).
- **Minimal example** (`column-1`):

```tsx
<column id="catalog-stack-list" gap={2}>
  <label id="catalog-line-1">{'first'}</label>
  <label id="catalog-line-2">{'second'}</label>
</column>
```

### 5.5 `stack`

- **Purpose**: overlay container; children are positioned on top of each other.
- **Children**: elements, each placed independently by `anchor` (default `topLeft`) inside the
  inner box; later children paint over earlier ones.
- **Props**: shared only.
- **Layout**: intrinsic size is the largest child plus padding; children are sized by their own
  `width`/`height` or intrinsic size (no `fill` distribution). Child paint order replaces CSS
  `z-index`.
- **Common errors**: expecting percentage-anchor or z-index semantics.
- **Events**: the shared nine (section 6.3).
- **Profile behavior**: shared responsive props apply.
- **Minimal example** (`stack-1`, verified anchor placement):

```tsx
<stack id="catalog-overlay" width={100} height={50}>
  <label id="catalog-overlay-base">{'base'}</label>
  <spacer id="catalog-overlay-pin" width={20} height={10} anchor="bottomRight" />
</stack>
```

### 5.6 `scroll`

- **Purpose**: clipped scrolling viewport over content.
- **Children**: elements; laid out like a `column` (or `direction` override).
- **Props**: `scrollX?: Responsive<boolean>`, `scrollY?: Responsive<boolean>`,
  `scrollOffset?: Responsive<number> ≥ 0` (+ shared).
- **Layout**: children are offset by `scrollOffset` along the layout axis; content outside the
  scroll bounds is clipped and reported as `overflow` in the layout snapshot diagnostics
  (`<id>:overflow-top` etc.). Axis enablement is host policy; the common layout offsets by
  `scrollOffset` only.
- **Events**: `onScroll` receives `delta` (payload supplied by the host).
- **Profile behavior**: `scrollX`/`scrollY`/`scrollOffset` are responsive; shared responsive props
  apply.
- **Common errors**: negative `scrollOffset`; assuming wheel handling — the common runtime
  synthesizes no input; hosts dispatch.
- **Minimal example** (`scroll-1`, verified clipping):

```tsx
<scroll id="catalog-scroll" width={100} height={30} scrollOffset={0}>
  <column id="catalog-scroll-content" gap={2}>
    {lines.map(line => <label key={line} id={'catalog-line-' + line}>{line}</label>)}
  </column>
</scroll>
```

### 5.7 `label`

- **Purpose**: text display.
- **Children**: **text-only children** (strings/numbers) are joined into `text`; element children
  are rejected (`layout` failure "label children must be text").
- **Props**: `text?: string`, `color?: string | number`, `fontSize?: Responsive<number>` (default
  9 when unset), `wrap?: boolean`, `truncate?: boolean` (+ shared).
- **Layout**: intrinsic size comes from `adapter.measureText(text, fontSize, maxWidth)` — the common
  runtime never guesses text widths (a host without `measureText` fails layout). `wrap` is passed
  through to the host text path. `truncate: true` keeps the label a single line cut with an
  ellipsis at the available width — it implies no wrapping.
- **Visual**: NEKO-6001 for invalid colors; NEKO-6002 for non-positive `fontSize` or a non-boolean
  `truncate` (host side); the runtime itself rejects negative `fontSize` and non-boolean
  `truncate` at validation.
- **Events**: the shared nine (section 6.3).
- **Minimal example** (`label-1`):

```tsx
<label id="catalog-hello" color="#FFFF00" fontSize={{ base: 9, profiles: { 6: 12 } }}>{'Hello'}</label>
```

### 5.8 `button`

- **Purpose**: clickable control with text.
- **Children**: text-only children (joined into `text`).
- **Props**: `text?: string`, `disabled?: boolean` (runtime-enforced boolean),
  `tooltip?: string` (+ shared).
- **Events**: all shared events; `onClick`/`onRelease` receive `x`/`y`/`button` when the host
  supplies them. Dispatch requires `id`.
- **Profile behavior**: shared responsive props apply.
- **Common errors**: non-boolean `disabled`; shared set.
- **Minimal example** (`button-1`, verified dispatch round-trip):

```tsx
<button id="catalog-click" disabled={false}
        onClick={event => lastTarget.set(event.target)}>{'Click'}</button>
```

### 5.9 `input`

- **Purpose**: single-line text input.
- **Children**: none (element children are rejected; `input` is not a text-children type).
- **Props**: `value?: string` (runtime-enforced string), `placeholder?: string`,
  `maxLength?: number` (runtime-enforced non-negative integer), `disabled?: boolean` (+ shared).
- **Events**: `onTextInput`/`onChange`/`onSubmit` carry `value`/`key`; `onFocus`/`onBlur`
  fire on focus moves. The controlled pattern is: keep `value` in a signal, update it in
  `onChange`, pass it back — the runtime does not own input state.
- **Common errors**: non-string `value`; fractional or negative `maxLength`.
- **Profile behavior**: shared responsive props apply.
- **Minimal example** (`input-1`, verified controlled round-trip):

```tsx
<input id="catalog-name" value={name.get()} placeholder="name" maxLength={32}
       onChange={event => name.set(event.value ?? '')} />
```

### 5.10 `image`

- **Purpose**: texture-backed image.
- **Children**: none.
- **Props**: `resource?: string` (controlled resource id), `fit?: 'contain' | 'cover' | 'stretch'`,
  `opacity?: number` (fraction in `[0, 1]`, same validation as `panel`), `icon?: string`
  (controlled icon resource id, same grammar and resolution as `resource`),
  `crop?: { x, y, width, height } | [x, y, width, height]` (source crop rectangle in texture
  pixels; `x`/`y` ≥ 0, `width`/`height` > 0) (+ shared).
- **Resources**: the id grammar is `namespace:path` (lowercase `[a-z0-9_.-]` namespace,
  `[a-z0-9/._-]` path, no `..` segments, at most one `:`, missing namespace defaults to
  `minecraft`); `.png` is implied for textures. Resolution goes through the single resource-root
  policy (NekoJS disk pack + vanilla stack). Invalid id → NEKO-6003; unresolvable → NEKO-6004;
  illegal size (including a malformed `crop`) → NEKO-6006. NEKO-6005 (load) and NEKO-6007 (decode)
  are reserved codes, not yet emitted: the texture load/decode pipeline is not wired and hosts
  paint a placeholder box; the codes take effect once that pipeline lands. File paths and URLs
  are never accepted.
- **Events**: the shared nine (section 6.3).
- **Common errors**: the resource failures above (NEKO-6003/6004/6006); invalid `fit` value;
  malformed `crop` (NEKO-6006); shared set.
- **Minimal example** (`image-1`, fixture verifies the element mounts with the id passed through
  to the host; id grammar and resolution are host/Java-side, see section 1 sources):

```tsx
<image id="catalog-art" resource="minecraft:block/stone" fit="contain" width={16} height={16} />
```

### 5.11 `spacer`

- **Purpose**: pure layout gap/filler.
- **Children**: none.
- **Props**: shared only.
- **Layout**: sized by its own `width`/`height`; with `fill` it takes the remaining main-axis
  share like any other fill child.
- **Events**: the shared nine (section 6.3).
- **Profile behavior**: shared responsive props apply.
- **Common errors**: shared set (section 5.0).
- **Minimal example** (`spacer-1`):

```tsx
<row id="catalog-spaced" width="100%">
  <label id="catalog-left">{'L'}</label>
  <spacer id="catalog-flex-gap" width="fill" />
  <label id="catalog-right">{'R'}</label>
</row>
```

### Color grammar

`UiColor.parse` accepts exactly: `#RGB`, `#RRGGBB`, `#AARRGGBB` (case-insensitive; short form
expands per digit), the CSS basic named colors plus `transparent`, and integral numbers in the
int32/uint32 range read as ARGB bits. Everything else — `rgb()`, `hsl()`, `#RRGGBBAA` (alpha
must lead), extended named colors — is NEKO-6001. Alpha via 8-digit hex is the supported replacement
for CSS `opacity` on color-carrying props; whole-element translucency uses the controlled
`opacity` prop (see cookbook).

## 6. State and events

### 6.1 Signal

`UI.createSignal(initial)` → `{ get, set, update }`.

- `get()` inside a render tracks the root automatically; dependencies rebuild after a successful
  render, so conditional reads swap dependencies.
- `set(v)` deep-freezes the value (plain data and callbacks only — host objects and cycles throw),
  returns `false` when `Object.is`-equal, `true` when applied, `'queued'` when enqueued from off
  the owner thread (queued writes do not mutate early; they run in order on the owner thread).
- `update(fn)` applies `fn(current)`; off-thread it queues without running `fn` early.
- Batch with `UI.batch(fn)`: invalidations inside `fn` coalesce into one reconcile.

### 6.2 Store

`UI.createStore({ key: value })` → `{ get, set, update, snapshot }`: per-key signal cells, lazy
(reads of unknown keys yield `undefined`), non-empty string keys, frozen `snapshot()`. Use a store
when several controls share one state shape; use signals for independent values.

### 6.3 Event objects

The 9 event props: `onClick`, `onRelease`, `onScroll`, `onKey`, `onTextInput`, `onFocus`,
`onBlur`, `onChange`, `onSubmit`. A dispatched event is a **frozen** object:

```ts
{ type: string; target: string; x?: number; y?: number; button?: number; key?: string; value?: string; delta?: number }
```

`type` is the lowercased event name, `target` is the receiving node's `id`. Payload fields are
host-supplied; the runtime freezes exactly what the host passed. There is no DOM `Event`, no
bubbling, no `preventDefault`, no capture phases, and no other fields — authoring code that reads
`event.stopPropagation` or similar is wrong by construction. Event handler errors are isolated:
the handler's failure becomes an `event`-phase diagnostic and dispatch returns `false`; other
controls keep working.

### 6.4 Thread and lifecycle discipline

- Create roots, and perform synchronous root operations, on the owner thread. Off-thread calls
  enqueue and return `'queued'`; if the adapter rejects the queue they throw.
- Roots are bound to the creating generation. Registration on a closed generation throws
  NEKO-7001; failures while releasing roots during generation teardown are logged as NEKO-7005 and
  teardown continues. After a successful CLIENT reload, old roots stay closed — business state must
  be explicitly held or serialized by the script, never assumed to survive.
- `root.close()` is idempotent; a failed close keeps the tree for retry; a disposed root rejects
  `refresh` and ignores dispatch.

## 7. AI self-check checklist

Run before delivering any output. Each item names the failure mode and the detection method.

1. **Unknown tags** — every lowercase tag is one of the 11 primitives; anything else must be an
   uppercase function component. Detection: `UI.primitives()` (runtime), the managed declaration
   (typecheck); failure phase `layout` ("Unknown JSX primitive").
2. **Unknown CSS capability** — every visual/layout intent maps to a controlled prop or is reported
   unsupported (cookbook tables). No invented props (`Unsupported prop for <type>`).
3. **Illegal resources** — every `resource` value matches the id grammar and resolves in the disk
   pack; detection: NEKO-6003 (grammar) / NEKO-6004 (missing).
4. **Wrong thread** — no root creation or synchronous root call outside the owner thread; off-thread
   writes must tolerate `'queued'` returns and rejection throws.
5. **Per-frame render** — the render function must be driven by signal/store reads only; no timers,
   no polling loops, no work per paint frame.
6. **Keys** — list children get stable string keys; sibling keys unique (`Duplicate sibling key`);
   keys exist where reordering matters (keyed reorder retains host identity).
7. **Focus** — keyboard paths rely on `onFocus`/`onBlur`/`onKey` events; no DOM focus API.
8. **Scroll** — scrolling goes through `scroll` + `scrollOffset` + `onScroll`(`delta`);
   overflow must be visible in snapshot diagnostics, not clipped away silently.
9. **Text readability** — `fontSize` ≥ 1 at the target profiles (default 9); label text set via
   `text`/text children; no assumption of browser font metrics.
10. **Reload residue** — no module-top-level roots (NEKO-7001); no captured root handles reused
    across generations; event closures replaced per render are the runtime's job, holding them in
    globals is the script's bug.
11. **Conversion uncertainty** — every unmapped or partially mapped input item appears in the report
    as `uncertainty`/`unsupported`/`downgraded`/`needs-human`; nothing silently dropped.

## 8. Prohibitions

Hard prohibitions for authored output (induced from AGENTS.md, docs/agents/coding.md, and the
implementation discipline of tickets 42/44):

1. **No arbitrary paths or URLs** in resource props — controlled `namespace:path` ids only
   (NEKO-6003 otherwise).
2. **No Canvas/GL/GuiGraphics handles, no DOM, no React/Preact APIs, no hooks** — state is
   signal/store; drawing belongs to the host.
3. **No static UI registry extension** — the 11-primitive set is frozen; hosts opt out via
   `supportsPrimitive` (a rejected candidate is a `host-update` failure that keeps the last tree),
   scripts never register primitives.
4. **No top-level root creation or top-level side effects** — roots bind generation + owner thread
   (NEKO-7001); modules only define components/state and export them.
5. **No unguarded node-specific references** — scripts use the managed script surface only; no
   version-guarded Java class references (26.x-only or otherwise) inside UI code.
6. **No blocking I/O or unbounded work in render/event callbacks** (AGENTS.md hot-path rules).
7. **No generated golden mutation** — fixtures and goldens are read-only inputs to checks.

## 9. Inspector correction protocol

**Basis: the ticket 45 Inspector contract** ([ui-inspector-contract.md](ui-inspector-contract.md),
frozen and implemented). The read port is `UiInspector.inspect()` → `InspectorSnapshot` in
`com.tkisor.nekojs.api.ui`; the fake host and the NeoForge host collect through the same
`InspectorSnapshots` path, so their records are isomorphic by construction. Script-side,
`root.layout()` returns the same frozen layout snapshot the Inspector reads from. A shape
mismatch between runtime and collector fails with NEKO-8001 and keeps the last good frame.

The record shapes the protocol operates on (all frozen data — a snapshot never holds live runtime
references):

- `InspectorSnapshot(rootId, source, viewport, nodes, diagnostics, errors, screenshot)` —
  `errors` are `PhaseError(phase, rootId, message)` with the five runtime phases,
  `diagnostics` are layout strings like `my-node:overflow-right`, and `screenshot` is
  metadata-only (`source`/`width`/`height`; no pixels or buffers cross the contract).
- `InspectorNode(id, type, key, visible, focused, rect, clip, overflow, scrollOffset, style,
  bindings, resources, children)` — `style` is the **final resolved props** (responsive values
  already flattened for the current profile), `bindings` the sorted event names bound on the node
  id (never callbacks), `resources` the ticket 44 `ResourceStatus` entries for the node's
  `resource`/`icon` ids, `overflow` the per-edge spill.
- `InspectorViewport(width, height, safeArea, contentWidth, contentHeight, profile, guiScale,
  designScale)`.
- `SnapshotDiffer.diff(reference, actual, referenceImage)` → `SnapshotDiff`: entries ranked by
  deviation (absolute for numbers, 1 for discrete changes, `Double.MAX_VALUE` for structural
  breaks — a missing or added node outranks any measurement drift), so `largest()` names the
  node/property that drifted most. Provenance is retained on the report (both snapshot sources,
  both profiles and viewports, the reference image id, the actual capture metadata). Screenshots
  and the reference image never produce entries — only public measurements and behavior facts are
  compared.

The correction loop for a screen that measures wrong:

1. **Read the snapshot** — `inspect()` (host tooling) or `root.layout()` (script side); check
   `errors` first: a retained `PhaseError` names the failing phase before any measurement is
   worth trusting.
2. **Locate** — address the offending node by `id` (focus, bindings, and resource statuses are
   all keyed by node id), cross-check `type`/`key` for reconciled list items, and read
   `viewport.profile` to know which responsive branch produced the measurement.
3. **Diagnose** — compare `rect` against expectation, `clip`/`overflow`/`scrollOffset` for
   spill and scroll, `style` for the resolved values actually applied (a wrong responsive branch
   shows here immediately), `focused` for focus paths, `bindings` to confirm event wiring,
   `resources` for resolution failures (NEKO-6004 and siblings).
4. **Diff when a reference exists** — `SnapshotDiffer.diff(reference, actual, referenceImage)`;
   start the repair at `largest()`, and treat structural entries (node missing/added) as
   higher-priority than measurement drift.
5. **Minimal repair** — edit the smallest JSX unit: one prop, one responsive branch, one node.
   **Rewriting the whole tree per deviation is prohibited**: it destroys keys and host identity and
   makes regression unreviewable.
6. **Re-measure** — resize/dispatch to re-run layout, re-read the snapshot, confirm the single
   changed node (`InspectorSnapshots.canonical()` renders the deterministic text form used for
   old/new comparison and golden files). Repeat per deviation.
7. **Stop condition** — structural, interaction, and reactive equivalence achieved; remaining
   deltas are visual-only and documented as such (font rasterization / GUI scale differences are
   expected residual, not defects).

## 10. Unsupported report format

Machine-readable JSON, one per conversion. Everything not representable in the controlled surface
**must** appear here — silent dropping or faked success is the primary contract violation.

```json
{
  "reportVersion": 1,
  "conversion": {
    "source": "login-form.html",
    "output": "login-form.output.tsx",
    "tool": "AI-assisted (human-reviewed)"
  },
  "inputChecklist": {
    "structure": "complete",
    "styles": "complete",
    "resources": "missing",
    "fonts": "missing",
    "interactions": "complete",
    "profiles": "partial",
    "notes": ["no reference screenshots for profiles 1-2"]
  },
  "equivalence": {
    "structural": "achieved",
    "interaction": "achieved",
    "reactive": "achieved",
    "visual": "downgraded"
  },
  "verification": {
    "fixture": "common/src/test/resources/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx",
    "executor": "common/src/test/java/com/tkisor/nekojs/core/module/TypeScriptUiAuthoringDocsTest.java",
    "method": "fake-host mount + event dispatch + viewport resize; assertions read the layout snapshot (the script-side Inspector record)",
    "profilesExercised": [2, 6],
    "assertions": ["mount + controlled input round-trip", "signal-driven visibility", "profile-dependent fontSize resolution"],
    "result": "pass"
  },
  "items": [
    {
      "id": "U-1",
      "severity": "unsupported",
      "web": "-apple-system font stack",
      "reason": "no script-facing font-family prop; fonts resolve by controlled id on the host side",
      "replacement": "default host font",
      "location": { "source": "login-form.html:18", "node": "login-title" }
    },
    {
      "id": "U-2",
      "severity": "uncertainty",
      "checklistKey": "fonts",
      "web": "no font metrics provided with the input",
      "reason": "input gap: label sizes cannot be derived from the source",
      "replacement": "approximate from the runtime default and verify with the Inspector",
      "location": { "source": "login-form.html:18", "node": "login-title" }
    }
  ]
}
```

`severity` ∈ `unsupported` (no controlled equivalent exists), `downgraded` (mapped to a weaker
controlled feature), `uncertainty` (input missing or ambiguous), `needs-human` (decision cannot
be automated). `equivalence` values ∈ `achieved` / `partial` / `downgraded`.

**Ticket 46 contract rules (reportVersion frozen at 1 by ticket 46):**

- The `inputChecklist` object is governed by the input contract
  ([cookbook §1](../../ui-conversion/web-to-jsx-cookbook.md#1-input-contract-ticket-46)): keys
  `structure`, `styles`, `resources`, `fonts`, `interactions`, `profiles`, each carrying
  `complete` / `partial` / `missing` / `not-applicable`, with notes required for every `missing`
  or `partial` item and a reason note for every `not-applicable` item.
- Items may carry an optional `checklistKey` naming the `inputChecklist` key whose gap produced
  them. Every `missing` or `partial` checklist key must be traced by at least one `uncertainty`
  or `needs-human` item through `checklistKey` — absent input is itself an uncertainty to record,
  never a silent default.
- The required `verification` object records what actually verified the output: `fixture` and
  `executor` name the executing proof, `method` states the verification seam, `profilesExercised`
  lists the viewport profiles the run covered (subset of 1–6, at least one), `assertions`
  summarizes what was asserted (phrased as Inspector-record facts), and `result` is `pass` for
  published fixtures.
- Any later change to these shapes bumps `reportVersion` and is reviewed like a golden change.
- The shipped fixture reports are validated against these rules by
  `common/src/test/java/com/tkisor/nekojs/core/module/WebConversionReportContractTest.java`.

## 11. Example verification map

| Example set | Fixture | Executor |
|---|---|---|
| Every primitive minimal example in section 5 (`screen-1` … `spacer-1`), shared prop semantics, signal/store/event behavior, thread queueing | `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx` | `common/src/test/java/com/tkisor/nekojs/core/module/TypeScriptUiAuthoringDocsTest.java` |
| Web conversion outputs (`docs/ui-conversion/fixtures/*.output.tsx`) — mounted, interacted, resized across profiles | same fixture (imports the docs outputs from the sandbox copies written by the same test) | same test |
| Conversion report contract (ticket 46) — input-checklist linkage, severity coverage, verification record on the shipped fixture reports | `docs/ui-conversion/fixtures/*.conversion-report.json` (read-only) | `common/src/test/java/com/tkisor/nekojs/core/module/WebConversionReportContractTest.java` |
| Declaration-level prop typing | `common/src/test/probe-ts/jsx-primitive-props.tsx` | `npm run test:probe-types` (probe golden gate) |

The proof fixture asserts check ids named in section 5 plus the conversion outputs' proof objects,
so a doc example that stops running fails CI.

## 12. Pending backfill

- ~~Ticket 45~~ — **backfilled**: section 9 now cites the frozen Inspector contract
  (`UiInspector`/`InspectorSnapshot`/`InspectorNode`/`SnapshotDiffer.diff`/
  `InspectorSnapshots.canonical()`, NEKO-8001) verified against the landed Java records.
- ~~Ticket 46~~ — **backfilled (2026-09-25)**: the `[46]` marks are resolved. Section 3 defers to
  the normative input contract (cookbook §1); section 10 gained the `verification` block and froze
  `reportVersion` at 1; the cookbook's mapping and downgrade tables were reviewed and extended
  (pseudo-elements, selector cascade, scripted DOM rows); and the shipped fixture reports now record
  the mapping decisions they previously omitted (scripted card generation, `window.alert` dialogs)
  and escalate the media-query re-chunking to `needs-human`.
