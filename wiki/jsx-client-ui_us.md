<!-- wiki-page: jsx-client-ui; locale: us -->

> **English** · [中文](jsx-client-ui_cn)

<a id="wiki-section-1"></a>
# JSX client UI

NekoJS provides a UI runtime for describing Minecraft client Screens with JSX. It is neither a browser DOM nor a React compatibility layer. It converts JSX component trees into controlled Minecraft UI nodes and manages layout, input, state updates, and lifecycle.

> **Current scope**: client Screen UI is currently available only on **NeoForge 26.x**. It can only be created in `client_scripts/`; UI support is not currently guaranteed for Fabric, NeoForge 1.21.1, or Cleanroom. See [Platforms and compatibility](platform-compatibility_us).

<a id="wiki-section-2"></a>
## Getting started

<a id="wiki-section-3"></a>
### 1. Enable the automatic JSX runtime

Set the following in `nekojs/config/engine.toml`:

```toml
jsxAutomaticRuntime = true
```

NekoJS uses the built-in `nekojs/jsx-runtime` and updates the script directories' `jsconfig.json` to the automatic JSX configuration during workspace/probe processing. You do not need to create a runtime file with the same name in `node_modules/`.

**Complete forms, tabs, dialog and read-only item example**

Save the following complete script as `nekojs/client_scripts/authoring-examples/forms-and-dialogs.tsx` and run `/nekojs reload client` on a supported NeoForge 26.x client. Remove other example scripts that open a Screen before this exercise; they otherwise compete for the visible Screen. This is the exact bundled `examples/client/forms-and-dialogs.tsx`. Signals live outside render, including tab/form values and the confirmed snapshot.

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI, Tabs, Dialog, validateForm, Validators } from "nekojs/jsx-runtime";

const tab = UI.createSignal("profile");
const name = UI.createSignal("");
const quantity = UI.createSignal("1");
const errors = UI.createSignal("");
const dialogOpen = UI.createSignal(false);
const reviewed = UI.createSignal({ name: "", quantity: 1 });
const status = UI.createSignal("Nothing confirmed");
const backgroundClicks = UI.createSignal(0);

function review() {
    const values = { name: name.get(), quantity: quantity.get() };
    const result = validateForm(values, {
        name: [Validators.required("Enter a name"), Validators.maxLength(24, "Use at most 24 characters")],
        quantity: [
            Validators.required("Enter a quantity"),
            Validators.numberRange(1, 64, "Quantity must be between 1 and 64"),
            value => Number.isInteger(Number(value)) ? null : "Quantity must be a whole number"
        ]
    });
    errors.set(Object.keys(result.errors).map(field => result.errors[field].join("; ")).join(" | "));
    if (!result.valid) return;
    reviewed.set({ name: values.name.trim(), quantity: Number(values.quantity) });
    dialogOpen.set(true);
}

const tabs = [
    {
        value: "profile", label: "Profile", render: () => (
            <column gap={6} width="fill">
                <label>Name and requested quantity (local preview only)</label>
                <input id="forms-name" width="fill" height={20} value={name.get()} maxLength={24}
                    placeholder="Name" onChange={event => name.set(event.value || "")} />
                <input id="forms-quantity" width="fill" height={20} value={quantity.get()} maxLength={8}
                    placeholder="Whole number 1..64" onChange={event => quantity.set(event.value || "")} />
                <label id="forms-errors">{errors.get()}</label>
                <button id="forms-review" height={20} onClick={review}>Review</button>
            </column>
        )
    },
    {
        value: "preview", label: "Preview", render: () => (
            <column gap={6}>
                <item id="forms-preview-item" item="minecraft:diamond" count={1} itemTooltip={true}
                    width={32} height={32} />
                <label>Read-only icon and tooltip; no inventory is changed.</label>
            </column>
        )
    },
    { value: "locked", label: "Locked", disabled: true, render: () => <label>Not selectable</label> }
];

const host = ClientUI.screen("Authoring forms and dialogs", false);
const root = UI.createRoot(() => (
    <screen padding={12}>
        <scroll id="forms-scroll" width="fill" height="fill">
            <column gap={8} width="fill">
                <Tabs id="forms-tabs" width="fill" height={190} value={tab.get()} tabs={tabs}
                    onChange={value => tab.set(value)} />
                <button id="forms-background" height={20}
                    onClick={() => backgroundClicks.update(value => value + 1)}>Background action</button>
                <label id="forms-background-count">{"Background clicks: " + backgroundClicks.get()}</label>
                <label id="forms-status">{status.get()}</label>
            </column>
        </scroll>
        <Dialog id="forms-dialog" open={dialogOpen.get()} title="Confirm local profile"
            onOpenChange={value => dialogOpen.set(value)}>
            <scroll id="forms-dialog-scroll" width="fill" height="fill">
                <column gap={8} width="fill">
                    <label>{reviewed.get().name + " x" + reviewed.get().quantity}</label>
                    <label>Confirmation changes only this screen, not a world or inventory.</label>
                    <button id="forms-cancel" height={20} onClick={() => dialogOpen.set(false)}>Cancel</button>
                    <button id="forms-confirm" height={20} onClick={() => {
                        status.set("Confirmed " + reviewed.get().name + " x" + reviewed.get().quantity);
                        dialogOpen.set(false);
                    }}>Confirm</button>
                </column>
            </scroll>
        </Dialog>
    </screen>
), host);
host.bindRoot(root);
host.open();
```

**Run and observe:** initially Profile is selected, name is blank, quantity is `1`, errors are empty, status is `Nothing confirmed`, and the background counter is zero. Review with a blank name: validation shows errors and does not open a dialog. Try quantity `0`, `65`, text or `1.5`: range/integer validation rejects it. Enter `Alex` and `3`, click Review, then Cancel or dismiss with Escape/backdrop: status stays unchanged. Review again and Confirm: status becomes `Confirmed Alex x3`; no world, inventory or network state changes. Preview shows a read-only diamond and tooltip; Locked cannot be selected. Returning to Profile retains the external signal values. Closing/removing the script and reloading CLIENT disposes the root and callbacks; reloading resets this example, not persistent storage.

**Pending native acceptance checklist — not run**

Before treating this UI as accepted, record the exact node, loader, GUI scale, viewport, actual outcomes and a maintainer conclusion for every row. In-memory native/common-engine fixtures are not live-client evidence.

| Check | Expected observation | Status |
|---|---|---|
| Invalid/valid form | Invalid values do not open a dialog; Alex/3 can cancel or confirm the local snapshot | Not run |
| Tabs | Pointer and arrows/Home/End skip Locked; selected pane alone is mounted and values survive switching | Not run |
| Modal boundary | Background action/scroll, keyboard and manual background dispatch stay blocked; any older pointer/slider capture is cancelled | Not run |
| Focus/dismissal | Tab/Shift+Tab stay in the top modal, Escape/backdrop follow flags without closing the Screen; eligible prior focus is restored | Not run |
| Read-only item | Real item/model/count and vanilla or overriding custom tooltip appear with no inventory mutation | Not run |
| Viewport/lifecycle | Short windows, scale changes and nested scrolling preserve the portal and reachable content; close/reload/invalid-candidate retention leave no stale callbacks/modal state | Not run |

Maintainer conclusion: **pending**. Use the earlier controls example for real slider/OS clipboard checks; the forms example has no slider. Actual GPU visuals, native focus/narration, tooltip behavior, IME and multiplayer acceptance remain unverified.

<a id="wiki-section-4"></a>
### 2. Create a Screen

The following script creates a Screen with a counter button:

```tsx
// client_scripts/counter.tsx
import { UI } from 'nekojs/jsx-runtime'

const adapter = ClientUI.screen('计数器', false)
const count = UI.createSignal(0)

const root = UI.createRoot(() => (
  <screen title="计数器" width="fill" height="fill" padding={12}>
    <column width="fill" height="fill" gap={8} align="center" justify="center">
      <label>{`当前值：${count.get()}`}</label>
      <button
        id="increment"
        text="增加"
        onClick={() => count.update(value => value + 1)}
      />
    </column>
  </screen>
), adapter, { id: 'counter' })

adapter.bindRoot(root)
adapter.open()
```

Key steps:

1. `ClientUI.screen(title, pausesGame)` creates a Minecraft Screen host adapter.
2. `UI.createSignal` creates reactive state.
3. `UI.createRoot(render, adapter)` creates a UI root and performs the initial render.
4. `adapter.bindRoot(root)` binds the root to the Screen host.
5. `adapter.open()` opens the Screen on the client thread.

UI roots must be created from a managed CLIENT script context, and all creation, state updates, and closing operations should run on the client owner thread.

**Complete controls example: input, toggle, slider and inline select**

Save this complete script as `nekojs/client_scripts/authoring-examples/controls.tsx`, then run `/nekojs reload client` only on a supported NeoForge 26.x client. Create signals outside the render callback so reconciliation does not reset state. The code matches `examples/client/controls.tsx` in the bundled AI documentation.

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI, Toggle, Slider, Select } from "nekojs/jsx-runtime";

const text = UI.createSignal("Paste or type a long single-line message here.");
const enabled = UI.createSignal(true);
const volume = UI.createSignal(40);
const quality = UI.createSignal("balanced");
const expanded = UI.createSignal(false);
const options = [
    { value: "balanced", label: "Balanced" },
    { value: "fast", label: "Fast" },
    { value: "unavailable", label: "Unavailable", disabled: true }
];
const appearance = {
    background: "#20252B",
    borderColor: "#708090",
    color: "#FFFFFF",
    placeholderColor: "#A0A0A0",
    selectionColor: "#804488CC",
    cursorColor: "#FFFFFF",
    trackColor: "#4C566A",
    thumbColor: "#88C0D0",
    hover: { background: "#303B46" },
    focused: { borderColor: "#88C0D0" },
    pressed: { background: "#405466" },
    disabled: { color: "#777777", background: "#181C20" }
};

const host = ClientUI.screen("Authoring controls", false);
const root = UI.createRoot(() => (
    <screen padding={12}>
        <scroll id="controls-scroll" width="fill" height="fill">
            <column gap={8} width={280}>
                <label>Clipboard and controlled components</label>
                <input id="controls-text" width="fill" height={20}
                    value={text.get()} maxLength={160} appearance={appearance}
                    onChange={event => text.set(event.value || "")} />
                <Toggle id="controls-enabled" width="fill" height={20}
                    checked={enabled.get()} label="Enable volume"
                    appearance={appearance} onChange={value => enabled.set(value)} />
                <Slider id="controls-volume" width="fill" height={20}
                    value={volume.get()} min={0} max={100} step={5}
                    disabled={!enabled.get()} appearance={appearance}
                    onChange={value => volume.set(value)} />
                <Select id="controls-quality" width="fill"
                    value={quality.get()} options={options} expanded={expanded.get()}
                    onExpandedChange={value => expanded.set(value)}
                    appearance={appearance} onChange={value => quality.set(value)} />
                <label id="controls-summary">{
                    "enabled=" + String(enabled.get()) + "; volume=" + String(volume.get())
                    + "; quality=" + quality.get()
                }</label>
                <label>Close this screen, then reload CLIENT to reset the example.</label>
            </column>
        </scroll>
    </screen>
), host);
host.bindRoot(root);
host.open();
```

**Observe and clean up:** the initial summary is `enabled=true; volume=40; quality=balanced`. Enable volume disables/re-enables the slider; drag it or focus it and use arrows/Home/End. Choose Fast in the selector and check `quality=fast`; Unavailable cannot be selected. The input supports long single-line text, selection and clipboard operations with horizontal caret following. Close the Screen, remove the script and reload CLIENT; reloading resets signals and disposes retired-generation callbacks. Engine/platform-fixture tests do not replace real-client rendering, clipboard and focus acceptance.

<a id="wiki-section-5"></a>
## First, this is not HTML/CSS

JSX is only syntax. Lowercase tags such as `<panel>`, `<row>`, and `<button>` are not browser tags: they are host primitives registered in NekoJS's fixed set. The runtime converts them into immutable VNodes, and the host adapter handles layout, drawing, and input.

The following constructs therefore have no corresponding capability:

- Browser tags such as `<div>`, `<span>`, and `<form>`;
- `className`, `style={{ ... }}`, CSS selectors, and the CSS cascade;
- Unregistered properties such as `margin`, `box-shadow`, `transform`, `z-index`, `font-weight`, and `font-family`;
- Using arbitrary Java classes, Minecraft Widgets, or `GuiGraphics` directly as JSX tags.

This does not mean that a UI must be a collection of static tags. The supported way to extend it is to compose interfaces from function components, explicit state, VNode factories, and controlled props.

<a id="wiki-section-6"></a>
## Four ways to express a UI

<a id="wiki-section-7"></a>
### 1. JSX primitives

Use primitives to describe a stable interface structure directly:

```tsx
<panel background="#20252B" borderColor="#708090" borderWidth={1} radius={4} padding={8}>
  <column gap={6}>
    <label color="white" fontSize={10}>设置</label>
    <button text="保存" />
  </column>
</panel>
```

Lowercase tags must come from these 16 primitives: `screen`, `panel`, `row`, `column`, `stack`, `scroll`, `label`, `button`, `input`, `slider`, `image`, `item`, `dialog`, `spacer`, `workspace`, and `window`. Use `UI.primitives()` to query the current runtime's registered set.

<a id="wiki-section-8"></a>
### 2. Function components

Split complex interfaces into ordinary JavaScript/TypeScript function components. Function components return VNodes and can encapsulate layout, visual tokens, state, and events:

```tsx
const CARD_STYLE = {
  background: '#20252B',
  borderColor: '#4C566A',
  borderWidth: 1,
  radius: 4,
  padding: 8
}

function Card({ title, children }) {
  return (
    <panel {...CARD_STYLE} gap={4}>
      <label color="#E5E9F0" fontSize={10}>{title}</label>
      {children}
    </panel>
  )
}
```

Here, `CARD_STYLE` is an ordinary JS object spread, not a CSS `style` property. Reuse shared styles through prop tokens or function parameters; do not expect CSS classes, selectors, or inheritance.

<a id="wiki-section-9"></a>
### 3. Create VNodes directly with `UI.element`

Use `UI.element(type, props, key)` when you need to choose tags dynamically, construct data-driven lists, or create function components inside expressions:

```tsx
function ItemCard({ item }) {
  return <Card title={item.name}><label>{item.description}</label></Card>
}

const body = items.map(item =>
  UI.element(ItemCard, { item }, item.id)
)

const root = UI.createRoot(() => (
  <scroll id="items" width="fill" height="fill">
    <column>{body}</column>
  </scroll>
), adapter)
```

There is a current lowering limitation to remember: uppercase component tags inside expressions, such as `{items.map(item => <ItemCard ... />)}`, do not currently compile. Use `UI.element(ItemCard, props, key)` in list expressions; lowercase primitives do not have this limitation inside expressions.

`UI.element` can also create primitives:

```javascript
const button = UI.element('button', {
  id: 'apply',
  text: '应用',
  disabled: false,
  onClick: event => console.log(event.target)
}, 'apply-button')
```

`type` can only be a registered primitive, a function component, or `Fragment`. VNode props are frozen; they cannot contain cyclic objects, native Minecraft objects, or arbitrary host handles.

<a id="wiki-section-10"></a>
### 4. Plain data trees and the classic runtime

If you only need to generate tree-shaped data rather than open a Minecraft Screen, you can continue using ordinary JSX/VNodes or a custom factory with the classic runtime. A classic runtime factory determines the data it returns, but it does not automatically gain `ClientUI` layout, input, or Screen lifecycle management.

In other words:

- **For an interactive Screen**: use `UI.createRoot` + `ClientUI.screen`.
- **For arbitrary script data**: use ordinary JSX or a classic factory.
- **For manually drawing rectangles, text, and textures each frame**: use the `PainterJS` approach described later.

<a id="wiki-section-11"></a>
## Built-in elements

Lowercase JSX tags can only use host primitives registered by NekoJS. Arbitrary Java classes, native Minecraft Widgets, and `Screen` subclasses do not automatically become JSX elements.

| Element | Purpose | Common props |
|---|---|---|
| `screen` | Screen root node | `title`, `pausesGame`, `closeOnEscape` |
| `panel` | Background and border container | `background`, `borderColor`, `borderWidth`, `radius`, `opacity` |
| `row` | Horizontal arrangement | Shared layout props |
| `column` | Vertical arrangement | Shared layout props |
| `stack` | Overlapping children | Shared layout props |
| `scroll` | Scrollable container | `scrollX`, `scrollY`, `scrollOffset` |
| `label` | Text | `text`, `color`, `fontSize`, `font`, `wrap`, `truncate` |
| `button` | Clickable button | `text`, `disabled`, `tooltip` |
| `input` | Single-line text input | `value`, `placeholder`, `maxLength`, `disabled`, `appearance` |
| `slider` | Numeric slider | `value`, `min`, `max`, `step`, `disabled`, `appearance` |
| `item` | Read-only item visual | `item`, `count`, `itemTooltip`, `tooltip` |
| `dialog` | Modal viewport portal | `open`, `title`, `width`, `height`, `closeOnEscape`, `closeOnBackdrop`, `onDismiss` |
| `image` | Image or icon | `resource`, `fit`, `opacity`, `icon`, `crop` |
| `spacer` | Placeholder and space filler | Shared layout props |
| `workspace` | One logical desktop, normally controller-managed | Shared logical layout props |
| `window` | Direct workspace child, normally controller-managed | Numeric normal bounds, `responsiveRect`, `active`, frame capability flags/metrics and controlled resize/minimize/maximize/restore requests; see Workspace below |

Uppercase tags are ordinary JavaScript/TypeScript function components. They may return VNodes, text, numbers, empty values, booleans or arrays of renderable values; this example returns a VNode:

```tsx
function Section({ title, children }) {
  return (
    <panel padding={8} gap={4}>
      <label text={title} />
      {children}
    </panel>
  )
}
```

Probe extracts concrete callback signatures from the runtime: root/workspace render is `() => NekoUiRenderable`, and components use `NekoUiComponent<Props>`. Arbitrary objects and promises are not render results; a `void` function without a return is not an explicit empty render. Signal updaters are `(current: T) => T`; stores infer keys and value types from the initial object, for example `UI.createStore({count: 0}).update("count", current => current + 1)`. Returning a string is rejected. Dynamic keys can use `UI.createStore<Record<string, unknown>>({})`, with unknown values narrowed before use. Queued actions receive no arguments and their return value is ignored; `UI.batch` retains its callback result type. This does not change runtime scheduling.

**Controlled components and native editing boundaries**

Import `{ UI, Toggle, Slider, Select }` from `nekojs/jsx-runtime`. `Toggle` requires `id`, `checked: boolean`, `onChange(boolean)` and optional `label` / `text`; `Slider` requires `id`, `value: number`, `onChange(number)`; `Select` requires `id`, `value: string`, `options: readonly { value, label, disabled? }[]`, `expanded: boolean`, `onExpandedChange(boolean)`, `onChange(string)`. Components own no hidden signals: supply current state on every render. Option values must be unique and include the selected value; empty options permit only `value=""`. Select is an **inline**, scrollable list of at most six 20-pixel rows, not a floating native popup.

IDs must be root-unique. Toggle/Slider use their base IDs; Select uses its base trigger ID and reserves `id:root`, `id:options`, and `id:option:` plus `encodeURIComponent(value)`. Disabled controls also reject manual `root.dispatch`; dispatch cannot bypass disabled state.

Native `input` counts `maxLength` in Unicode code points. Control/Super+A selects all and Shift+Left/Right/Home/End extends selection. Control or Super + C/X/V copy/cut/paste; cut writes the clipboard before deleting, and clipboard failure preserves input. Paste filters ISO control characters (including CR/LF, tab and DEL) and unpaired surrogates while retaining valid supplementary characters. The single-line editor uses the same pixel offset for caret following, mouse hit testing, selection and clipping, clamped again when value/width shrinks. It does not provide multiline or IME composition contracts.

Native `slider` value/min/max/step default to 0/0/100/1. Values, span and span/step ratio must be finite, `min < max`, `step > 0`, and value must be in range. Interior interaction snaps to `min + round((raw-min)/step)*step` and clamps; dragging to endpoints and Home/End reach exact bounds even if off-step. Assigning a value does not auto-snap it. Left/Down subtract a step, Right/Up add, Home/End choose endpoints. Primary-button dragging captures only that slider, not generic drag/drop. **Lowercase primitive `onChange` receives an event with a string `event.value`; uppercase `Slider` calls back with a number.**

**Tabs, validation, genuine Dialog and read-only item contracts**

- Import named `{ Tabs, Dialog, validateForm, Validators }`; there are no `UI.Tabs`/`UI.Dialog` aliases. Tabs takes `id`, controlled `value`, unique `tabs: [{ value, label, disabled?, render: () => ... }]`, and `onChange(string)`. Tabs must be nonempty and contain the selected value. The selected header has a `[Label]` text marker; selection does not force an appearance override, and there is no roving-focus API. Only the selected renderer runs/mounts; external signals survive switching. It defaults to fill width/height, so specify an embedding height as in the example. Root-unique IDs reserve `id:tabs`, `id:tab:` + `encodeURIComponent(value)`, and `id:content`. Arrow/Home/End navigation skips disabled tabs.
- `validateForm(values, rules)` explicitly returns deeply frozen `{ valid, errors }`, with readonly message arrays for failed fields only, in field/rule order. It does not render errors, submit, normalize or contact a server. Values are a plain record of flat string/boolean/null/finite-number scalars; explicit undefined is invalid, while a missing rule field is passed to its rules as undefined. All rules run, fields can have multiple messages, and even an empty message string is an error. Built-ins: `Validators.required(message?)`, `minLength(n, message?)`, `maxLength(n, message?)`, `numberRange(min, max, message?)`. Required rejects missing/null/blank strings, not `0`/`false`; length rules require strings and count JavaScript UTF-16 code units, unlike native input maxLength code points. Number range accepts finite numbers or nonblank strings via `Number`, not integers only. A custom `(value, frozenValues) => message | null | undefined` rule supplies the integer check; promises or other return types are invalid. Nested/async validation is not supported.
- Dialog takes `id`, controlled `open`, `onOpenChange(boolean)`, optional `title`, `children`, `width`, `height`, `closeOnEscape`, `closeOnBackdrop`. Closed returns no nodes. Open creates a viewport portal with a centered 80% × 80% panel by default, not an in-flow styled panel. Width/height size the panel; add an explicit scroll child for overflowing content. Reserve `id:panel` and `id:content`. Both close flags default true; dismissal requests `false`, and your signal update actually closes it.
- The last visible dialog in depth-first order is the top modal. Common dispatch rejects background IDs, including queued events after opening; native pointer/capture/wheel/key/text/Tab/narration remain in its subtree. Escape is consumed before Screen closure even if dismissal is disabled; only primary clicks outside the panel request backdrop dismissal. Modal changes cancel old captures and focus an enabled modal descendant; closure restores prior focus only if still retained, visible and enabled. Parent clipping/scroll does not confine the portal. This behavior needs the native checklist, not just a panel screenshot.
- `<item item="minecraft:diamond" count={1} itemTooltip={true} />` accepts an item resource ID, not an ItemStack or component descriptor. Count is integer 1..99, default 1; the display limit is not a stack-limit guarantee. `itemTooltip` defaults true; custom `tooltip: string` overrides the vanilla tooltip. A known non-air item resolves once per staged native create/update; invalid/missing IDs reject the candidate, retaining the active tree. It paints a centered fixed 16×16 icon and count in its bounds; it is not an inventory Slot and grants no mutation/network authority. Only the topmost eligible hovered item shows a tooltip; background items under a modal do not. Native item rejection uses `NEKO-6012`; earlier common layout validation may instead report `NEKO-7007`.

<a id="wiki-section-12"></a>
## Layout

The UI uses Minecraft logical pixels and controlled constraints, not browser CSS. Common shared props include:

- Size: `width`, `height`, `minWidth`, `maxWidth`, `minHeight`, `maxHeight`.
- Spacing: `gap`, `spacing`, `padding`.
- Arrangement: `align` (`start`, `center`, `end`, `stretch`), `justify` (`start`, `center`, `end`, `spaceBetween`, `spaceAround`).
- Positioning: `direction`, `anchor` (such as `topLeft`, `center`, `bottomRight`).
- Display: `visible`, `coordinateSpace` (`logical` or `design`).
- Stable identity: `id` and `key` for list nodes.

For proportional sizing, supply the actual logical viewport together with paired `designWidth`/`designHeight` to `UI.createRoot`. Set `coordinateSpace="design"` on each participating primitive; it is not inherited. The design scale is `min(contentWidth / designWidth, contentHeight / designHeight)`. Numeric geometry, label text and native button/input/slider details scale together; input caret, selection, horizontal scrolling and slider hit mapping use the same design coordinates. Keep the design dimensions when forwarding native `resize` calls, which otherwise contain only logical dimensions. `logical` controls retain normal Minecraft GUI sizing. Scaling does not require replacing a two-column layout with separate pages.

The Java JSX host automatically reuses bounded text layouts and paints the visible line window for long labels. Multiline advances use base-font units before scaling, keeping the final lines reachable at fractional GUI scales. Changed layout inputs or font-resource reload invalidate cached results, and host disposal releases them. Scripts can use ordinary `label` and `scroll` nodes without manual line paging or a JavaScript text cache; copy actions can still read the complete original text.

Native `scroll` nodes automatically display a slim overlay scrollbar for overflowing content. The thumb shows the visible fraction and current offset, supports dragging, and track clicks move one viewport. Row-direction scrolls use a horizontal bar; other scrolls use a vertical bar. Bars overlay the trailing edge without reserving layout space; use content padding when necessary. Nested bars operate independently, respect clipping and modal boundaries, and cancel capture when their node is unavailable or overflow disappears. Scripts need no additional nodes or frame callbacks.

The built-in error dashboard on NeoForge 26.1.2/26.2.0 uses the bundled `nekojs/builtin-ui/error-dashboard.tsx` resource. It explicitly imports `UI` from `nekojs/jsx-runtime` as native ESM, using automatic JSX imports and fixed in-memory bundled resources; it does not install a global UI or classic JSX factory. Presentation and interactions are JSX/JS; Java supplies the Screen, real snapshots, verified clipboard writes and checked asynchronous source opening. Its root-owned session is independent of user CLIENT generations and closes with its Screen. A failed bundled UI falls back to the existing native dashboard. The internal session is not a public Script API or an exemption from ordinary host generation checks; other nodes retain their current error UI.

The CLIENT-only `I18n.translate(key, ...args)` Binding is available on all five supported nodes, including Fabric and NeoForge 1.21.1. It returns a plain string in the current client language, using resource-pack translations and Minecraft placeholders (`%s`, `%1$s`); missing keys remain visible. Arguments accept strings, numbers, booleans and null. For example, use `I18n.translate("gui.done")` for a button label. Call it again when rebuilding after a language/resource change: previously stored strings do not update automatically. SERVER scripts should use `Text.translatable(...)` to preserve the receiving player's language. This Binding does not add JSX support to other nodes.

`I18n.has(key)` checks the active translation table, including language fallbacks; a translation equal to its key still exists. `I18n.translateWithFallback(key, fallback, ...args)` translates an existing key or formats the literal fallback template using `Locale.ROOT` when the key is missing. The fallback is not another translation key. It accepts the same argument types; use `%%` for a literal percent sign. Malformed fallback formats return `Format error: ` followed by the template. Read-only `I18n.languageCode` returns the selected language code, such as `zh_cn`; `I18n.rightToLeft` reports the active translation table's direction. Both read current state on each access; read again after a language/resource reload completes. They do not change settings, subscribe to changes, or automatically mirror/rebuild UI. These members are shared by ordinary CLIENT scripts and the built-in diagnostic session.

I18n uses ordinary Java Binding interop and Bean properties, with input validation in the native implementation. Java varargs can expand a final array; typed scripts should prefer `I18n.translate(key, ...values)`. A single null format argument remains one value; null keys/fallbacks and object format values are rejected. Wrong arity, incompatible Java parameter types and writes to read-only properties use standard interop errors; there is no dedicated I18n member proxy.

Sizes can be numbers, `auto`, `fill`, or percentage strings:

```tsx
<panel width="80%" minWidth={240} maxWidth={640} padding={12}>
  <label text="会随窗口变化，但不会小于 240 像素" />
</panel>
```

To adjust values by viewport tier, pass responsive values. Profiles are logical tiers from 1 to 6, not fixed screen resolutions:

```tsx
<column
  padding={{ base: 8, profiles: { 1: 4, 5: 16, 6: 24 } }}
  gap={{ base: 6, profiles: { 1: 3, 6: 12 } }}
>
  {/* ... */}
</column>
```

Text dimensions, wrapping, and clipping are measured by the Minecraft font adapter. Scripts should not estimate character widths themselves.

<a id="wiki-section-13"></a>
## Visual styles and resources

JSX UI styling consists of node props and a host resolver, not CSS. Visual properties are flat and validated per primitive:

| Visual intent | NekoJS prop | Applicable elements |
|---|---|---|
| Text color | `color` | `label` |
| Background color | `background` | `panel` |
| Border color/width | `borderColor`, `borderWidth` | `panel` |
| Rounded corners | `radius` | `panel` |
| Overall opacity | `opacity` | `panel`, `image` |
| Font size | `fontSize` | `label` |
| Font resource | `font` | `label` |
| Wrapping/truncation | `wrap`, `truncate` | `label` |
| Image fit mode | `fit` | `image`, `contain` / `cover` / `stretch` |
| Image crop | `crop` | `image`, `{ x, y, width, height }` or a four-element array |
| Image/icon resource | `resource`, `icon` | `image` |

These visual properties are flat props. Currently, `color`, `background`, `borderColor`, `opacity`, and similar properties cannot be supplied as responsive `{ base, profiles }` objects. Responsive profiles are primarily for sizes, spacing, arrangement, visibility, and scrolling parameters.

<a id="wiki-section-14"></a>
### Color syntax

Colors only accept controlled formats:

- `#RGB`, `#RRGGBB`, `#AARRGGBB`;
- Basic CSS named colors and `transparent`, such as `red`, `navy`, `white`;
- Integer ARGB values, such as `0xFF4C566A`.

`rgb(...)`, `rgba(...)`, `hsl(...)`, and arbitrary extended CSS color names are unsupported. Eight-digit hex is always interpreted as `#AARRGGBB`, not CSS `#RRGGBBAA`. Write translucent white as `#80FFFFFF`; `#FFFFFF80` is parsed as a different ARGB color.

<a id="wiki-section-15"></a>
### The supported way to reuse styles

Use ordinary objects, spread, and function components to reuse visual tokens:

```tsx
const COLORS = Object.freeze({
  surface: '#20252B',
  border: '#4C566A',
  text: '#E5E9F0'
})

function Surface({ children }) {
  return (
    <panel background={COLORS.surface} borderColor={COLORS.border} borderWidth={1} radius={4} padding={8}>
      {children}
    </panel>
  )
}
```

Do not write `<panel style={COLORS}>`. `style` is not a registered prop and causes an `Unsupported prop` layout error.

**Control appearance:** `button`, `input`, `slider` and the components above accept a reusable `appearance` object. Only eight color fields are allowed: `background`, `borderColor`, `color`, `placeholderColor`, `selectionColor`, `cursorColor`, `trackColor`, `thumbColor`. Its `hover`, `focused`, `pressed`, `disabled` objects may contain only those color fields, not nested states. Precedence is base → hover → focused → pressed → disabled. Unknown keys, nulls and responsive color objects are invalid; pass layout props separately. This finite color configuration is not CSS and does not turn an input into another primitive. Colors follow the ARGB/named-color rules above, including signed/unsigned 32-bit ARGB integers.

<a id="wiki-section-16"></a>
### Resource IDs

`resource`, `icon`, and `font` only accept controlled resource IDs, such as `mymod:gui/panel`. They do not accept local file paths, absolute paths, or URLs. Missing resources, malformed IDs, and invalid dimensions produce UI diagnostics identifying the node and root; do not read arbitrary files or remote resources through the UI runtime.

<a id="wiki-section-17"></a>
## Migrating from HTML/CSS

If you already have a web page structure, you can use it as **design input**, but you cannot copy HTML/CSS directly into the runtime unchanged:

| Web construct | NekoJS construct | Description |
|---|---|---|
| Ordinary `div` block | `column` | Vertical arrangement by default |
| `display: flex; flex-direction: row` | `row` | `gap` maps directly |
| Absolutely positioned overlay | `stack` + child `anchor` | Only 9 anchor points, not arbitrary top/right/bottom/left |
| `overflow: auto` | `scroll` | Use `scrollX` / `scrollY` / `scrollOffset` |
| `span` / `p` / text node | `label` | Through text children or `text` |
| `img` | `image` | Replace `src` with a controlled `namespace:path` |
| `flex: 1` | `width="fill"` or `height="fill"` | Fill the remaining space along the main axis |
| `@media` | `{ base, profiles: { 1..6 } }` | Use logical viewport profiles |
| `z-index` | Child order in `stack` | Children drawn later cover earlier children |
| CSS Grid, complex selectors, animations | No direct equivalent yet | Rewrite using row/column, signals, or manual handling |

In particular, `margin` has no corresponding prop; normally use the parent's `gap`, `padding`, or a `spacer`. A single `padding` value is uniform inner padding; separate values for each side are not supported. `font-weight`, `font-style`, `line-height`, `letter-spacing`, `box-shadow`, CSS gradients, transitions, and pseudo-elements are also outside the current JSX UI contract.

<a id="wiki-section-18"></a>
## Reactive state

<a id="wiki-section-19"></a>
### Signal

`UI.createSignal(initial)` provides single-value state:

```tsx
const query = UI.createSignal('')

const root = UI.createRoot(() => (
  <column>
    <input
      id="query"
      value={query.get()}
      placeholder="搜索"
      onChange={event => query.set(event.value || '')}
    />
    <label>{`当前查询：${query.get()}`}</label>
  </column>
), adapter)
```

- `get()` reads the current value and establishes a dependency inside the render function.
- `set(value)` sets the value directly.
- `update(updater)` computes the new value from the old value.
- State changes only reconcile roots that depend on that state; they do not re-execute JSX on every render frame.

<a id="wiki-section-20"></a>
### Store

Use `UI.createStore(initial)` for several related fields:

```javascript
const form = UI.createStore({ name: '', enabled: true })
form.set('name', 'Neko')
form.update('name', oldName => oldName + 'JS')
console.log(form.snapshot())
```

Signals, stores, and UI roots do not automatically persist across CLIENT reloads. Scripts must explicitly save or reinitialize any business state that needs to survive.

<a id="wiki-section-21"></a>
### Batch updates

When several state values change in the same operation, use `UI.batch` to coalesce invalidation notifications:

```javascript
UI.batch(() => {
  form.set('name', 'Neko')
  form.set('enabled', false)
})
```

<a id="wiki-section-22"></a>
## Events

Shared event props include:

| Prop | Trigger |
|---|---|
| `onClick` | Click |
| `onRelease` | Mouse release |
| `onScroll` | Mouse wheel |
| `onKey` | Keyboard input |
| `onTextInput` | Text input |
| `onFocus` / `onBlur` | Gaining/losing focus |
| `onChange` | Input value changes |
| `onSubmit` | Input submission |

Event objects only contain stable script fields, such as `type`, `target`, `x`, `y`, `button`, `key`, `value`, and `delta`. Scripts do not need to handle `GuiGraphics`, native `Screen` objects, or Minecraft version-specific input events directly.

Events have no DOM bubbling, capture, `preventDefault()`, or `stopPropagation()`. Every node receiving callbacks needs an `id` unique within its root: real mouse/keyboard input and manual `root.dispatch(id, eventName, event)` both locate handlers through that ID. An `onClick` or `onChange` prop without an ID does not receive script events. The runtime freezes event objects. Handler exceptions produce `event`-phase diagnostics without disabling other events on the same Screen.

Elements such as `button`, `input`, and `scroll` manage their own hit testing, focus, disabled state, scrolling, and text editing. Once an old node is deleted or its root is closed, its event callbacks no longer receive input.

**A cooperative region, not another Screen**

`ClientUI.region(int x, int y, int width, int height)` returns a managed JsxHostAdapter without creating or navigating a Screen. Use GUI coordinates and positive representable extents; construction still requires the NeoForge 26.x CLIENT context/owner thread. A caller-owned Screen or non-Screen GUI owner must explicitly cooperate. The construction below is **not a standalone auto-opening script**: a real integration retains the adapter and forwards its frame, input, focus and detach. There is no automatic listener/Widget/third-party UI bridge.

```tsx
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
const region = ClientUI.region(24, 24, 180, 100);
const regionRoot = UI.createRoot(() => (
    <column width="fill" height="fill" padding={6} gap={6}>
        <label>Region clicks: {count.get()}</label>
        <button id="region-click" onClick={() => count.update(value => value + 1)}>Count</button>
    </column>
), region);
region.bindRoot(regionRoot);
```

Forward the real `render(GuiGraphicsExtractor, int mouseX, int mouseY)`, `mouseClicked(double x, double y, int button, int modifiers)`, `mouseDragged(double x, double y, int button)`, `mouseReleased(double x, double y, int button)`, `mouseScrolled(double x, double y, double delta)`, `keyPressed(int key, int modifiers)` and `charTyped(int codepoint)`. Pass caller GUI coordinates; do not pre-subtract the region origin. Only the committed generation paints/handles input. Rendering translates/clips the tree and restores caller pose/scissor. Each input method returns consumption: forward to the caller own control only when false, never invoke the same action twice.

Use `setRegion(int x, int y, int width, int height)` on resize, and `setFocused(boolean)`/`isFocused()` for the caller focus chain. Outside click clears keyboard focus; continue forwarding captured drag/release outside bounds. Without a modal, edge Tab clears focus and returns false for caller handoff. Losing keyboard focus does not itself cancel pointer capture. A modal preserves its boundary. The caller decides rendering and input order; this API does not automatically occlude arbitrary native controls.

Region `open()` is optional and never navigates/makes the caller visible. Normal `close(): boolean` succeeds only on literal true and remains retryable after failure; terminal detach/removal uses `dispose(): void`. Neither may close the caller Screen or a newer replacement. Reuse the adapter across resize/init rather than installing another input chain. Non-Screen callers need the same owner thread, actual GUI extractor, viewport, ordered input/focus and cleanup; no automatic HUD subscription or generic renderer adapter is provided. Real borrowed/non-Screen integration acceptance remains **not run**.

**One Screen, three logical windows**

The separate `nekojs/jsx-workspace` module manages one root in one host. It is not several active Minecraft Screens, extra OS windows, or a second reconciler. Save this complete bundled example as `nekojs/client_scripts/authoring-examples/workspace.tsx`, remove other auto-opening examples, then run `/nekojs reload client` on NeoForge 26.x with ClientUI. State is allocated outside the window renderers.

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI } from "nekojs/jsx-runtime";
import { createWorkspace } from "nekojs/jsx-workspace";

const note = UI.createSignal("Draft note");
const toolText = UI.createSignal("Tool input");
const saved = UI.createSignal(0);
const clicks = UI.createSignal(0);
const host = ClientUI.screen("Authoring workspace", false);
const workspace = createWorkspace(host, {
    minimizedPresentation: "taskbar",
    taskbarHeight: 28
});

workspace.add("notes", () => (
    <scroll width="fill" height="fill">
        <column padding={6} gap={6} width="fill">
            <input id="notes-text" width="fill" height={20} maxLength={80}
                value={note.get()} onChange={event => note.set(event.value || "")} />
            <button id="notes-save" height={20} onClick={() => saved.update(value => value + 1)}>
                Save locally
            </button>
            <label id="notes-count">{"Notes saved: " + saved.get()}</label>
        </column>
    </scroll>
), {
    title: "Notes", rect: { x: 12, y: 12, width: 190, height: 136 },
    responsiveRect: {
        x: { base: 12, profiles: { 1: 8, 2: 12 } },
        width: { base: 190, profiles: { 1: "70%", 2: "60%" } },
        height: "65%"
    },
    minWidth: 160, minHeight: 100, maxWidth: 480, maxHeight: 320,
    resizable: true, minimizable: true, maximizable: true,
    titlebarHeight: 24, titlebarButtonWidth: 18, titlebarTitle: true
});

workspace.add("tools", () => (
    <scroll width="fill" height="fill">
        <column padding={6} gap={6} width="fill">
            <input id="tools-text" width="fill" height={20} maxLength={80}
                value={toolText.get()} onChange={event => toolText.set(event.value || "")} />
            <button id="tools-click" height={20} onClick={() => clicks.update(value => value + 1)}>
                Count this window
            </button>
            <label id="tools-count">{"Tools clicks: " + clicks.get()}</label>
        </column>
    </scroll>
), {
    title: "Tools", rect: { x: 90, y: 42, width: 190, height: 136 },
    minWidth: 140, minHeight: 100,
    resizable: true, minimizable: true, maximizable: true,
    titlebarTitle: false
});

workspace.add("preview", () => (
    <scroll width="fill" height="fill">
        <column padding={6} gap={6} width="fill">
            <item id="preview-item" item="minecraft:diamond" count={1} />
            <label id="preview-summary">{
                "Saved=" + saved.get() + "; clicks=" + clicks.get()
            }</label>
            <label>Drag a titlebar; close one window without closing the desktop.</label>
        </column>
    </scroll>
), {
    title: "Preview", rect: { x: 160, y: 72, width: 190, height: 136 },
    resizable: true, minimizable: true, maximizable: true
});

workspace.open();
```

Notes and Tools start with independent text and zero counters; their first buttons update only the corresponding counters and Preview. Edit Notes, minimize it, then use its titled Notes taskbar button to restore the same text/counters and keyed content. Try maximize → minimize → restore, edge/corner resize and GUI profile/scale changes. Notes demonstrates numeric normal restore bounds plus responsive fields and 24/18 titlebar metrics; Tools hides title text only. Preview remains a read-only diamond visual, never an inventory operation. The reserved taskbar reduces available work area even when empty. Close the Screen, remove the script and reload CLIENT to reset the four signals; no disk or world state is saved. These are pending observations, not native/GPU/physical-client acceptance.

**Controller:** `createWorkspace(host, options?)` creates and binds the only root. Use `add(id, render, config?)`, `update(id, patch)`, `activate(id)`, `hide(id)`, `show(id)`, `minimize(id)`, `maximize(id)`, `restore(id)`, `resize(id, rectPatch)`, `closeWindow(id)`, `open()`, `close()`, `isDisposed()`, `snapshot()` and `layout()`. Never bind another root to that host. IDs are stable nonempty strings; configuration is plain data, not a native Widget or operating-system window. Mutations return a boolean on the owner thread or `"queued"` when admitted off-thread; queued is not completion. A rejected refresh preserves the committed window state. Keep renderers pure and mutate through events.

**Window state:** `mode` is `"normal"`, `"minimized"` or `"maximized"`. The immutable back-to-front `snapshot().windows` also records `restoreMode` (`"normal"` or `"maximized"`), visibility, capabilities and normal geometry. `snapshot().activeId` identifies the active window. `layout()` reads actual published constrained geometry and is null after disposal.

| Action | Meaning |
|---|---|
| `minimize(id)` | Requires `minimizable: true`; retains the keyed content and remembers whether it came from normal or maximized mode |
| `maximize(id)` | Requires `maximizable: true`; fills the available work area without replacing the normal restore bounds or responsive policy |
| `restore(id)` | Minimized returns to its remembered mode; maximized returns to normal |
| `show(id)` / `activate(id)` | Restore a minimized window, then raise and activate it; show also makes it visible |
| `hide(id)` | Keeps the mode and keyed content but hides both the window and its minimized presentation |
| `resize(id, rectPatch)` | Requests a normal-window resize when `resizable: true`; the patch contains numeric rectangle fields, not native edge metadata |
| `closeWindow(id)` | Removes that logical window; this is distinct from minimize or hide |

Minimize and hide retain local content identity and external signals; neither suspends signal-driven work or persists anything to disk. Only actual removal of the last window can trigger `closeWhenEmpty: true`; ordinary host close remains retryable rather than guaranteed disposal. A fresh CLIENT generation recreates the example's four business signals.

**Normal and responsive geometry:** `rect: { x, y, width, height }` remains numeric requested normal/restore geometry, defaulting to `{ x: 16, y: 16, width: 240, height: 160 }`. It is not the current maximized rectangle. Normal minimum width/height default to 40/20; optional finite `minWidth`, `minHeight`, `maxWidth`, `maxHeight` must be ordered and cannot lower those normal minima. A smaller available work area takes precedence over a normal minimum. Maximized layout fills that work area rather than applying normal min/max constraints.

`responsiveRect` independently specifies `x`, `y`, `width`, `height` using finite numbers, percentage strings, or existing `{ base, profiles: { 1: ..., 6: ... } }` values. Profiles are logical tiers 1–6, not CSS media queries. Numeric width/height must be nonnegative and percentage branches strictly positive. `update(id, { responsiveRect: policy })` replaces the whole policy, not a deep merge; `responsiveRect: null` clears it. Percentages use the workspace work area, not physical display pixels. Missing fields fall back to numeric `rect`. Manual native move/resize materializes the currently shown normal bounds and clears the responsive policy; maximizing alone preserves it. Read `layout()`, not the snapshot's requested rectangle, to inspect the shown result.

**Opt-in frame controls:** `resizable`, `minimizable`, `maximizable` default to false. Legacy windows retain their draggable 20-pixel titlebar and close button. Flat `titlebarHeight`, `titlebarButtonWidth`, `titlebarTitle` configure the frame: defaults are 20, 20, true; height must be an integer at least 0 and button width an integer at least 1. Effective chrome is constrained by the actual window bounds. Height 0 hides every titlebar control and titlebar dragging, not just its text; keep an explicit programmatic restore path if using it. `titlebarTitle: false` hides only the title text. This is finite frame configuration, not CSS or a new window lifecycle owner.

**Default window outline:** Every native window has a subtle gray inner outline, with active windows slightly brighter than inactive ones. Its nominal thickness stays at one framebuffer pixel across Minecraft GUI scales. It is painted above the window content and below resize-hover feedback, so adjacent windows remain distinguishable without widening their bounds or resize hit bands. Hiding the titlebar does not remove the outline; hidden windows paint nothing. This default styling adds no Script API configuration.

**Chrome hover feedback:** Resizable normal windows request Minecraft's horizontal or vertical resize cursor at the corresponding edge. Corners use the native all-direction resize cursor and highlight both matching edges; the supported Minecraft profiles do not provide built-in diagonal cursor types. The hit edge highlights above window content, and a captured resize keeps its original direction until release or cancellation. Titlebar controls take priority over overlapping resize bands: Close highlights red, while Minimize and Maximize/Restore use a lighter background. Covered, hidden or disabled windows do not receive hover feedback, and a modal blocks underlying window chrome. Cursor requests are frame-local; a cooperative region requests a cursor only for its resize hit or retained resize capture, leaving the caller's other cursor choices intact.

**Raw primitive boundary:** a lowercase `window` accepts native layout `mode="normal"` or `mode="maximized"`; the controller implements its separate `"minimized"` state by retaining hidden keyed content. Raw `onMinimize`, `onMaximize` and `onRestore` are change requests, not automatic state changes. `onResize` carries `{ x, y, width, height, edge }`, where `edge` is `n`, `ne`, `e`, `se`, `s`, `sw`, `w` or `nw`. Let the controller wire these handlers in the complete example. Manual dispatch remains gated by modal/visibility/active-window/capability checks and cannot create missing resize or minimize authority.

**Minimized presentations:** configure `minimizedPresentation` when creating the controller. Its default `"none"` keeps the legacy workspace root unchanged; minimized windows then need your own restore action. `"taskbar"` adds a full-width row of equal-fill titled restore buttons, reserving `taskbarHeight` (default 28, integer at least 1) even when no window is minimized. The workspace gets the remaining nonnegative height. If the safe viewport is shorter than the bar, the bar keeps its requested height but is clipped, and the workspace can have zero height; no minimum-size promise overrides that limit.

`"floating"` instead adds a bottom-left panel, width 220 and height `min(160, 24 * visibleMinimizedCount + 4)`, with a scrollable column of 20-high buttons, gap 4 and padding 4. It reserves no work area. Hidden windows are absent from every minimized presentation; only visible minimized windows receive restore buttons.

A custom callback receives an immutable `{ windows, restore, activate }` context, where `windows` contains only visible minimized snapshots. It must return exactly one direct `panel` VNode with explicit `width`, `height` and `anchor`, or `null`. Child components are allowed; an outer component, array or Fragment is not. The controller marks that panel as the workspace presentation while preserving its key/children, without inserting a fill-size wrapper. Existing responsive panel styles are allowed, but a huge opaque panel can intentionally block underlying windows throughout its bounds. Keep the panel bounded, render it without side effects, and call `restore(id)` only from an event handler.

**Alternative initializers — replace, do not append:** keep the same host, business signals and window definitions. Replace only the shipped `const workspace = createWorkspace(...)` statement with **one** of these initializers. Do not create a second controller/root or install the variants as additional auto-opening scripts.

```tsx
const workspace = createWorkspace(host, { minimizedPresentation: "floating" });
```

```tsx
const workspace = createWorkspace(host, {
    minimizedPresentation: context => {
        if (context.windows.length === 0) return null;
        return (
            <panel width={220} height={32} anchor="bottomRight" background="#202020">
                <row padding={4} gap={4} width="fill" height="fill">
                    {context.windows.map(window => (
                        <button key={window.id} id={`demo-workspace-restore:${window.id}`}
                            width="fill" height="fill"
                            onClick={() => context.restore(window.id)}>{window.title}</button>
                    ))}
                </row>
            </panel>
        );
    }
});
```

The callback type is `(context: NekoWorkspaceMinimizedContext) => NekoVNode | null`; it receives no native Screen or inventory authority. This small replacement panel has no hidden full-screen wrapper; empty space outside its bounds is not its input surface. A zero-height titlebar or `"none"` presentation still needs an explicit controller restore action. Every interactive raw button needs an explicit stable `id`; `key` supplies reconciliation identity, not event registration. Presentation panels are outside window scopes, so these IDs must be root-unique. Keep the example’s `demo-workspace-restore:` prefix distinct from the application’s own window/node IDs.

Use a fresh managed host. Binding the identical guest root handle again is idempotent; `bindRoot` rejects another handle, and layout rejects a mismatched root ID before publication (`NEKO-7014`). These checks do not make a host shareable by separately constructed roots with matching ID text. Only one workspace is allowed, as the root or a direct child of root `screen`; direct children must be windows, with at most one active window. Geometry including ancestors must be logical, not design-space. `show` raises/activates; `hide` keeps identity and signal updates but blocks input. `closeWhenEmpty: true` requests normal host close, not guaranteed disposal on failure. Root-publication/nested-render controller mutations are rejected. Descendant explicit IDs are scoped after component expansion: different windows may reuse local IDs, but duplicates in one window fail; native window IDs remain exact. For manual dispatch read the opaque actual ID from published layout/native props, never construct its serialized format.

Titlebar/resize capture, activation, active-window keyboard focus and opaque-window/presentation occlusion belong to the shared host; do not install independent global input listeners per window. Minimize/hide retain keyed content and signal updates but do not suspend background work or persist data. This is still not snapping, layout persistence, arbitrary Screen/Widget embedding, server inventory authority or a measured speedup. Real GPU, physical input, focus/occlusion and accessibility acceptance remain **not run**.

| Pending workspace check | Observe through real client input | Status |
|---|---|---|
| Retained minimize/restore | Edited Notes and counters survive; hidden content cannot receive input; a minimized maximized window returns maximized first | Not run |
| Resize/chrome/responsive bounds | Eight resize edges/corners, min/max and tiny work areas align drawing with hit tests; manual move/resize clears responsive policy; zero chrome needs an explicit restore path | Not run |
| Presentation ownership | Taskbar reserves space; floating/custom panels do not; only panel bounds occlude eligible underlying windows; modal priority still wins | Not run |

<a id="wiki-section-23"></a>
## Root lifecycle

The handle returned by `UI.createRoot` provides the following operations:

| Method | Description |
|---|---|
| `id` | The root's stable identifier |
| `refresh()` | Request a new render |
| `resize(viewport)` | Recalculate layout for a new logical viewport |
| `layout()` | Read the most recent layout snapshot |
| `dispatch(id, eventName, event)` | Dispatch a controlled event to a specified node |
| `isDisposed()` | Check whether the root has been disposed |
| `isUpdating()` | True throughout render, expansion, layout, staging, commit and ordinary host clearing; false while idle/disposed |
| `close()` | Retryable release; false while updating or if host clearing fails; does not dismiss the native Screen |
| `forceClose()` | Owner-thread-only terminal release, including when host clearing fails; no success boolean |

Function identity plus the outer key/unkeyed position scopes component-local child keys. Independent component instances may reuse local keys; direct duplicate siblings still fail, and unkeyed fragments/arrays remain transparent. Expanded IDs remain unique within the root. Keep render functions stable rather than recreating a component function on each render.

Re-entrant owner-thread refresh returns `"queued"` and serializes follow-up work; it does not recursively commit. Only stable candidates publish, with at most 32 passes before a render diagnostic. `true` means at least one commit even if a later retry fails; `false` means none. Off-owner admission is also `"queued"`, not completion. Keep render-phase work pure.

Normal close remains retryable after failure/busy. Owner-thread resize also returns false while busy, without queueing another viewport; retry when idle. Terminal force-close marks disposal and drops render/event/dependency references immediately; during an update, native clearing waits for that transaction to finish. It cannot revive through queued work and must only be invoked on the owner thread. Native user navigation should use the host normal close, not force-close as a routine event action.

A root is bound to the CLIENT generation that created it:

- After a successful CLIENT reload, old roots, Screens, event closures, and VNodes become invalid.
- A candidate generation cannot affect the currently running Screen before commit.
- If reload fails, the current active UI remains usable.
- Owned Screen title/pause policy comes from committed root `screen` props, falling back to factory defaults. Rejected candidates cannot change it; `pausesGame` does not pause a remote server.
- Explicit normal host close is retryable; native Screen removal/replacement/client exit force terminal cleanup. Late cleanup cannot close a newer Screen.
- Operations through stale handles are rejected and produce the corresponding `NEKO-700x` diagnostic instead of silently operating on a new generation.

<a id="wiki-section-24"></a>
## Resources and diagnostics

`image` uses controlled resource IDs; fonts and textures are resolved by the Minecraft resource manager. Resource reading, decoding, dimension, and upload failures enter the JSX UI diagnostic chain.

Common diagnostics include:

- `NEKO-7001`: the root is invalid or belongs to an old generation.
- `NEKO-7003`: the host adapter was not created from a managed CLIENT script context.
- `NEKO-7004`: the operation is not running on the client owner thread.
- `NEKO-7006`: deferred work from an old generation was dropped.
- `NEKO-7007`: a render, layout, event, or host-update phase failed.
- `NEKO-7013`: Workspace authoring rejected invalid configuration/IDs or a controller mutation during publication; direct calls can throw before any native logging.
- `NEKO-7014`: invalid GUI region bounds, a region-only operation on an owned Screen mount, or single-root ownership violation; construction/setRegion/binding/publication can reject directly.
- `NEKO-7008`: native host validation rejects control appearance fields, types or colors.
- `NEKO-7011`: native host validation rejects slider bounds, step or value.

The common JSX runtime may reject malformed props earlier during layout, reporting `NEKO-7007` with the layout diagnostic rather than those native codes. Do not assume every invalid prop produces one specific native code. Clipboard host failures also enter `NEKO-7007`.

Render or layout failures do not commit an incomplete tree. If a valid tree already exists, the runtime attempts to retain the last valid result and sends the error through the existing error-reporting chain.

<a id="wiki-section-25"></a>
## Other approaches

JSX is not the only way to build client interfaces. Choose a different owner according to your requirements:

<a id="wiki-section-26"></a>
### Approach A: JSX retained Screen

Use `ClientUI.screen` + `UI.createRoot` as described on this page. The runtime retains the committed host tree and performs render/layout/commit again when state changes; ordinary paint frames only read the committed tree.

Suitable for:

- Settings pages, configuration pages, search fields, lists, pagination, and reusable forms;
- Interfaces that need focus, keyboard input, text input, scrolling, and root cleanup during reload;
- Managing state through function components and signals/stores.

Not suitable for:

- Per-frame animated drawing, particle-like effects, or free-form drawing paths;
- Custom native Minecraft controls or menu/Slot network synchronization;
- Adding a JSX primitive. The primitive allowlist is currently fixed; scripts cannot register new tags.

<a id="wiki-section-27"></a>
### Approach B: Draw directly with `PainterJS`

If you only need a HUD or a layer drawn on an existing Screen, use `ClientEvents.hud` or `ClientEvents.screenRender`:

```javascript
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.outline(8, 8, 120, 18, 0xFF708090)
  painter.text('任务进行中', 14, 14, 0xFFFFFFFF)
})

ClientEvents.screenRender(event => {
  const painter = event.painter
  painter.text(`鼠标：${event.mouseX}, ${event.mouseY}`, 8, 8)
})
```

`PainterJS` provides rectangles, outlines, gradients, text, textures, item icons, transforms, clipping, font measurement, and text wrapping. It uses **immediate-mode drawing**: the callback runs every render frame, and the script manages coordinates, drawing order, and animation state itself.

It does not automatically provide JSX UI layout, focus, input fields, scrolling, root reconciliation, or state subscriptions. `screenRender` can only observe and draw over the current Screen; it cannot turn that Screen into your JSX root.

<a id="wiki-section-28"></a>
### Approach C: Registered HUD / world rendering

Use `ClientEvents.hudRender` and `ClientEvents.worldRender` when you need to manage persistent renderers by ID:

```javascript
ClientEvents.hudRender('status-bar', { layer: 'foreground', priority: 0 }, (ctx, gui) => {
  ctx.text('状态', 8, 8)
})

ClientEvents.worldRender('quest-path', { layer: 'normal', priority: 0 }, ctx => {
  ctx.line(0, 64, 0, 10, 70, 10, 0xFFFFFF00, 2)
})
```

These entry points are registered and replaced together by the CLIENT generation and are suitable for HUD/world overlays. They remain render callbacks, not interactive Screens or JSX host trees.

These **registered** entry points are NeoForge-only, unlike Painter HUD/Screen events that also exist on Fabric. NeoForge 26.2 `ctx.line` / `ctx.box` now submit through an independently owned staged buffer; source/geometry regressions still do not establish visual acceptance. Layer order, priority, replacement/cleanup, and post-effect declaration versus activation are covered in [Client rendering and post effects](client-rendering_us). The options map above is the current runtime contract, not a newly invented builder overload; JSX props are data, not NekoJS definition builders. See [Script API style](script-api-style_us).

<a id="wiki-section-29"></a>
### Approach D: Native Java Screen / Widget

If you need full native Minecraft controls, menus, Slots, complex text editing, or custom input routing, use a Java plugin or an existing native Screen extension point. This offers the most control, but also requires you to handle Minecraft version differences, lifecycle, focus, input, and reload cleanup yourself.

Native objects such as `Screen` and `GuiGraphics` do not automatically become JSX elements. Embedding a native Screen inside JSX is not a current runtime extension mechanism either; implement such integrations in the plugin/platform Adapter layer.

<a id="wiki-section-30"></a>
### Choosing an approach

| Requirement | Recommended approach |
|---|---|
| Configuration pages, forms, lists, input fields | JSX Screen |
| Text, health bars, rectangles, or textures on the HUD | `ClientEvents.hud` / `hudRender` |
| Coordinates or hints over an existing interface | `ClientEvents.screenRender` |
| Wireframes, paths, and 3D markers in the world | `ClientEvents.worldRender` |
| Native menus, Slots, complex Widgets | Java Screen / plugin Adapter |
| Plain data trees or rendering configuration | Classic JSX factory or ordinary JS objects |

<a id="wiki-section-31"></a>
## Current limitations

JSX client UI does not currently provide:

- UI parity on Fabric, NeoForge 1.21.1, or Cleanroom;
- Server-side containers, menus, Slots, inventory synchronization, or multiplayer authoritative state;
- JSX replacements for HUD, Overlay, world rendering, or PostEffects entry points;
- Arbitrary Java classes, native Widgets, `GuiGraphics`, or GL objects as JSX tags;
- React/Preact hooks, DOM, a CSS parser, CSS Grid, or full browser layout;
- `className`, `style`, CSS selectors/cascade, margins, shadows, gradients, transforms, z-index, or font weight/style/family;
- Remote UI downloads, network resource proxies, a general animation system, or arbitrary shaders.
- Generic drag/drop, virtualized lists, IME composition, or a floating native Select popup.

Existing `ClientEvents.hud`, `hudRender`, `worldRender`, `screenRender`, and handwritten Java Screens continue to work through their original APIs. Enabling JSX UI does not migrate them automatically.

<a id="wiki-section-32"></a>
## Related pages

- [TypeScript and JSX](typescript-and-jsx_us): TS erasure, JSX lowering, and the automatic runtime.
- [Event reference](event-reference_us): `ClientEvents`, `KeyBindEvents`, and client event limitations.
- [Platforms and compatibility](platform-compatibility_us): current NeoForge/Fabric support status.
- [FAQ](faq_us): automatic JSX and client script troubleshooting.

<!-- wiki-nav -->

---

[Previous: TypeScript and JSX](typescript-and-jsx_us) · [Contents](Home) · [Next: Client rendering and post effects](client-rendering_us)
