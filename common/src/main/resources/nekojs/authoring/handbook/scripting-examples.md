# Scripting examples

Use these complete files as small, independent scenarios, not as an automatically installed demo pack. They ship with this NekoJS release; confirm the installed Minecraft version, loader and Script Type in `environment.json` under the selected AI root (discover it through `nekojs/config/ai-location.json`; default `.github/neko_ai`), then check unfamiliar calls against that instance's declarations or [query API](query-api.md). Vanilla identifiers and the `nekojs_example` namespace keep dependencies explicit; they do not guarantee behavior on every platform.

## Safe setup and completion

1. Use a disposable game instance and world. Back up existing scripts and close the game before copying STARTUP files. Registration changes can affect saved content; test them only in a world you can discard.
2. Resolve the active bundle from `current.json` under that selected AI root. Paths beginning `examples/` below are relative to that bundle. Copy only the selected scenario to its exact destination under the game directory; keep coupled files together. The exported bundle itself is immutable reference data, not the editable script workspace.
3. Run the scenario's full-type reload command, explicit test command or process restart. In-game commands require the appropriate permission; use the matching client runtime for CLIENT scripts. Check the command result and original script diagnostics before inspecting effects.
4. Compare the actual result with **Expect**. Record passed, failed and not-run checks separately. An isolated contract fixture or successful compilation is not proof of live recipe decoding, tag application, rendering or gameplay.
5. Remove copied runtime files and reload their Script Type when finished. For startup registry content, shut down and discard the disposable world/instance rather than removing registered objects from a valuable save. State cleanup notes below apply separately.

Keep identifiers unique; install a scenario once. Signed packs retain their normal source and signing workflow. [Validation](validation.md) describes the final acceptance record. Authoring exports do not install these scripts. HTTP and batch authoring remain read-only; [explicitly enabled live MCP actions](live-controls.md) require separate authorization and do not replace manual file installation.

## Scenario map

Paths below are relative to the bundle's `examples/` directory. Each section contains exact shipped source; [metadata](../examples/metadata.json) records the 14 scenario install sets, prerequisites and verification labels. Follow the linked section's profile and cleanup notes as part of the scenario.

| Scenario | Files | Script Type / trigger |
|---|---|---|
| [Login welcome](#welcome-a-player-after-login) | `server/player-login.js` | SERVER reload, then reconnect |
| [Recipes](#replace-and-add-recipes) | `server/recipes.js` | Whole SERVER reload plus recipe postprocessing |
| [Item tag](#item-and-block-tags-separate-registries) | `server/item-tags.js` | NeoForge: SERVER reload, then vanilla `/reload` |
| [Block tag](#item-and-block-tags-separate-registries) | `server/block-tags.js` | NeoForge: SERVER reload, then vanilla `/reload` |
| [Registry builders](#register-an-item-and-a-block-at-process-startup) | `startup/registry.js` | STARTUP; process restart |
| [Components and counts](#construct-detached-stacks-with-components) | `server/item-components.js` | SERVER reload |
| [State and timer](#share-primitive-configuration-and-schedule-work) | `startup/shared-config.js` + `server/state-and-timer.js` | STARTUP + SERVER; restart once, then SERVER reload |
| [Client TSX counter](#client-tsx-counter-supported-ui-profile-only) | `client/counter.tsx` | NeoForge 26.x only; CLIENT reload |
| [Client controls](#client-controls-input-toggle-slider-and-inline-select) | `client/controls.tsx` | NeoForge 26.x only; CLIENT reload |
| [Client form and dialog](#client-tabs-validated-form-modal-confirmation-and-item-preview) | `client/forms-and-dialogs.tsx` | NeoForge 26.x only; CLIENT reload |
| [CommonJS](#commonjs-two-files-one-relative-import) | `modules/hello.cjs` + `modules/lib/message.cjs` | SERVER reload; preserve layout |
| [Assertions](#explicit-test-assertions) | `test/assertions.js` | TEST; `/nekojs test` |
| [Console warmup](#small-smoke-checks) | `greeting.js` | SERVER reload |
| [Event warmup](#small-smoke-checks) | `event-subscription.js` | SERVER reload |

Verification labels are evidence boundaries, not platform support promises: `isolated-contract` checks callback/API shape against fixtures; `real-engine` runs the common script/module pipeline; `real-engine-with-platform-fixture` exercises compiler/runtime behavior with the platform host replaced. Live Minecraft acceptance remains separate and not run by these bundle checks.

## Welcome a player after login

### [examples/server/player-login.js](../examples/server/player-login.js)

```js
// Place in server_scripts. The callback runs for future logins, not existing players.
PlayerEvents.loggedIn(event => {
    event.player.sendMessage("Welcome to NekoJS!");
});
```

**Copy:** `nekojs/server_scripts/authoring-examples/player-login.js` (SERVER).

**Run:** `/nekojs reload server`, then disconnect and log in again. **Expect:** the joining player receives `Welcome to NekoJS!`. Existing connected players are not retroactively sent the message. The callback reads the Bean property `event.player` and calls its message operation; keep the player handle inside the callback rather than storing it across reloads.

**Cleanup:** remove the copied file and reload SERVER; future logins no longer trigger this handler.

## Replace and add recipes

### [examples/server/recipes.js](../examples/server/recipes.js)

```js
// Use a disposable world: this replaces the vanilla stick recipe.
ServerEvents.recipes(event => {
    event.remove({ id: "minecraft:stick" });
    event.shaped(Item.of("minecraft:stick", 4), ["P", "P"], {
        P: Ingredient.of("minecraft:oak_planks")
    }).id("nekojs_example:sticks");
    event.shapeless(Item.of("minecraft:flint"), [
        Ingredient.of("minecraft:gravel"),
        Ingredient.of("minecraft:gravel")
    ]).id("nekojs_example:flint");
    event.smelting(Item.of("minecraft:glass"), Ingredient.of("minecraft:sand"), 0.1, 200)
        .id("nekojs_example:glass");
});
```

**Copy:** `nekojs/server_scripts/authoring-examples/recipes.js` (SERVER). This deliberately removes `minecraft:stick`; use only the disposable world.

**Run:** `/nekojs reload server` and wait for both script commit and recipe postprocessing to succeed. Use the whole-type command, not a single-file reload: the command's recipe replay is a separate postprocessing step.

**Expect:** `nekojs_example:sticks` makes four sticks from two vertically arranged oak planks; the ordinary vanilla stick recipe is removed. `nekojs_example:flint` makes one flint from two gravel in any arrangement. `nekojs_example:glass` smelts sand into glass with 0.1 experience and a configured duration of 200 ticks. The vanilla sand-to-glass recipe may also remain; adding this ID does not replace it. Check actual crafting/furnace behavior, not only an editor tooltip or recipe viewer cache.

`Item.of` constructs output stacks, `Ingredient.of` constructs inputs, and each `.id(...)` identifies the resulting recipe. Filters such as `{ id: ... }` belong to the recipe API, not the authoring query language. Version-specific recipe decoding still needs live verification.

**Cleanup:** remove the copied file and perform a full SERVER reload; verify the vanilla stick recipe is restored and the three example IDs are absent. If resource/viewer state remains stale, finish a vanilla `/reload` and inspect its result too.

## Item and block tags: separate registries

**Profile gate:** these examples require the implemented NeoForge tag-reload path. The current Fabric tag hook still has TODO paths; do not treat an isolated callback test as Fabric support.

### [examples/server/item-tags.js](../examples/server/item-tags.js)

```js
// Requires the NeoForge tag-reload path; follow server script reload with /reload.
ServerEvents.tags("minecraft:item", event => {
    event.add("nekojs_example:stones", "minecraft:stone");
    event.add("nekojs_example:stones", "minecraft:granite");
    event.remove("nekojs_example:stones", "minecraft:granite");
});
```

### [examples/server/block-tags.js](../examples/server/block-tags.js)

```js
// This block tag is separate from the item tag with the same name. NeoForge tag-reload path required.
ServerEvents.tags("minecraft:block", event => {
    event.add("nekojs_example:stones", "minecraft:stone");
});
```

**Copy:** `nekojs/server_scripts/authoring-examples/item-tags.js` and/or `nekojs/server_scripts/authoring-examples/block-tags.js` (SERVER). They are independent scenarios; installing both demonstrates that the same tag name belongs to two different registries.

**Run:** `/nekojs reload server`, then vanilla `/reload`, waiting for each command and the resource reload to finish. Whole SERVER reload alone replays recipes, not tag materialization.

**Expect:** the item tag `nekojs_example:stones` contains `minecraft:stone` but not `minecraft:granite`: removals apply after additions. The block tag with that name contains `minecraft:stone` independently. Test an actual stone item/block against the corresponding registry tag after resource reload. `event.add`/`remove` here take plain element IDs, not `#`-prefixed tag references. `getEntries` reads source tag data and does not prove queued modifications were applied.

**Cleanup:** remove the selected copied files, reload SERVER, then run vanilla `/reload` again and verify the example-only tags are absent. Other packs can contribute to the same names; use a clean disposable instance for the stated membership checks.

## Register an item and a block at process startup

### [examples/startup/registry.js](../examples/startup/registry.js)

```js
// STARTUP only: restart the whole process. Use a disposable world and matching client/server definitions.
RegistryEvents.register(event => {
    event.item("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
    event.block("nekojs_example:ruby_block", build => {
        build.hardness = 2;
        build.resistance = 6;
        build.lightLevel = 3;
    });
});
```

**Copy:** `nekojs/startup_scripts/authoring-examples/registry.js` (STARTUP), before starting the disposable instance. Keep matching startup definitions on client and server.

**Run:** fully restart the game process. Registration is a once-per-boot phase; ordinary script reload is not a registry rebuild. **Expect:** the runtime registers `nekojs_example:ruby` with maximum stack size 16 and rare rarity, and `nekojs_example:ruby_block` with hardness 2, resistance 6 and light level 3. Its same-ID block item is automatic: the file intentionally does not declare another item with the block ID. With permission, obtain the items through `/give @s nekojs_example:ruby` and `/give @s nekojs_example:ruby_block`, then place the block and inspect behavior.

The example supplies no textures, models, language assets, creative-tab placement or automatic item grants. Missing visuals are not proof registration failed. Check installed declarations and live registration on your exact loader/version before extending the builders.

**Cleanup:** close and discard the disposable world before removing this STARTUP file; restart without it. Removing registered content from a valuable save is not a safe reload operation.

## Construct detached stacks with components

### [examples/server/item-components.js](../examples/server/item-components.js)

```js
// Detached stacks only: component decoding still needs the running instance registry and codec.
const exampleSword = Item.of("minecraft:iron_sword[damage=2]", 1);
const exampleStones = Item.of("minecraft:stone", 4);
console.info("Detached sword with damage 2:");
console.info(exampleSword);
console.info("Detached stone stack with count 4:");
console.info(exampleStones);
```

**Copy:** `nekojs/server_scripts/authoring-examples/item-components.js` (SERVER). Confirm the installed `Item.of(String, int)` signature and component syntax; component decoding depends on the running instance registry and codec.

**Run:** `/nekojs reload server`. **Expect:** the console labels and stack objects describe one detached iron sword with damage 2 and a detached stone stack with count 4. The explicit second argument sets each constructor count; this does not demonstrate a generic host-object setter. No item is given to a player and no inventory is modified. The isolated fixture checks the string, counts and log calls, not Minecraft codec acceptance.

**Cleanup:** remove the copied file and reload SERVER. These local stacks do not write inventory or world data.

## Share primitive configuration and schedule work

### [examples/startup/shared-config.js](../examples/startup/shared-config.js)

```js
// Restart the process after installing this STARTUP script. Store primitives, not player handles.
shared.authoringMessage = "Welcome to NekoJS";
```

### [examples/server/state-and-timer.js](../examples/server/state-and-timer.js)

```js
// Copy startup/shared-config.js first, then restart once. This counter is not saved to disk.
global.authoringLoads = (global.authoringLoads || 0) + 1;
console.info(`Authoring SERVER load #${global.authoringLoads}: ${shared.authoringMessage}`);
setTimeout(() => {
    console.info("Authoring timeout fired");
}, 1000);
```

**Copy:** both `nekojs/startup_scripts/authoring-examples/shared-config.js` (STARTUP) and `nekojs/server_scripts/authoring-examples/state-and-timer.js` (SERVER).

**Run:** fully restart the game process once so the STARTUP assignment runs, then open the disposable world. **Expect:** the SERVER script logs `Authoring SERVER load #1: Welcome to NekoJS` in a fresh runtime; after the delay becomes eligible and the owner thread flushes callbacks, it logs `Authoring timeout fired`. The 1000 ms argument is not an exact wall-clock or tick deadline.

Run `/nekojs reload server` again. **Expect:** the same runtime's `global.authoringLoads` increases; the shared greeting stays available. Old-generation timers are canceled on teardown, while the new script schedules its own timeout. Reloading before the delay elapses need not produce an old-generation timeout message. Other previously installed copies can change the counter, so use a clean instance for the initial `#1` observation.

`global` is private to a Script Type; `shared` crosses Script Types in this one runtime/process. Primitive state survives ordinary reloads and world changes until the runtime root closes, but is neither saved to disk nor synchronized between client/server processes. Guest objects, callbacks and player handles are generation-lifetime values, not durable storage. Failed candidate top-level state edits are not published as an active generation. Positive-delay timers belong here in SERVER, not STARTUP.

**Cleanup:** remove both copied files and restart the process to clear their root-owned primitive state and timers. Changing the STARTUP greeting also requires a process restart.

## Client TSX counter: supported UI profile only

### [examples/client/counter.tsx](../examples/client/counter.tsx)

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
const host = ClientUI.screen("Authoring counter", false);
const root = UI.createRoot(() => (
    <screen>
        <column>
            <label id="count">{String(count.get())}</label>
            <button id="increment" onClick={() => count.update(value => value + 1)}>
                Increment
            </button>
        </column>
    </screen>
), host);
host.bindRoot(root);
host.open();
```

**Profile gate:** the current `ClientUI` binding is published only for **NeoForge 26.x**. Skip this file on Minecraft 1.21.1 and Fabric; a generic CLIENT directory or TSX compiler does not imply that binding exists. Confirm the installed profile and declarations first.

**Copy:** `nekojs/client_scripts/authoring-examples/counter.tsx` (CLIENT), only in the supported integrated client.

**Run:** `/nekojs reload client`. **Expect:** an `Authoring counter` screen opens with count `0`; clicking **Increment** changes the label to `1`, then `2`. Close the screen normally. Reload CLIENT to create a fresh signal at `0` and supersede the old screen/root; closed roots dispose their listeners. This is `UI.createSignal` + `UI.createRoot` with the native `ClientUI` host, not React state or browser DOM elements.

**Cleanup:** close the screen, remove the copied file and reload CLIENT. Rendering, input focus and click behavior need real client acceptance; the compiler/runtime contract test uses a fake platform host and cannot certify those effects.

## Client controls: input, toggle, slider and inline select

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

**Profile/copy:** NeoForge 26.x CLIENT only; install at `nekojs/client_scripts/authoring-examples/controls.tsx`. Do not install on Fabric, 1.21.1 or a dedicated server lacking `ClientUI`. The historical counter above is unchanged.

**Run/expect:** `/nekojs reload client` opens Authoring controls with `enabled=true; volume=40; quality=balanced`. Toggle Enable volume to disable/re-enable the numeric slider; drag it or use arrows/Home/End. Expand the inline selector and choose Fast (`quality=fast`); Unavailable cannot be chosen. The text field supports single-line selection and clipboard C/X/V with Control/Super, code-point `maxLength`, and caret-following horizontal clipping. Read [JSX UI](jsx-ui.md) for exact native versus controlled callback types, reserved Select IDs and appearance constraints.

**Cleanup/evidence:** close the Screen, remove the copied file and reload CLIENT. A fresh load resets all five signals; closing/replacing the managed host disposes callbacks. Platform-fixture engine verification is not GPU, clipboard, native focus, IME or live-client acceptance.

## Client tabs: validated form, modal confirmation and item preview

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

**Profile/copy:** NeoForge 26.x CLIENT only; install alone at `nekojs/client_scripts/authoring-examples/forms-and-dialogs.tsx`. Skip Fabric, 1.21.1 and dedicated servers without `ClientUI`. Keep the earlier counter and controls scenarios unchanged and choose only one auto-opening example.

**Run/expect:** `/nekojs reload client` opens Profile with `Nothing confirmed` and `Background clicks: 0`. Review without a name shows errors and no dialog; quantity must be an integer from 1 through 64. Enter `Alex` and `3`, then Review: the dialog displays `Alex x3`. Confirm sets `Confirmed Alex x3`; Cancel/dismissal changes no profile status. Preview shows a read-only diamond icon/tooltip, not inventory access. Locked remains disabled; external signals preserve values across tab changes. See [JSX UI](jsx-ui.md) for exact contracts and the pending native acceptance checklist.

**Cleanup/evidence:** close the Screen, remove this script and reload CLIENT. All eight signals reset. Common-engine/platform fixtures do not prove modal focus/capture, OS input, visible native item models/tooltips, resizing or live lifecycle acceptance. No world, inventory, persistent or multiplayer state is written.

## CommonJS: two files, one relative import

### [examples/modules/hello.cjs](../examples/modules/hello.cjs)

```js
// Copy this entry and lib/message.cjs together, preserving their relative paths.
const { formatGreeting } = require("./lib/message.cjs");
const message = formatGreeting("NekoJS");
console.info(message);
module.exports = { message };
```

### [examples/modules/lib/message.cjs](../examples/modules/lib/message.cjs)

```js
// This helper has no top-level side effects, even if discovered as an entry.
exports.formatGreeting = name => `Hello, ${name}!`;
```

**Copy:** both files to `nekojs/server_scripts/authoring-examples/modules/hello.cjs` and `nekojs/server_scripts/authoring-examples/modules/lib/message.cjs`. Preserve the `lib/` relationship.

**Run:** `/nekojs reload server`. **Expect:** the entry logs `Hello, NekoJS!` and exports an object whose `message` has that value. The helper exports a pure formatting function; recursive script discovery may also load it as an entry, so it has no top-level side effects. `require` is relative to the entry file, not the game directory. This is the NekoJS CommonJS loader, not a promise of Node.js built-ins or npm compatibility.

**Cleanup:** remove both copied files and reload SERVER.

## Explicit TEST assertions

### [examples/test/assertions.js](../examples/test/assertions.js)

```js
// Place in test_scripts and run /nekojs test. This does not run on server reload.
Test.section("Authoring example assertions");
Test.assertEquals("minecraft:stick", ["minecraft", "stick"].join(":"), "identifier formatting");
Test.assertTrue(["oak", "birch"].includes("oak"), "membership check");
Test.assertThrows(() => { throw new Error("Expected example failure"); }, "failure is observed");
Test.summary();
```

**Copy:** `nekojs/test_scripts/authoring-examples/assertions.js`.

**Run:** `/nekojs test`; ordinary server reload does not execute TEST files. **Expect:** three passing assertions and a summary. `Test.assertThrows` passes because it observes the deliberately thrown error; that error is not a failing test. Other installed test files may add to the total. These are script-level assertions, not world or player integration tests.

**Cleanup:** remove the copied test file; subsequent `/nekojs test` runs no longer include it.

## Small smoke checks

### [examples/greeting.js](../examples/greeting.js)

```js
// Place in server_scripts. This example only writes a startup message.
console.info("NekoJS authoring example loaded");
```

**Copy:** `nekojs/server_scripts/authoring-examples/greeting.js`. **Run:** `/nekojs reload server`. **Expect:** one `NekoJS authoring example loaded` console line from this script per execution. It makes no gameplay changes.

### [examples/event-subscription.js](../examples/event-subscription.js)

```js
// Place in server_scripts. Observe the recipe event without changing recipes.
ServerEvents.recipes(event => {
    console.info("NekoJS recipe authoring example loaded");
});
```

**Copy:** `nekojs/server_scripts/authoring-examples/event-subscription.js`. **Run:** `/nekojs reload server`. **Expect:** `NekoJS recipe authoring example loaded` when the recipe callback is dispatched. The callback observes the event without adding or removing recipes. This smoke check does not certify recipe codec behavior.

**Cleanup:** remove the copied smoke-check files and reload SERVER.
