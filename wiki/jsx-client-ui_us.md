<!-- wiki-page: jsx-client-ui; locale: us -->

> **English** · [中文](jsx-client-ui_cn)

<a id="wiki-section-1"></a>
# JSX client UI

Build Minecraft screens with JSX: buttons, text fields, sliders, tabs, dialogs, even a desktop with several windows. You describe what the screen looks like and how its state changes. NekoJS handles layout, drawing, focus, and mouse and keyboard input.

> **Available on NeoForge 26.1.2 and 26.2.0 only.** UI scripts go in `client_scripts/` and use the global `ClientUI` object. Fabric, NeoForge 1.21.1 and dedicated servers have no `ClientUI`. See [Platform and compatibility](platform-compatibility_us).

<a id="wiki-section-2"></a>
## Setup

First turn on the automatic JSX runtime. In `nekojs/config/engine.toml`, set:

```toml
jsxAutomaticRuntime = true
```

Restart the game, then run `/nekojs probe` once so your editor picks up JSX completion.

`nekojs/jsx-runtime` ships with NekoJS. You do not need to put anything in `node_modules/`.

<a id="wiki-section-3"></a>
## Your first screen

Save this as `nekojs/client_scripts/counter.tsx` and run `/nekojs reload client`:

```tsx
import { UI } from 'nekojs/jsx-runtime'

const count = UI.createSignal(0)
const host = ClientUI.screen('Counter', false)

const root = UI.createRoot(() => (
  <screen padding={12}>
    <column gap={8} align="center">
      <label>{`Count: ${count.get()}`}</label>
      <button id="increment" onClick={() => count.update(value => value + 1)}>
        Add one
      </button>
    </column>
  </screen>
), host)

host.bindRoot(root)
host.open()
```

The screen opens right after the reload. Each click adds one. Press Esc to close it.

Here is what the code does:

1. `UI.createSignal(0)` creates a piece of state that starts at 0.
2. `ClientUI.screen(title, pausesGame)` creates a screen host.
3. `UI.createRoot(render, host)` renders the UI. When any state read inside the render function changes, the screen updates by itself.
4. `host.bindRoot(root)` attaches the UI to the host.
5. `host.open()` opens the screen.

A few rules:

- Create signals **outside** the render function. Inside it, they reset on every re-render.
- An element needs an `id` to receive events. With `onClick` but no `id`, clicks do nothing.
- A closed screen cannot be opened again. To reopen it, create a new host and root, for example by wrapping the code above in a function and calling it when needed.

<a id="wiki-section-4"></a>
## This is not HTML

JSX is only syntax. Lowercase tags like `<panel>`, `<row>` and `<button>` are NekoJS built-in elements, not web tags. So:

- `<div>`, `<span>`, `<form>` and other web tags do not exist.
- There is no CSS: no `className`, no `style={{ ... }}`, no selectors, no inheritance.
- Properties like `margin`, `box-shadow`, `transform`, `z-index` and `font-weight` do not exist.
- Java classes, vanilla widgets and `GuiGraphics` cannot be used as tags.

An unsupported property is an error. For example, `style` gives `Unsupported prop`.

To reuse styles and structure, write function components. See "Components and lists" below.

<a id="wiki-section-5"></a>
## Built-in elements

Lowercase tags must be one of these 16. You can also call `UI.primitives()` to list them.

| Element | Purpose | Common properties |
|---|---|---|
| `screen` | Screen root | `title`, `pausesGame`, `closeOnEscape` |
| `panel` | Container with background and border | `background`, `borderColor`, `borderWidth`, `radius`, `opacity` |
| `row` | Horizontal layout | Shared layout properties |
| `column` | Vertical layout | Shared layout properties |
| `stack` | Children on top of each other | Shared layout properties; children use `anchor` |
| `scroll` | Scrollable area | `scrollX`, `scrollY`, `scrollOffset` |
| `label` | Text | `text`, `color`, `fontSize`, `font`, `wrap`, `truncate` |
| `button` | Button | `text`, `disabled`, `tooltip`, `appearance` |
| `input` | Single-line text field | `value`, `placeholder`, `maxLength`, `disabled`, `appearance` |
| `slider` | Number slider | `value`, `min`, `max`, `step`, `disabled`, `appearance` |
| `image` | Image or icon | `resource`, `icon`, `fit`, `crop`, `opacity` |
| `item` | Read-only item icon | `item`, `count`, `itemTooltip`, `tooltip` |
| `dialog` | Modal dialog (prefer the `Dialog` component) | `title`, `closeOnEscape`, `closeOnBackdrop` |
| `spacer` | Empty space | Shared layout properties |
| `workspace` | Multi-window desktop (managed by `createWorkspace`) | Shared layout properties |
| `window` | One window on the desktop (managed by `createWorkspace`) | See "Multi-window workspace" below |

Text for `label` and `button` can be a child (`<label>Hello</label>`) or the `text` property.

Capitalized tags are your own function components. See "Components and lists" below.

<a id="wiki-section-6"></a>
## Layout

Units are Minecraft GUI pixels. Every element accepts these shared properties:

- Size: `width`, `height`, `minWidth`, `maxWidth`, `minHeight`, `maxHeight`. A value can be a number, `"auto"`, `"fill"` (take the remaining space) or a percentage string such as `"80%"`.
- Spacing: `gap` (between children) and `padding` (the same on all four sides).
- Alignment: `align` is `start`, `center`, `end` or `stretch`. `justify` is `start`, `center`, `end`, `spaceBetween` or `spaceAround`.
- Direction and position: `direction` (`row` or `column`). Inside a `stack`, `anchor` places a child at one of nine spots such as `topLeft`, `center` or `bottomRight`.
- Display: `visible`.
- Identity: `id`, plus `key` for list items.

```tsx
<panel width="80%" minWidth={240} maxWidth={640} padding={12}>
  <label>Grows with the window but never narrower than 240 pixels</label>
</panel>
```

Text width and wrapping are measured with the game font. You do not need to estimate them.

<a id="wiki-section-7"></a>
### Adapting to window size

Size, spacing, alignment, visibility and similar properties accept a responsive value: `{ base, profiles: { tier: value } }`. NekoJS picks a tier from 1 to 6 based on the current screen size:

| Tier | Minimum width | Minimum height |
|---|---|---|
| 1 | None | None |
| 2 | 320 | 180 |
| 3 | 480 | 240 |
| 4 | 640 | 360 |
| 5 | 854 | 480 |
| 6 | 1280 | 720 |

Width and height each give a tier, and the smaller one wins. Units are GUI pixels, so a larger GUI scale lowers the tier.

```tsx
<column
  padding={{ base: 8, profiles: { 1: 4, 5: 16, 6: 24 } }}
  gap={{ base: 6, profiles: { 1: 3, 6: 12 } }}
>
  {/* ... */}
</column>
```

How a value is chosen: the current tier first, then the nearest lower tier you wrote, then `base`. Higher tiers are never used. So the `padding` above is 4, 4, 4, 4, 16, 24 for tiers 1 to 6.

When the window is resized, the screen is laid out again. The render function does not run again.

Colors and other visual properties do not accept responsive values.

<a id="wiki-section-8"></a>
### Scrolling

Wrap content that may not fit in a `scroll`. When content overflows, a thin scrollbar appears. You can drag it or click the track to move one page. A `scroll` with direction `row` scrolls horizontally; otherwise it scrolls vertically. The scrollbar sits over the content edge and takes no layout space, so add some `padding` if needed.

For long text, put it in a `label` inside a `scroll`. No manual paging needed.

<a id="wiki-section-9"></a>
## Styling

Styles are element properties. Each element accepts only its own:

| Effect | Property | Elements |
|---|---|---|
| Text color | `color` | `label` |
| Background | `background` | `panel` |
| Border color and width | `borderColor`, `borderWidth` | `panel` |
| Rounded corners | `radius` | `panel` |
| Opacity | `opacity` | `panel`, `image` |
| Font size | `fontSize` | `label` |
| Font | `font` | `label` |
| Wrap or truncate | `wrap`, `truncate` | `label` |
| Image scaling | `fit`: `contain` / `cover` / `stretch` | `image` |
| Image crop | `crop`: `{ x, y, width, height }` or an array of four numbers | `image` |
| Image resource | `resource`, `icon` | `image` |

<a id="wiki-section-10"></a>
### Colors

Colors can be:

- `#RGB`, `#RRGGBB` or `#AARRGGBB`.
- A basic color name or `transparent`, such as `red`, `navy` or `white`.
- An ARGB integer, such as `0xFF4C566A`.

Watch out: eight-digit hex puts **alpha first** (`#AARRGGBB`), the opposite of CSS `#RRGGBBAA`. Half-transparent white is `#80FFFFFF`.

`rgb(...)`, `rgba(...)` and `hsl(...)` are not supported.

<a id="wiki-section-11"></a>
### Reusing styles

Keep colors in a plain object and write a function component:

```tsx
const COLORS = {
  surface: '#20252B',
  border: '#4C566A',
  text: '#E5E9F0'
}

function Surface({ children }) {
  return (
    <panel background={COLORS.surface} borderColor={COLORS.border} borderWidth={1} radius={4} padding={8}>
      {children}
    </panel>
  )
}
```

Do not write `<panel style={COLORS}>`. `style` is not a supported property.

<a id="wiki-section-12"></a>
### Control colors (appearance)

`button`, `input`, `slider` and the `Toggle`, `Slider`, `Select` and `Tabs` components below accept an `appearance` object:

- There are only eight color fields: `background`, `borderColor`, `color`, `placeholderColor`, `selectionColor`, `cursorColor`, `trackColor`, `thumbColor`.
- The state objects `hover`, `focused`, `pressed` and `disabled` accept the same eight fields only.
- When several states apply, later ones win: base → hover → focused → pressed → disabled.

Any other field is an error. See the example under "Common controls" below.

<a id="wiki-section-13"></a>
### Resource IDs

`resource`, `icon` and `font` take resource IDs such as `mymod:gui/panel`. File paths and URLs are not accepted. A missing resource is logged as an error; check it with `/nekojs error`.

<a id="wiki-section-14"></a>
## Coming from HTML/CSS

If you have a web design, translate it with this table:

| Web | NekoJS | Notes |
|---|---|---|
| `div` | `column` | Vertical by default |
| `display: flex; flex-direction: row` | `row` | `gap` maps directly |
| Absolutely positioned overlay | `stack` + child `anchor` | Nine anchor points only |
| `overflow: auto` | `scroll` | |
| `span`, `p`, text | `label` | |
| `img` | `image` | Replace `src` with a resource ID |
| `flex: 1` | `width="fill"` or `height="fill"` | |
| `@media` | `{ base, profiles }` | See tiers above |
| `z-index` | Child order in a `stack` | Later children draw on top |
| `margin` | Parent `gap`, `padding` or a `spacer` | |
| CSS Grid, animation, shadows, gradients | No equivalent | Combine `row`/`column` or use another approach |

<a id="wiki-section-15"></a>
## State

<a id="wiki-section-16"></a>
### Signal

`UI.createSignal(initial)` holds one value:

```tsx
const query = UI.createSignal('')

const root = UI.createRoot(() => (
  <column>
    <input
      id="query"
      value={query.get()}
      placeholder="Search"
      onChange={event => query.set(event.value || '')}
    />
    <label>{`Query: ${query.get()}`}</label>
  </column>
), host)
```

- `get()` reads the value. Signals read in the render function update the screen when they change.
- `set(value)` sets a new value.
- `update(old => new)` computes the new value from the old one.

<a id="wiki-section-17"></a>
### Store

Group related values in a store:

```javascript
const form = UI.createStore({ name: '', enabled: true })
form.set('name', 'Neko')
form.update('name', old => old + 'JS')
console.log(form.get('name'), form.snapshot())
```

Signals and stores live in memory only. After `/nekojs reload client` or a restart, they go back to their initial values. To keep data, see [Data and state](data-and-state_us).

<a id="wiki-section-18"></a>
### Batch updates

When you change several values at once, wrap them in `UI.batch` so the screen updates only once:

```javascript
UI.batch(() => {
  form.set('name', 'Neko')
  form.set('enabled', false)
})
```

<a id="wiki-section-19"></a>
## Events

| Property | When it fires |
|---|---|
| `onClick` | Click; also Enter or Space on a focused button |
| `onRelease` | Mouse button released |
| `onScroll` | Mouse wheel |
| `onKey` | Key press |
| `onTextInput` | Character typed |
| `onFocus` / `onBlur` | Focus gained / lost |
| `onChange` | The value of an `input` or `slider` changed |
| `onSubmit` | Enter pressed in an `input` |

Handlers receive an event object. Common fields are `type`, `target` (the element's id), `x`, `y`, `button`, `key`, `value` and `delta`.

Keep in mind:

- **An element needs an `id` to receive events**, and ids must be unique in the whole screen.
- For lowercase `input` and `slider`, `onChange` receives an event and `event.value` is a **string**. The capitalized `Slider` component below passes a number directly.
- There is no bubbling, `preventDefault()` or `stopPropagation()`.
- Disabled elements receive no events.
- An error in a handler is logged once; the rest of the screen keeps working.

<a id="wiki-section-20"></a>
## Components and lists

As a screen grows, split it into function components. A component is a plain function that takes props and returns JSX:

```tsx
function Card({ title, children }) {
  return (
    <panel background="#20252B" borderColor="#4C566A" borderWidth={1} radius={4} padding={8} gap={4}>
      <label color="#E5E9F0">{title}</label>
      {children}
    </panel>
  )
}

const items = [
  { id: 'a', name: 'Diamond', description: 'Expensive' },
  { id: 'b', name: 'Dirt', description: 'Plentiful' }
]

const root = UI.createRoot(() => (
  <scroll width="fill" height="fill">
    <column gap={6}>
      {items.map(item => (
        <Card key={item.id} title={item.name}>
          <label>{item.description}</label>
        </Card>
      ))}
    </column>
  </scroll>
), host)
```

Every list item needs a stable, unique `key`.

Define component functions outside the render function. Do not create a new function on every render.

To create elements in code, use `UI.element(type, props, key)`. It works the same as JSX, for example `UI.element(Card, { title: 'Diamond' }, 'a')`.

<a id="wiki-section-21"></a>
## Common controls

Import `Toggle`, `Slider` and `Select` from `nekojs/jsx-runtime`. They are controlled components: they keep no state of their own. Pass the current value on every render and update your signal in the callback.

Save this as `nekojs/client_scripts/controls.tsx` and run `/nekojs reload client`:

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

The summary starts as `enabled=true; volume=40; quality=balanced`. Try this:

- Click Enable volume. The slider is disabled or enabled with it.
- Drag the slider, or focus it and use the arrow keys, Home and End.
- Open the list and pick Fast. The summary shows `quality=fast`. Unavailable is greyed out.
- Type in the text field, select text, copy and paste.

Required properties:

- `Toggle`: `id`, `checked`, `onChange(boolean)`; optional `label`.
- `Slider`: `id`, `value`, `onChange(number)`; optional `min`, `max`, `step` (defaults 0, 100, 1).
- `Select`: `id`, `value`, `options`, `expanded`, `onExpandedChange(boolean)`, `onChange(string)`. Each option is `{ value, label, disabled? }`. Values must be unique and include the current value.

An expanded `Select` shows its list in place, up to six rows, scrolling if there are more. It is not a floating dropdown.

Components use some derived ids. For example, a `Select` with `id="quality"` also uses `quality:root`, `quality:options` and `quality:option:...`. Avoid these names for other elements.

<a id="wiki-section-22"></a>
### Text field and slider details

- `input` is single-line and has no IME candidate window. `maxLength` counts characters.
- Shortcuts: Ctrl+A selects all, Shift+arrows extend the selection, Ctrl+C / X / V copy, cut and paste (Cmd on macOS). Pasting removes line breaks and tabs.
- `slider` requires `min < max`, `step > 0` and `value` within range, or it reports an error. Dragging snaps to `step`. Dragging to an end, or pressing Home / End, lands exactly on `min` / `max`.

<a id="wiki-section-23"></a>
## Tabs, forms and dialogs

`Tabs`, `Dialog`, `validateForm` and `Validators` also come from `nekojs/jsx-runtime`.

Save this as `nekojs/client_scripts/forms-and-dialogs.tsx` and run `/nekojs reload client`. Remove the earlier examples first, or the screens will compete to open.

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

Try this:

1. Leave the name empty and click Review. An error appears and no dialog opens.
2. Quantities `0`, `65`, `abc` and `1.5` are all rejected.
3. Enter `Alex` and `3`, then click Review. The dialog opens. Cancel, Esc or a click outside closes it without changing the status.
4. Review again and click Confirm. The status becomes `Confirmed Alex x3`.
5. While the dialog is open, the Background action button behind it cannot be clicked.
6. The Preview tab shows a diamond icon. The Locked tab cannot be selected. Switch back to Profile and your input is still there.

The example only changes screen state. It never touches the world or the inventory.

<a id="wiki-section-24"></a>
### Tabs

- Requires `id`, `value` (the current tab), `tabs` and `onChange`.
- Each tab is `{ value, label, disabled?, render }`. Only the selected tab's `render` runs.
- Keep tab content state in outside signals so it survives switching tabs.
- Arrow keys, Home and End switch tabs and skip disabled ones.
- `Tabs` fills its parent by default. Next to other content, give it an explicit `height` as the example does.

<a id="wiki-section-25"></a>
### Form validation

`validateForm(values, rules)` returns `{ valid, errors }`. `errors` contains only fields that failed, each with a list of messages. It only checks values. It does not display errors or submit anything.

Built-in rules:

- `Validators.required(message?)`: must not be empty. A whitespace-only string counts as empty; `0` and `false` count as values.
- `Validators.minLength(n, message?)` / `maxLength(n, message?)`: string length.
- `Validators.numberRange(min, max, message?)`: number range. It does not check for whole numbers.

A custom rule is a function `(value, values) => message | null`, like the whole-number check in the example. Async validation is not supported.

<a id="wiki-section-26"></a>
### Dialogs

- `Dialog` requires `id`, `open` and `onOpenChange`. Optional: `title`, `width`, `height` (80% by default), `closeOnEscape` and `closeOnBackdrop` (both `true` by default).
- Esc or an outside click only calls `onOpenChange(false)`. The dialog closes when you set your signal to `false`.
- While a dialog is open, the screen behind it gets no clicks, scrolling or keys, and Tab moves focus only inside the dialog. Esc inside a dialog closes only the dialog, not the whole screen.
- If the content may not fit, put a `scroll` inside.

<a id="wiki-section-27"></a>
### Item icons

```tsx
<item item="minecraft:diamond" count={1} itemTooltip={true} />
```

- `item` is an item ID, not an item stack.
- `count` is a whole number from 1 to 99, default 1.
- `itemTooltip` defaults to `true` and shows the vanilla tooltip on hover. Set `tooltip="..."` to show your own text instead.
- It is only an icon, not an inventory slot. Nothing can be picked up and the inventory never changes.
- An unknown item ID reports `NEKO-6012` and the screen keeps its previous state.

<a id="wiki-section-28"></a>
## Multi-window workspace

`createWorkspace` from `nekojs/jsx-workspace` puts several windows in one screen. They can be dragged, resized and minimized. All of them live inside one Minecraft screen; no OS windows are created.

Save this as `nekojs/client_scripts/workspace.tsx`, remove other examples that open screens, and run `/nekojs reload client`:

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

Try this: drag a title bar; drag an edge or corner to resize; minimize Notes and restore it from the taskbar at the bottom (your text is still there); maximize and restore; close one window and the others stay.

Keep in mind:

- `createWorkspace` creates and binds the root itself. Do not call `UI.createRoot` or `bindRoot` again.
- Keep each window's state in signals outside its render function, as usual.
- Elements in different windows may share an `id`. Within one window, ids must be unique.
- Minimizing or hiding does not pause state updates and does not save anything.

<a id="wiki-section-29"></a>
### Controlling windows

| Method | What it does |
|---|---|
| `add(id, render, config?)` | Adds a window |
| `update(id, patch)` | Changes window settings, including `render` |
| `activate(id)` | Brings a window to the front; restores it first if minimized |
| `show(id)` / `hide(id)` | Shows / hides a window; content is kept |
| `minimize(id)` / `maximize(id)` / `restore(id)` | Minimizes / maximizes / restores |
| `resize(id, rect)` | Changes position or size |
| `closeWindow(id)` | Removes the window for good |
| `open()` / `close()` | Opens / closes the whole screen |
| `snapshot()` | Reads the state of all windows |

Do not call these methods inside a window's render function.

<a id="wiki-section-30"></a>
### Window settings

The third argument of `add`:

- `title`: the title.
- `rect`: `{ x, y, width, height }`, the normal position and size. Default `{ x: 16, y: 16, width: 240, height: 160 }`.
- `minWidth`, `minHeight`, `maxWidth`, `maxHeight`: resize limits. The minimum cannot go below 40 × 20.
- `resizable`, `minimizable`, `maximizable`: allow resizing, minimizing, maximizing. All `false` by default.
- `titlebarHeight`, `titlebarButtonWidth`: title bar height and button width, both 20 by default. `titlebarTitle: false` hides only the title text. A title bar height of 0 hides the buttons too, so you must provide your own way to restore the window.
- `responsiveRect`: position and size that follow the screen size. Each field can be a number, a percentage, or `{ base, profiles }`. Percentages are relative to the workspace. Once the user drags or resizes the window by hand, this setting is cleared and the window stays where it is.

<a id="wiki-section-31"></a>
### Where minimized windows go

The `minimizedPresentation` option of `createWorkspace`:

- `"none"` (default): minimized windows just disappear. Provide your own restore buttons.
- `"taskbar"`: a bar along the bottom, `taskbarHeight` tall (default 28). The space is reserved even when nothing is minimized.
- `"floating"`: a small panel in the bottom-left corner that takes no workspace space.
- A function: draw your own restore panel.

To switch, replace only the `const workspace = createWorkspace(...)` line in the example. Do not add a second workspace:

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

The custom function receives `{ windows, restore, activate }`, where `windows` are the minimized windows. It must return a `panel` with `width`, `height` and `anchor` set, or `null`. Buttons in the panel need an `id` too, and it must not clash with ids inside the windows.

<a id="wiki-section-32"></a>
## Translating UI text

Client scripts can use `I18n` to get translations in the player's current language:

```javascript
const done = I18n.translate('gui.done')
const hello = I18n.translateWithFallback('mymod.hello', 'Hello, %s', 'Alex')
```

- `I18n.translate(key, ...args)` returns the translated string, or the key itself if it is missing.
- `I18n.translateWithFallback(key, fallback, ...args)` uses the fallback text when the key is missing.
- `I18n.has(key)` checks whether a translation exists.
- `I18n.languageCode` is the current language, such as `en_us`.

`I18n` works in client scripts on every platform, not only NeoForge 26.x.

Strings you already computed do not update when the language changes; reopen the screen. When a server script sends messages to players, use `Text.translatable(...)` instead.

<a id="wiki-section-33"></a>
## Closing and reloading

- Esc closes the screen. To prevent that, set `closeOnEscape={false}` on `<screen>`.
- `host.close()` closes it from a script and returns `true` on success.
- `/nekojs reload client` closes screens opened by the old scripts.
- If the reload fails, the current screen stays as it is and the last working scripts stay in use.
- A screen only appears once `client_scripts` finished loading successfully.

The root returned by `UI.createRoot` also has these methods. You rarely need them:

| Method | What it does |
|---|---|
| `refresh()` | Re-renders manually |
| `layout()` | Reads the current layout, for debugging |
| `dispatch(id, eventName, event)` | Fires an element's event from code, e.g. `root.dispatch('increment', 'click', {})` |
| `isDisposed()` | Whether the screen has closed |

<a id="wiki-section-34"></a>
## Common errors

| Code | Cause | Fix |
|---|---|---|
| `NEKO-7001` | A UI object left over from old scripts was used | Create a new host and root after reloading |
| `NEKO-7003` | The host was not created from a client script | Call `ClientUI.screen` from `client_scripts/` |
| `NEKO-7004` | The UI was changed from another thread | Change the UI from client callbacks |
| `NEKO-7007` | Render, layout or an event handler failed, including unsupported properties | Read the details in `/nekojs error` |
| `NEKO-7008` | Invalid `appearance` | Use only the eight color fields listed above |
| `NEKO-7011` | Invalid slider range, step or value | Check `min`, `max`, `step` and `value` |
| `NEKO-7012` | Invalid dialog properties | Check `id` and `title` |
| `NEKO-7013` | Invalid workspace or window settings | Check window ids and the `add` settings |
| `NEKO-7014` | Two roots bound to one host, or an invalid region size | Bind one root per host |
| `NEKO-6012` | Unknown item ID | Check the `item` of `<item>` |

When render or layout fails, the screen keeps its last good state and the error shows up in `/nekojs error`. The full list is in [Error and log reference](error-reference_us).

<a id="wiki-section-35"></a>
## Embedding in another screen

`ClientUI.region(x, y, width, height)` creates a host for embedding. It does not open a screen. Instead, your existing screen forwards drawing and input to it. This is mainly for Java plugin authors and custom screens.

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

This code alone shows nothing. The outer screen must call the region's methods at the right time:

- Drawing: `render(graphics, mouseX, mouseY)`.
- Input: `mouseClicked`, `mouseDragged`, `mouseReleased`, `mouseScrolled`, `keyPressed`, `charTyped`. Pass the outer screen's coordinates; do not subtract the region origin. Each method returns `true` when it handled the input; then the outer screen must not handle the same input.
- On resize, call `setRegion(x, y, width, height)`. For focus, use `setFocused` / `isFocused`.
- When the outer screen closes, call `dispose()`.

<a id="wiki-section-36"></a>
## Other ways to build UI

JSX screens suit settings pages, forms and lists, anything you click and type into. For other needs:

| Need | Use |
|---|---|
| Settings pages, forms, lists, text fields | JSX screens (this page) |
| Text, bars or icons on the HUD | `ClientEvents.hud` / `hudRender` |
| Hints drawn over an existing screen | `ClientEvents.screenRender` |
| Outlines and paths in the world | `ClientEvents.worldRender` |
| Vanilla-style container screens and inventory slots | A Java plugin |

HUD and world drawing are per-frame callbacks. You handle coordinates yourself, and there is no layout or input:

```javascript
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text('Quest in progress', 14, 14, 0xFFFFFFFF)
})
```

See [Client rendering and post effects](client-rendering_us).

<a id="wiki-section-37"></a>
## Not supported yet

- JSX screens on Fabric or NeoForge 1.21.1.
- Containers, inventory slots and inventory sync.
- CSS, `className`, `style`, Grid, animation, shadows, gradients.
- React hooks.
- New lowercase tags, or vanilla widgets as tags.
- Drag and drop, virtualized lists, IME candidate windows, multi-line text fields.

<a id="wiki-section-38"></a>
## Related pages

- [TypeScript and JSX](typescript-and-jsx_us): JSX syntax and the automatic runtime.
- [Client rendering and post effects](client-rendering_us): HUD, world drawing and post effects.
- [Event reference](event-reference_us): `ClientEvents`, `KeyBindEvents`.
- [Platform and compatibility](platform-compatibility_us): what each platform supports.

<!-- wiki-nav -->

---

[Previous: TypeScript and JSX](typescript-and-jsx_us) · [Contents](Home) · [Next: Client rendering and post effects](client-rendering_us)
