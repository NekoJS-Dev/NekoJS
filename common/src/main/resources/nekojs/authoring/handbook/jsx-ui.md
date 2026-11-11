# JSX client UI

## Choose the supported owner and runtime

Use CLIENT scripts with the installed profile's declarations. The managed `ClientUI` Screen binding is available on **NeoForge 26.x only**, not Fabric, NeoForge 1.21.1 or dedicated servers. TSX compilation alone does not create that binding. Use the configured automatic JSX runtime `nekojs/jsx-runtime`; classic factories can build arbitrary data but do not acquire native Screen behavior.

Import `UI`, the controlled components `Toggle`, `Slider`, `Select`, `Tabs`, `Dialog`, and the synchronous helpers `validateForm`, `Validators` from that runtime as needed. These are NekoJS exports, not HTML tags, React hooks or native Minecraft Widgets. The 16 lowercase primitives are `screen`, `panel`, `row`, `column`, `stack`, `scroll`, `label`, `button`, `input`, `slider`, `image`, `item`, `dialog`, `spacer`, `workspace`, `window`; `UI.primitives()` reports the installed set. Query matching `@manual` declarations when structured indexing is incomplete.

## Infer state and callback contracts

Probe extracts callback signatures from the canonical runtime types. A root or workspace render is `() => NekoUiRenderable`; a function component is `NekoUiComponent<Props>`, receiving its declared props and returning VNodes, text, numbers, booleans, null/undefined, or arrays of renderable values. Arbitrary objects and promises are not render results. Event handlers receive their event type; queued actions have no arguments and their return value is ignored.

Pass `ClientUI.screen(...)` or `ClientUI.region(...)` directly to `UI.createRoot`; no casts or local declaration augmentation are required. Probe retains numeric `width`/`height` fields on native measurement and viewport results alongside their Java Map methods. Host implementations must accept `layout(tree, viewport, snapshot, publish?)`; the runtime supplies the viewport and snapshot on every layout call.

Java plugin authors can use `@ScriptOutput("TypeScript expression")` when reflection cannot describe the actual returned shape. This changes only TypeScript declarations, including Bean getters and inherited members; explicit Probe type edits take precedence. Use resolvable inline imports for external types and an intersection with the Java type when its methods remain available. The annotation does not discover dependencies or convert runtime values, and ordinary Maps keep their reflected types.

Signal updates are `(current: T) => T`. Stores infer keys and value types from their initial state, so each updater must return the type for its selected key:

```ts
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
count.update(current => current + 1);
const state = UI.createStore({ count: 0, title: "Ready" });
state.update("count", current => current + 1);
state.update("title", current => current.toUpperCase());
```

Returning a string from the numeric updater is a type error. Root/component render and state updaters do not await promises. `UI.batch(() => value)` preserves the returned value's type. A render with no return statement is `void`; use an explicit `null` or `undefined` to render nothing. For a store with keys added dynamically, use `UI.createStore<Record<string, unknown>>({})` and narrow unknown values before using them. These declarations tighten authoring checks without changing runtime scheduling or storage.

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

## Cooperate with a caller-owned GUI region

`ClientUI.region(x, y, width, height)` creates the same managed adapter without creating or navigating a Screen. Use integer GUI coordinates and positive, representable extents. Creation/root binding still require the supported managed CLIENT context and owner thread. This is a cooperative seam for a caller Screen or non-Screen draw/input owner, not an automatic third-party mount, Widget tag or paint-after overlay with implied interaction.

The following construction belongs inside that integration. It is **not** a standalone auto-opening script: the caller must retain the adapter and forward its actual frame/input/lifecycle. No listeners are installed for it.

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

Do not call `ClientUI.screen`/`host.open` to attach this region to the caller. Region `open()` is optional and does not navigate or make the caller visible. Rendering/input are admitted only for the committed generation. The caller decides rendering order and global focus/input order; do not deliver an event to both owners after the region consumes it.

| Caller responsibility | Adapter method |
|---|---|
| Paint in the real GUI frame | `render(GuiGraphicsExtractor, int mouseX, int mouseY)` |
| Pointer press | `mouseClicked(double x, double y, int button, int modifiers)` |
| Captured motion/release, even outside bounds | `mouseDragged(double x, double y, int button)`, `mouseReleased(double x, double y, int button)` |
| Wheel | `mouseScrolled(double x, double y, double delta)` |
| Keyboard/text | `keyPressed(int key, int modifiers)`, `charTyped(int codepoint)` |
| Caller layout/resize | `setRegion(int x, int y, int width, int height)` |
| Focus chain | `setFocused(boolean)`, `isFocused()` |
| Retryable user close versus terminal detach | `close(): boolean`, `dispose(): void` |

Pointer coordinates are caller GUI coordinates, not manually pre-subtracted local coordinates. Drawing and hit testing translate through the same region origin; drawing restores caller pose/scissor state. Input methods return consumption booleans. An outside click releases region keyboard focus, while an already captured drag/release must still be forwarded outside the region. Without a modal, Tab at an edge returns false and clears region focus so the caller can continue its focus chain; a modal retains its own boundary. `setFocused(false)` clears keyboard focus, not an ongoing pointer capture.

On resize, update the existing adapter instead of installing a second input chain. `close()` succeeds only when ordinary root close returns literal true; false remains retryable. Detach/removal must call `dispose()` to force terminal release. Region close/dispose never closes the caller Screen, and late cleanup must not navigate a replacement Screen. Non-Screen callers need the same real GUI extractor, owner thread, viewport, ordered input/focus and detach responsibilities; there is no automatic HUD subscription, generic renderer capability negotiation or third-party integration implementation supplied by this example.

## Run one desktop with three logical windows

Use the separate module `nekojs/jsx-workspace`, not a second JSX runtime. Install only [workspace.tsx](../examples/client/workspace.tsx) at `nekojs/client_scripts/authoring-examples/workspace.tsx` and run `/nekojs reload client` on the supported NeoForge 26.x CLIENT profile. A Workspace is one managed root inside one host, not multiple simultaneous Minecraft Screens or operating-system windows.

### [examples/client/workspace.tsx](../examples/client/workspace.tsx)

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

**Observe:** Notes, Tools and Preview overlap above a taskbar. Notes saved and Tools clicks start at zero, update independently and appear in Preview. Editing one input must not edit the other. Edit Notes, minimize it using the frame control, then click its titled Notes taskbar button: text, counters and keyed content should survive. Try edge/corner resizing, maximize, minimize while maximized and restore. Notes uses responsive normal bounds plus a 24-high titlebar and 18-wide buttons; Tools hides only its title text. Closing one window leaves the others usable; closing the last leaves the desktop and reserved taskbar by default. GUI logical work-area constraints can change shown positions/sizes. These are observation tasks, not a claim of native or physical-client acceptance.

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

Use a fresh managed host for each controller. Rebinding the identical guest root handle is idempotent; `bindRoot` rejects another handle, and layout rejects a mismatched root ID before publication (`NEKO-7014`). These checks do not make a host shareable by separately constructed roots with matching ID text. There may be at most one workspace, either the root or a direct child of the root `screen`; its direct children must be windows. A window is never nested under another window, and at most one is active. Window geometry and its ancestors use logical coordinates, not `coordinateSpace="design"`. `show(id)` raises and activates the window. `hide` retains content identity and signal-driven updates while blocking its input; it is not background-work suspension. `closeWhenEmpty: true` requests ordinary host close after removing the last window; a failed close is not proof of disposal. Controller mutations during root publication (including nested component expansion) are rejected; keep renderers pure.

Allocate business signals outside window renderers; hiding/removing content does not serialize signals to disk. Window content has private keyed identity scopes. Explicit descendant IDs are also scoped per window after component expansion, so different windows may reuse a local ID; duplicates within the same window still fail. Window IDs themselves remain exact. For manual dispatch, obtain the actual opaque expanded ID from published layout/native props; never construct or depend on its serialized format. Outside windows, IDs remain root-unique. Titlebar/frame routing is managed by the shared host. Native capture, active-window focus and opaque-window occlusion are separate from ordinary JSX child order. Do not register independent global mouse/key listeners for each window or assume direct dispatch proves native routing.

**Cleanup:** close the Screen, remove the script and reload CLIENT. A new generation creates a new desktop and four fresh signals. Window management remains inside one managed root and host. It does not provide snapping, layout persistence, arbitrary Screen embedding, a native Widget bridge, Slot/menu protocol or a measured performance optimization. GPU, physical input and accessibility acceptance remain pending.

## Native input and slider contracts

The Java JSX host owns long-text preparation and painting. It reuses bounded text layouts across unchanged measurements and scrolling, invalidates them on font-resource reload, and releases them with the host. Text/font/size/width/wrapping changes select a fresh layout. Painting submits the visible line window instead of every off-screen stack line. Multiline advances stay in base-font units before the pose scale, so fractional GUI scales do not accumulate rounded gaps or hide the final lines. Ordinary scripts can use `label` inside `scroll`; they do not need manual line paging or a JavaScript measurement cache. Full text remains available to author-controlled copy actions.

Native `scroll` nodes automatically show a slim overlay scrollbar whenever their content overflows. Its thumb reflects the visible fraction and current offset; drag it to seek or click the track to move one viewport. A row-direction scroll uses a horizontal bar; other scrolls use a vertical bar. The overlay does not reserve layout space, so leave content padding if text reaches the trailing edge. Nested bars control their own scroll, respect clipping and modal boundaries, and cancel capture when the node becomes unavailable or overflow disappears. No extra script nodes or per-frame updates are required.

On NeoForge 26.1.2 and 26.2.0, the built-in error dashboard also uses JSX. Its canonical UI is the bundled `nekojs/builtin-ui/error-dashboard.tsx` resource, outside player script discovery. It explicitly imports `UI` from `nekojs/jsx-runtime` and exports its factory as a native ESM module; its automatic JSX runtime imports resolve only to fixed in-memory bundled resources. No global `UI` or classic JSX factory is installed. Localization uses the same CLIENT `I18n` Binding as ordinary scripts: `translate`, `has`, `translateWithFallback`, and read-only `languageCode` / `rightToLeft`, described in [bindings and adapters](bindings-and-adapters.md). Players can always open the dashboard with `/nekojs view_all_errors`, including an empty state when there are no errors; `/nekojs error` remains a chat summary. Passive snapshot updates do not open a closed dashboard. A root-owned diagnostic session survives ordinary CLIENT reload success or failure; closing the Screen releases that session. Existing error snapshots, clipboard verification and checked asynchronous VS Code actions remain native operations. If the bundled UI fails, the existing native dashboard is the fallback. This internal session is not a public way to bypass the generation checks on `ClientUI.screen` or `ClientUI.region`; unsupported nodes keep their existing error presentation.

- For proportional layout, give `UI.createRoot` a viewport containing the real logical `width`/`height` and a paired `designWidth`/`designHeight`. Mark each participating primitive with `coordinateSpace="design"`; this setting is per node, not inherited. Numeric layout sizes and label text scale by `min(contentWidth / designWidth, contentHeight / designHeight)`. Native buttons, input text/selection/caret and slider details use the same scale for drawing and pointer positioning. `logical` controls keep their usual Minecraft GUI size. A host resize reports logical dimensions, so a root forwarder must preserve the design dimensions when forwarding `resize`; no alternate navigation layout is required.
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

## Pending composition and workspace acceptance

These are live-client acceptance tasks, not passing automated results. Record node, loader, GUI scale, viewport and maintainer judgment. All rows are **not run**.

| Check | Expected observation | Status |
|---|---|---|
| Scoped composition | Reused component-local keys remain independent across sibling windows and roots; duplicate direct keys fail without losing the usable tree | Not run |
| Re-entrant update | A state change requested while rendering does not publish stale output or loop without a bound | Not run |
| Borrowed region and caller input | Region drawing and hit tests use the same translated, clipped rectangle; native caller controls still work; each event is consumed at most once | Not run |
| Borrowed focus and capture | Focus handoff/edge Tab cooperate with the caller; captured drag/release stays coherent outside region bounds; modal input cannot leak behind it | Not run |
| Ownership and release | Resize/repeated init do not duplicate listeners; normal close failure remains retryable; detach/reload disposes only the owned session, never a newer or caller-owned Screen | Not run |
| Three-window stacking | Exposed background windows activate; foreground blank content and tooltips occlude the background; a click increments at most one counter | Not run |
| Window drag and viewport | Titlebar drag retains capture outside its initial bounds; GUI scale/viewport changes keep titlebar/close controls reachable; actual layout agrees with hit testing | Not run |
| Minimize and restore | Edit Notes, minimize it, verify hidden controls reject input, then restore via its titled presentation button; text, counters and expanded node identity survive; minimizing a maximized window restores maximized first | Not run |
| Resize and maximize | Opt-in edge/corner resize, min/max limits, tiny work areas and maximize/restore keep chrome/content hit tests aligned; manual normal move/resize clears responsive policy while maximize alone preserves it | Not run |
| Chrome and responsive layout | Check profile tiers 1–6, GUI scale changes, custom integer titlebar metrics, hidden title text and zero-height chrome with a programmatic restore path | Not run |
| Minimized presentations | Taskbar, floating and replacement panel restore the correct stable window; taskbar reserves its work area, overlays do not; clicks outside overlay bounds still reach eligible windows | Not run |
| Window focus and isolation | Only eligible content in the active window receives keyboard input; hiding/closing one window leaves the others usable and does not recreate their external state | Not run |

Maintainer conclusion: **pending**. Cooperative regions need a real caller that forwards the declared render/input/lifecycle methods. The standalone Workspace example does not by itself verify borrowed-Screen or non-Screen integration.

## Reuse a finite appearance object, not CSS

`button`, `input`, `slider` and these wrappers accept `appearance`. Its only color keys are `background`, `borderColor`, `color`, `placeholderColor`, `selectionColor`, `cursorColor`, `trackColor`, `thumbColor`; keys irrelevant to a primitive do not turn it into another primitive. The optional `hover`, `focused`, `pressed`, `disabled` objects contain only those color keys, not nested states. Precedence is base → hover → focused → pressed → disabled. Unknown keys, nulls, responsive color objects and nested state maps are invalid.

Colors accept 32-bit signed/unsigned ARGB integers, basic named colors, `#RGB`, `#RRGGBB`, `#AARRGGBB`; eight digits are **ARGB**, not CSS RGBA. Use ordinary objects and function components to share a theme as above. There is no `style`/`className`, CSS cascade, arbitrary CSS color parser, HTML attributes or DOM behavior. Layout props remain separate from this color-only object.

## Identity, serialized updates and terminal disposal

Function components have private identity scopes based on function identity plus their outer key or unkeyed position. Reused local child keys in separate component instances do not collide. Unkeyed fragments/arrays remain transparent; duplicate direct sibling keys still fail. Public `key` values are not rewritten; expanded public IDs must be unique within the root. Reuse a stable component function; allocating a new function type on every render changes identity.

Owner-thread re-entry into `root.refresh()` returns `"queued"` and requests serialized follow-up work, not a nested commit. Only stable candidates publish; work is bounded to 32 passes, after which a render-phase diagnostic reports failure to stabilize. A return of `true` means at least one candidate committed, even if a later follow-up fails; `false` means none committed. Off-owner admission is also `"queued"`, not completion. Keep rendering pure instead of relying on this bound as a loop mechanism. `root.isUpdating()` covers render, expansion, layout, staging, commit and ordinary host clearing; it is false while idle or disposed. Owner-thread resize while busy returns false and remains retryable.

Ordinary `root.close()` returns `false` while an update is active or if the host-clear transaction fails, leaving it retryable. Off-owner close may be queued. `root.forceClose()` is an owner-thread-only, terminal lifecycle operation: it releases guest render/event/dependency references and marks the root disposed even if host clearing fails. During an active update, disposal is immediate and host clearing waits for the transaction to finish; queued work cannot revive it. It returns no success boolean. Use the native host normal close for user navigation; generation teardown/forced native removal use terminal cleanup, not repeated ordinary close.

## Lifecycle, validation and current gaps

Use `ClientUI.screen` → `UI.createRoot` → `host.bindRoot` → `host.open` on the CLIENT owner. An owned Screen reads `title` and `pausesGame` from the committed root `screen` props; without those props it uses factory defaults. Rejected candidates cannot change them. `pausesGame` is a local Screen pause policy, not authority to pause a remote server. Player removal/replacement is terminal; normal user close is retryable, and late cleanup never dismisses a newer Screen. A successful CLIENT reload retires old roots, closures and Screens; failed candidates preserve the active generation. Closing/replacing the Screen cleans up managed callbacks. Do not store stale VNodes, Screen hosts or root handles for another generation. Report diagnostics with the installed source/declarations; a type-correct tree does not prove layout or input behavior. Relevant codes are `NEKO-7007` for runtime-phase/host failures (including clipboard failures), native `NEKO-7008` for invalid control appearance, and native `NEKO-7011` for an invalid slider range/value. Common JSX validation can reject malformed props earlier in the layout phase and report `NEKO-7007` with that diagnostic instead of a native code; do not promise one code for every invalid prop. The existing `NEKO-7004` remains the owner-thread diagnostic.

Workspace construction/controller validation uses `NEKO-7013` for invalid configuration/IDs or mutation during root publication. It can be thrown directly as an authoring exception; report the actual call/phase rather than assuming every rejection was logged natively. It is not a new HTTP/MCP wire error code. `NEKO-7014` identifies managed-mount authoring validation: non-positive/unrepresentable GUI region bounds, region-only operations on an owned Screen mount, or violation of the single-root binding/publication boundary. Direct factory/`setRegion` calls can throw; do not reclassify these as appearance (`NEKO-7008`) errors.

For HUD/world drawing and post-effect builders use [client rendering](client-rendering.md), not JSX tags. Missing capabilities remain explicit: no Fabric/1.21.1 ClientUI parity; no server containers, Slots or inventory/network authority; no generic drag/drop, virtualized list implementation or arbitrary native Widgets; no React/DOM/CSS or IME composition contract. The inline Select is not a native popup. Real GPU appearance, OS clipboard behavior, caret clipping/focus on supported clients, IME interaction and multiplayer acceptance need separate live testing and have not been established by fixture-only verification.
