# JSX client UI

## Choose the supported owner and runtime

Use CLIENT scripts with the installed profile's declarations. The managed `ClientUI` Screen binding is available on **NeoForge 26.x only**, not Fabric, NeoForge 1.21.1 or dedicated servers. TSX compilation alone does not create that binding. Use the configured automatic JSX runtime `nekojs/jsx-runtime`; classic factories can build arbitrary data but do not acquire native Screen behavior.

Import `UI`, the controlled components `Toggle`, `Slider`, `Select`, `Tabs`, `Dialog`, and the synchronous helpers `validateForm`, `Validators` from that runtime as needed. These are NekoJS exports, not HTML tags, React hooks or native Minecraft Widgets. The 14 lowercase primitives are `screen`, `panel`, `row`, `column`, `stack`, `scroll`, `label`, `button`, `input`, `slider`, `image`, `item`, `dialog`, `spacer`; `UI.primitives()` reports the installed set. Query matching `@manual` declarations when structured indexing is incomplete.

## Run a complete controls screen

Copy [the exact shipped source](../examples/client/controls.tsx) to `nekojs/client_scripts/authoring-examples/controls.tsx` in a disposable supported client. Run `/nekojs reload client`; the screen opens after successful CLIENT execution. Signals are created **outside** the render callback so reconciliation does not reset values.

### [examples/client/controls.tsx](../examples/client/controls.tsx)

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

**Observe:** the summary starts at `enabled=true; volume=40; quality=balanced`. Click the toggle to disable/re-enable the slider. Drag the slider or focus it and use arrows/Home/End; the number changes in steps of five. Expand the selector, choose Fast, and check `quality=fast`; Unavailable is disabled. Focus the text input, type/paste text longer than its viewport, move the caret, select, copy and cut. The field remains single-line and follows the caret horizontally; the outer scroll handles a small screen.

**Cleanup:** close the Screen, remove the copied file and reload CLIENT. Reload creates fresh signals and replaces the old generation/root; it is not persistence. Use [the cookbook](scripting-examples.md) for this and the unchanged minimal counter scenario. Compiler/runtime tests with a fake platform host establish contracts, not visible Minecraft rendering, clipboard integration or focus acceptance.

## Run tabs, a validated form and local confirmation

Install only [forms-and-dialogs.tsx](../examples/client/forms-and-dialogs.tsx) at `nekojs/client_scripts/authoring-examples/forms-and-dialogs.tsx`, on the same supported CLIENT profile. Do not install multiple auto-opening examples together: each opens its own Screen. Run `/nekojs reload client`.

### [examples/client/forms-and-dialogs.tsx](../examples/client/forms-and-dialogs.tsx)

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

**Observe:** Profile starts with an empty name, quantity `1`, `Nothing confirmed` and `Background clicks: 0`. Review shows `Enter a name` without opening a dialog. Try quantity `0`, `65`, text and `1.5`; invalid input stays editable and produces errors on Review. Enter `Alex` and `3`: Review opens `Confirm local profile` with `Alex x3`. Cancel or a supported dismissal leaves the status unchanged; Confirm sets `Confirmed Alex x3`. Confirmation only updates a local signal, never gives items or sends a server request. Errors refresh on Review, not on every keystroke.

Switch to Preview for a read-only diamond icon and its tooltip. Locked cannot be selected. Switching away unmounts the old pane, but input signals allocated outside render retain their values when Profile is selected again. The Background action counter is an observable target for modal-blocking checks, not a game action.

**Cleanup:** close the Screen, remove this copied file and reload CLIENT. Fresh execution resets all eight signals. No disk persistence, inventory edits or network authority is implied. The human checklist below remains pending until a real supported client is tested.

## Native input and slider contracts

- `input` is a single-line editor. `maxLength` counts Unicode code points, not UTF-16 units or pixels. Control/Super+A selects all; Shift+Left/Right/Home/End extends the selection. Clipboard C/X/V shortcuts accept Control or Super; cut writes before deleting, and a clipboard failure preserves the input. Paste discards ISO control characters (including CR/LF, tabs and DEL) and unpaired surrogates without splitting a valid supplementary character. It does not provide IME composition or multiline editing.
- Long input uses a pixel horizontal offset shared by caret following, mouse hit testing, selection and clipping; it clamps again when width/value shrinks. It does not wrap into multiple lines.
- Lowercase `slider` takes numeric `value`, `min`, `max`, `step` (defaults 0, 0, 100, 1). Require finite bounds/span/step ratio, `min < max`, `step > 0`, and an in-range value. Interior native interaction snaps to `min + round((raw - min) / step) * step`, clamped to the bounds; dragging to either endpoint and Home/End reach the exact bound even if it is off-step. Supplying a value does not silently snap it.
- Native slider arrows Left/Down decrease by one step, Right/Up increase; Home/End choose the bounds. Primary-button dragging captures that slider until release. This is not a generic drag-and-drop API. Disabled controls do not handle input.
- Raw primitive `onChange` receives a frozen event whose `value` is a **string**. Convert deliberately for a raw numeric slider. The uppercase `Slider` wrapper delivers a **number** instead; do not confuse the two callback shapes.

## Controlled components and stable IDs

Every component requires a root-unique `id`; allocate state once outside render and pass the current values on every render. Callbacks request an update; the components do not own hidden signals.

| Component | Controlled contract |
|---|---|
| `Toggle` | `checked: boolean`, `onChange(boolean)`; optional `label` / `text` |
| `Slider` | `value: number`, `onChange(number)`; optional `min`, `max`, `step` |
| `Select` | `value: string`, `options: readonly { value, label, disabled? }[]`, `expanded: boolean`, `onExpandedChange(boolean)`, `onChange(string)` |

Select values must be unique and the selected value must exist; an empty options list permits only `value=""`. It renders an **inline** list, at most six 20-pixel rows with scrolling, not a floating native popup. Reserve its derived IDs: `id:root`, `id:options`, `id:option:` plus `encodeURIComponent(value)`; its trigger uses the base ID. Toggle and Slider use their base IDs directly. Do not create another node with any reserved ID. Disabled nodes are also guarded against manual `root.dispatch`; dispatch is not an authorization bypass or a substitute for native input acceptance.

## Tabs, validation, dialogs and read-only items

**Tabs:** import `Tabs` and pass `id`, controlled `value`, `tabs: [{ value, label, disabled?, render: () => ... }]` and `onChange(string)`. Tabs must be nonempty; values are unique, the selected value must exist, and disabled choices cannot be activated. The selected header uses a `[Label]` text marker; selection does not force an appearance override, and there is no roving-focus API. Only the selected renderer runs and its pane is mounted; switching panes removes the old nodes, not the external signals. Do not allocate persistent form state inside the pane renderer. Default width/height are fill; give the component an explicit embedding height when sharing a column. Reserve `id:tabs`, `id:tab:` plus `encodeURIComponent(value)`, and `id:content` within its root. Keyboard navigation uses arrows/Home/End and skips disabled tabs; it requests a value change rather than owning selection state.

**Validation:** call `validateForm(values, rules)` explicitly when your workflow needs it. It returns a deeply frozen `{ valid, errors }`; `errors` maps only failed field names to readonly arrays of messages, in field/rule order. The helper does not render labels, intercept submission, change values or contact a server. Values must be a plain record of flat string/boolean/null/finite-number scalars; explicit undefined, nested values and asynchronous validation are outside this API. A missing field named by rules is passed to its rules as undefined. Built-ins are `Validators.required(message?)`, `minLength(n, message?)`, `maxLength(n, message?)`, `numberRange(min, max, message?)`. Required rejects missing/null/blank strings but accepts `0` and `false`. Length rules require strings and count JavaScript UTF-16 code units, unlike native input maxLength code points. Number range accepts finite numbers and nonblank numeric strings using JavaScript `Number` semantics; it does not enforce integers or normalize input. All rules run and a field can have multiple messages; even an empty returned string counts as an error. The example adds a custom integer rule. Custom rules receive `(value, frozenValues)` and return a message or `null`/`undefined`; promises and other result types are invalid. Keep validators synchronous and deterministic.

**Dialog:** import `Dialog` with `id`, controlled `open`, `onOpenChange(boolean)`, optional `title`, `children`, `width`, `height`, `closeOnEscape`, `closeOnBackdrop`. Reserve `id:panel` and `id:content` within its root. Closed returns no nodes. Open creates a genuine modal `dialog` portal over the safe viewport; its centered content panel defaults to 80% width and height. Size props affect the panel, not the viewport portal. Put an explicit `scroll` child inside when content can exceed it. Both close flags default true. Dismissal requests `onOpenChange(false)`; the caller must update the signal. Returning without changing `open` keeps the modal open. This is not a styled panel that merely looks modal.

The last visible dialog in depth-first tree order is the top modal. Common dispatch rejects background targets, including queued events after a modal opens; native input routes pointer/drag/release/wheel, keys, text, Tab and narration to the top modal subtree. Escape is consumed before Screen closure, even when dismissal is disabled. Only primary clicks outside the content panel request backdrop dismissal. Native transitions cancel captures, focus an enabled modal descendant and restore a still-retained, visible, enabled prior focus when the modal is removed. A parent scroll or clip does not confine the portal. Close/reload removes its generation-owned modal state; failed candidates preserve the active tree. These contracts still require the real-client checklist below.

**Read-only item:** `<item item="minecraft:diamond" count={1} itemTooltip={true} />` takes a resource-ID string, not an `ItemStack` or component-string descriptor. Count defaults to 1 and must be an integer from 1 through 99; the display limit is not an inventory stack-limit promise. `itemTooltip` defaults true; an existing custom `tooltip: string` overrides the automatic vanilla tooltip. A known non-air item is resolved once per staged native create/update; invalid or missing IDs reject the candidate rather than replacing a valid tree. The cached detached visual paints a centered 16×16 icon with a count overlay; larger bounds do not turn it into a Slot. It has no inventory, click-to-give, container, persistence or network authority. Tooltips are restricted to the topmost eligible hovered item, not background content under a modal. Native item rejection reports `NEKO-6012`; common layout may reject props earlier through `NEKO-7007`.

## Pending real-client acceptance checklist

Run the complete forms example separately on each supported NeoForge 26.x client node. Record the exact node, loader, GUI scale and viewport; fill in actual results and a maintainer conclusion. Every row below is **not run**, not a passing test or an instruction to record success without observation. Platform-fixture checks cannot establish these native effects.

| Check | Expected observation | Status |
|---|---|---|
| Invalid form | Blank name, out-of-range/text/decimal quantity show errors; no modal opens | Not run |
| Valid confirmation | Alex/3 opens the modal; Cancel leaves status unchanged; Confirm sets `Confirmed Alex x3` locally | Not run |
| Tabs | Pointer and keyboard skip Locked; returning to Profile retains external field values | Not run |
| Genuine modal boundary | Background action counter, underlying scroll and slider captures do not respond to pointer/wheel/key/manual background dispatch while open | Not run |
| Dismissal and focus | Tab/Shift+Tab stay in modal; Escape/backdrop follow flags, do not close the Screen, and closure restores eligible prior focus | Not run |
| Item visual and tooltip | Preview shows the diamond/model/count and vanilla tooltip, custom tooltip override works, no inventory changes | Not run |
| Viewport and clipping | Short windows, GUI scale changes and nested scroll preserve the viewport portal, reachable content and item hover bounds | Not run |
| Lifecycle and retained failure | Close/replacement/reload remove stale callbacks/modal/capture; invalid candidates retain the previous usable tree | Not run |

Maintainer conclusion: **pending**. OS clipboard/input, IME behavior, actual GPU rendering, tooltips and multiplayer acceptance remain separate from common-engine or in-memory native fixtures. The forms example intentionally has no slider; use the earlier controls example for the slider/clipboard rows of a broader acceptance pass.

## Reuse a finite appearance object, not CSS

`button`, `input`, `slider` and these wrappers accept `appearance`. Its only color keys are `background`, `borderColor`, `color`, `placeholderColor`, `selectionColor`, `cursorColor`, `trackColor`, `thumbColor`; keys irrelevant to a primitive do not turn it into another primitive. The optional `hover`, `focused`, `pressed`, `disabled` objects contain only those color keys, not nested states. Precedence is base → hover → focused → pressed → disabled. Unknown keys, nulls, responsive color objects and nested state maps are invalid.

Colors accept 32-bit signed/unsigned ARGB integers, basic named colors, `#RGB`, `#RRGGBB`, `#AARRGGBB`; eight digits are **ARGB**, not CSS RGBA. Use ordinary objects and function components to share a theme as above. There is no `style`/`className`, CSS cascade, arbitrary CSS color parser, HTML attributes or DOM behavior. Layout props remain separate from this color-only object.

## Lifecycle, validation and current gaps

Use `ClientUI.screen` → `UI.createRoot` → `host.bindRoot` → `host.open` on the CLIENT owner. A successful CLIENT reload retires old roots, closures and Screens; failed candidates preserve the active generation. Closing/replacing the Screen cleans up managed callbacks. Do not store stale VNodes, Screen hosts or root handles for another generation. Report diagnostics with the installed source/declarations; a type-correct tree does not prove layout or input behavior. Relevant codes are `NEKO-7007` for runtime-phase/host failures (including clipboard failures), native `NEKO-7008` for invalid control appearance, and native `NEKO-7011` for an invalid slider range/value. Common JSX validation can reject malformed props earlier in the layout phase and report `NEKO-7007` with that diagnostic instead of a native code; do not promise one code for every invalid prop. The existing `NEKO-7004` remains the owner-thread diagnostic.

For HUD/world drawing and post-effect builders use [client rendering](client-rendering.md), not JSX tags. Missing capabilities remain explicit: no Fabric/1.21.1 ClientUI parity; no server containers, Slots or inventory/network authority; no generic drag/drop, virtualized list implementation or arbitrary native Widgets; no React/DOM/CSS or IME composition contract. The inline Select is not a native popup. Real GPU appearance, OS clipboard behavior, caret clipping/focus on supported clients, IME interaction and multiplayer acceptance need separate live testing and have not been established by fixture-only verification.
