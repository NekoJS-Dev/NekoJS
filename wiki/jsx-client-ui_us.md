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

Lowercase tags must come from these 11 primitives: `screen`, `panel`, `row`, `column`, `stack`, `scroll`, `label`, `button`, `input`, `image`, and `spacer`. Use `UI.primitives()` to query the current runtime's registered set.

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
| `input` | Text input | `value`, `placeholder`, `maxLength`, `disabled` |
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
  const painter = event.getPainter()
  painter.text(`鼠标：${event.getMouseX()}, ${event.getMouseY()}`, 8, 8)
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

Existing `ClientEvents.hud`, `hudRender`, `worldRender`, `screenRender`, and handwritten Java Screens continue to work through their original APIs. Enabling JSX UI does not migrate them automatically.

<a id="wiki-section-32"></a>
## Related pages

- [TypeScript and JSX](typescript-and-jsx_us): TS erasure, JSX lowering, and the automatic runtime.
- [Event reference](event-reference_us): `ClientEvents`, `KeyBindEvents`, and client event limitations.
- [Platforms and compatibility](platform-compatibility_us): current NeoForge/Fabric support status.
- [FAQ](faq_us): automatic JSX and client script troubleshooting.

<!-- wiki-nav -->

---

[Previous: TypeScript and JSX](typescript-and-jsx_us) · [Contents](Home) · [Next: Node.js compatibility](nodejs-compatibility_us)
