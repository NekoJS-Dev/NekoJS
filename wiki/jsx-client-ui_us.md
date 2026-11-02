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

Lowercase tags must come from these 14 primitives: `screen`, `panel`, `row`, `column`, `stack`, `scroll`, `label`, `button`, `input`, `slider`, `image`, `item`, `dialog`, and `spacer`. Use `UI.primitives()` to query the current runtime's registered set.

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

Uppercase tags are ordinary JavaScript/TypeScript function components. Components should return VNodes:

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
| `close()` | Release the root's nodes, subscriptions, and handlers; it does not dismiss the native Screen automatically |

A root is bound to the CLIENT generation that created it:

- After a successful CLIENT reload, old roots, Screens, event closures, and VNodes become invalid.
- A candidate generation cannot affect the currently running Screen before commit.
- If reload fails, the current active UI remains usable.
- Player-initiated closure, replacement by another Screen, and client exit all use the same cleanup process.
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
