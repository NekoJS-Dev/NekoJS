# Web to NekoJS JSX Conversion Cookbook

**Status:** ticket 46 deliverable (conversion mapping contract; landed alongside the ticket-47 authoring docs)
**Contract:** [AI UI Authoring Contract](../architecture-refactor/ai-authoring-contract.md) — read it first; it defines the output structure, self-check, and the unsupported report format used here. The input contract in §1 below is the normative ticket-46 definition.

This cookbook maps controlled web input (HTML structure + CSS intent + interaction spec) onto the
NekoJS JSX primitive surface. It is written for AI-assisted conversion with human review: it is a
mapping table plus explicit downgrade rules, **not** a compiler and not a promise of browser
compatibility. Everything without a controlled equivalent must land in the conversion report as
`unsupported` / `downgraded` / `uncertainty` / `needs-human`.

Ticket 46 (AI-assisted 网页转换映射与输入契约) owns this mapping contract: §1 is the normative input
contract, the tables below are reviewed against the runtime facts cited in the authoring contract,
and the shipped fixture reports are machine-checked against the report contract by
`common/src/test/java/com/tkisor/nekojs/core/module/WebConversionReportContractTest.java`.

## 1. Input contract (ticket 46)

The converter (AI or human) must hold all seven input items of the authoring contract's
[input checklist](../architecture-refactor/ai-authoring-contract.md#3-input-checklist-what-the-ai-must-hold-before-authoring)
before mapping begins: structure, styles, resources, fonts/text intent, interaction spec, responsive
intent, and runtime constraints. Missing or partial input is never invented: it surfaces as explicit
`uncertainty` / `needs-human` entries in the conversion report.

The first six items map one-to-one to the machine-readable `inputChecklist` object of the
[conversion report](../architecture-refactor/ai-authoring-contract.md#10-unsupported-report-format);
runtime constraints are recorded in `inputChecklist.notes`. Each checklist key carries one of
`complete`, `partial`, `missing`, `not-applicable`, under these rules:

- Every `missing` or `partial` item must have at least one `inputChecklist.notes` entry naming
  the gap and at least one `uncertainty` / `needs-human` report item tracing the key through
  `checklistKey` (absent input is itself an uncertainty to record, never a silent default).
- `not-applicable` requires a reason note (for example: a form without images).
- **Responsive intent requires per-profile reference sizes or screenshots.** When they are absent,
  record the `uncertainty` and bound responsive output to explicit `{ base, profiles }` prop values
  that the verification step measures, rather than assumed visual equality.

The shipped fixture reports (`fixtures/*.conversion-report.json`) demonstrate the contract and are
validated by `WebConversionReportContractTest` (see §9).

## 2. Structure mapping

| Web construct | NekoJS JSX | Notes |
|---|---|---|
| `div` (block flow) | `column` | Default container direction is column |
| flex row (`display:flex; flex-direction:row`) | `row` | `gap` maps directly |
| flex column | `column` | |
| absolutely-positioned overlay / stacking context | `stack` + child `anchor` | Paint order = child order (replaces z-index) |
| scrollable container (`overflow:auto/scroll`) | `scroll` (+ `scrollY`/`scrollX` policy, `scrollOffset`) | Common layout offsets along the layout axis; input synthesis is host-owned |
| text node / `span` / `p` | `label` | Text-only children; string content |
| `button` | `button` | Disabled/loading states become explicit signal state |
| `input type=text/password` | `input` | Controlled pattern: signal + `onChange`; `maxLength` maps; no `type` prop (single-line text only) |
| `img` | `image` | `src` must become a controlled `namespace:path` id (see §5) |
| whitespace filler / flexible gap | `spacer` (`width="fill"` / `height="fill"`) | |
| form submit | `onSubmit` on the submitting control | No form element; wire events explicitly |
| CSS Grid | **unsupported → needs-human** | Downgrade candidate: rows of `row`s; record as `downgraded` or escalate |
| `table` | `column` of `row`s | Downgrade; record |
| `iframe`, `video`, `canvas`, `WebGL` | **unsupported** | No controlled equivalent; must surface in report |
| custom elements / framework components | map to function components | Only if their rendered intent is mappable; framework lifecycle (component state models, effects, hooks) and unmappable intent → `needs-human` |
| component rendered from a list | `{list.map(item => UI.element(Card, { ... }, item.id))}` | **Current lowering does not accept an uppercase component tag inside a `{...}` expression child** ("Missing JSX element name"); `UI.element` is the supported form (see the contract's lowering constraints) |
| runtime DOM scripting (`innerHTML`, `appendChild`, template-string generation) | data-driven function component over a store | The DOM manipulation itself has no equivalent; extract the data + intent. Effects beyond renderable data (timers, focus stealing, third-party lifecycle) → `needs-human` |

## 3. Layout mapping

| CSS | NekoJS prop | Notes |
|---|---|---|
| `width`/`height` px | `width`/`height` number | Logical pixels; browser px ≠ MC logical px at the same profile — verify with Inspector, do not trust 1:1 |
| `width`/`height` % | `'N%'` | Percent of available space |
| `flex-grow` / `flex: 1` | main-axis size `'fill'` | Fill children split the remaining space equally |
| `flex-basis`/`auto` | `'auto'` (default) | Intrinsic size |
| `min-`/`max-width/height` | `minWidth`…`maxHeight` | min > max is a layout failure |
| `gap` / `margin` between children | `gap` (or `spacing`; one of the two) | Outer margin → wrapper `padding` or a `spacer` |
| `padding` | `padding` | Uniform number only — per-side padding has no prop; downgrade asymmetric padding to wrapper nodes |
| `align-items` | `align` | Cross axis; per-child override allowed |
| `justify-content` | `justify` | `space-between`→`spaceBetween`, `space-around`→`spaceAround` |
| `flex-direction` override on a container | `direction` | Allowed on any container |
| `display:none` / `visibility:hidden` | `visible={false}` | Collapses to 0×0 (not just invisible) |
| `position:absolute` (top/right/bottom/left) | parent `stack` + `anchor` | 9 anchor points only; arbitrary insets need `spacer`s or padding — usually `downgraded` |
| `overflow:hidden` | parent clip (implicit) | Every node clips to its parent bounds; spill shows as `overflow` diagnostics |
| `@media` queries | responsive `{ base, profiles: {1..6} }` | Profiles are the only breakpoint system |
| `box-sizing` | — | Padding is inside sizes uniformly; no border-box switch |
| `float` | **unsupported** | Re-flow as row/column |
| `position:fixed/sticky` | **unsupported → needs-human** | Approximate with top-level stack placement |
| `transform`, `translate` | **unsupported** | |
| `z-index` | stack child order | |

## 4. Visual mapping

| CSS | NekoJS prop | Notes |
|---|---|---|
| `color` | `label` `color` | Controlled grammar only: `#RGB`, `#RRGGBB`, `#AARRGGBB`, CSS basic names, ARGB ints (NEKO-6001 otherwise) |
| `background-color` | `panel` `background` | Same grammar |
| `border`/`border-color` | `panel` `borderColor` | Uniform only |
| `border-width` | `panel` `borderWidth` | Non-negative integer (NEKO-6002) |
| `border-radius` | `panel` `radius` | Uniform corner radius |
| `opacity` | `panel`/`image` `opacity` | Controlled fraction in `[0, 1]` (NEKO-6002 otherwise); `#AARRGGBB` color alpha remains the tool for per-color translucency |
| `font-size` | `label` `fontSize` | Logical px, default 9; profile-responsive allowed |
| `font-weight`/`font-style` | **unsupported** | No synthetic bold/italic; needs-human for emphasis decisions |
| `font-family` | **unsupported** | No script-facing prop; host resolves fonts by controlled id |
| `text-align` | wrapper `align`/`justify` | No text-align prop on `label`; align the label node in its container instead |
| `line-height`, `letter-spacing` | **unsupported** | Host text metrics own this |
| `text-overflow`/`white-space` | `wrap`/`truncate` boolean | `truncate` = single line cut with an ellipsis (implies no wrapping); record `downgraded` where it matters |
| `box-shadow`, gradients, filters | **unsupported** | |
| pseudo-elements (`::before`, `::after`, `::placeholder`) | **unsupported** | No generated-content model; fold decorative text into real `label` children or report the loss |
| complex selectors / cascade / inheritance (descendant, sibling, specificity, inherited `em`) | **unsupported → needs-human** | Only flat per-element declarations map; converter-resolved styles must be recorded, ambiguous inheritance becomes `uncertainty` |
| animations / transitions | **unsupported** | No CSS animation model; state changes are instant signal updates |
| `cursor`, `user-select`, `pointer-events` | — | Out of model |

## 5. Resource mapping

- Web `src`/`url(...)` must be re-hosted as a controlled resource: copy the asset into the NekoJS
  disk pack and reference it by `namespace:path` (`.png` implied). A URL that cannot be re-hosted
  is `unsupported` — **never** emitted as a `resource` value (NEKO-6003).
- Grammars: namespace `[a-z0-9_.-]+`, path `[a-z0-9/._-]+`, no empty/`.`/`..` segments, at most
  one `:`, default namespace `minecraft`.
- Failure modes to expect and report: NEKO-6004 (not in any root), NEKO-6006 (size). NEKO-6005
  (load) and NEKO-6007 (decode) are reserved codes, not yet emitted — the texture load/decode
  pipeline is not wired and unresolved images draw as placeholder boxes; report that downgrade
  until the codes go live.
- Icon fonts / SVG sprites: `unsupported` (rasterize + re-host, or drop with a report entry).

## 6. Interaction and state mapping

| Web behavior | NekoJS mapping |
|---|---|
| `onclick` | `onClick` (event has `target`, host-supplied `x`/`y`/`button`) |
| `oninput` | `onChange`/`onTextInput` with `value`/`key` |
| `onsubmit` (Enter in a form) | `onSubmit` on the input/control |
| `onfocus`/`onblur` | `onFocus`/`onBlur` |
| wheel scrolling | `onScroll` with `delta`; host synthesizes input |
| hover (`:hover`) | **no hover events** — convert to explicit state (e.g. selected signal toggled by `onClick`), or report `downgraded` |
| `:focus`/`:active` styling | explicit signal-driven styles |
| `disabled` attribute | `disabled` prop (button/input) |
| loading state | `submitting`-style signal disabling the controls |
| event bubbling / delegation | **does not exist** — attach handlers per node by `id` |
| `preventDefault` / DOM defaults | **does not exist** — controlled inputs: the signal is the source of truth |

## 7. Responsive mapping

- Map breakpoints to profiles with responsive values, e.g. mobile-first CSS becomes
  `{ base: <narrow>, profiles: { 6: <wide> } }`; resolution picks the highest key ≤ the current
  profile (P1 anything below 320×180, P2 320×180 … P6 1280×720, content-area based, capped by
  `capabilities.maxProfile`).
- `vw`/`vh` units → percentages of available space in the container (`'N%'`).
- `min()/max()/clamp()` → `min* `/`max*` props (no expressions).
- Reference screenshots per profile are input, not goals: font rasterization and GUI scale make
  pixel equality meaningless; target structural > interaction > reactive > visual equivalence.

## 8. Conversion loop

1. Run the input checklist; record `uncertainty` for gaps.
2. Map structure (§2) → layout (§3) → visuals (§4) → resources (§5) → interactions/state (§6) →
   responsive (§7). Every unmappable item accumulates a report entry as you go.
3. Author the JSX module (components + signal/store; no top-level roots).
4. Run the authoring self-check checklist (contract §7), including the lowering constraints
   (contract §2): `UI.element` for components inside expression children, no `//` comments
   inside JSX bodies.
5. Execute the verification fixture; fix minimally per the Inspector protocol (contract §9).
6. Emit the conversion report (contract §10) and hand to human review.

## 9. Fixtures

| File | Role |
|---|---|
| `fixtures/login-form.html` | Representative input: labeled text/password inputs, validation, submit/cancel, loading state |
| `fixtures/login-form.output.tsx` | Controlled conversion output (executed by the proof fixture) |
| `fixtures/login-form.conversion-report.json` | Conversion report for the above |
| `fixtures/card-grid.html` | Representative input: card grid, images, prices, hover intent, scrolling |
| `fixtures/card-grid.output.tsx` | Controlled conversion output (executed by the proof fixture) |
| `fixtures/card-grid.conversion-report.json` | Conversion report for the above |

Both outputs are executed end-to-end (mount, interact, resize across viewport profiles) by
`TypeScriptUiAuthoringDocsTest` via `ui-authoring-docs-proof.tsx`, and both conversion reports are
machine-checked against the report contract (input-checklist linkage, severity coverage,
verification record) by `WebConversionReportContractTest` — see the contract's
[verification map](../architecture-refactor/ai-authoring-contract.md#11-example-verification-map).
