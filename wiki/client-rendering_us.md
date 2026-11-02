<!-- wiki-page: client-rendering; locale: us -->

> **English** · [中文](client-rendering_cn)

<a id="wiki-section-1"></a>
# Client rendering and post effects

Put these scripts in `nekojs/client_scripts/`. Client callbacks run on the client owner thread, not the server thread. A dedicated server cannot draw a HUD or open a Screen. Use [JSX client UI](jsx-client-ui_us) for retained interactive Screens; the drawing callbacks here are immediate-mode.

<a id="wiki-section-2"></a>
## Choose the actual platform surface

| Entry | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.2.0 | Fabric 26.x |
|---|---|---|---|---|
| `ClientEvents.hud` / `screenRender` Painter | Available | Available | Available | Available |
| `ClientEvents.hudRender(id, options, callback)` | Available | Available | Available | Not ported |
| `ClientEvents.worldRender(id, options, callback)` | Available | Available | Available, staged submission | Not ported |
| `ClientEvents.postEffects` / `PostEffects` | Available, legacy chain format | Available, modern chain format | Available, modern chain format | Not ported |
| `ClientUI` retained JSX Screen | Unavailable | Available | Available | Unavailable |

Availability above describes the implementation, not a recorded visual acceptance test. The 26.2 world helpers now use an independently owned staged buffer rather than the removed immediate buffer source. Refer to [Platforms and compatibility](platform-compatibility_us) before copying an example across loaders.

<a id="wiki-section-3"></a>
## Draw over the HUD or current Screen

This example works on the current NeoForge and Fabric nodes:

```javascript
// nekojs/client_scripts/painter.js
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text("NekoJS HUD", 14, 14, 0xFFFFFFFF)
})

ClientEvents.screenRender(event => {
  event.painter.text(`Mouse: ${event.mouseX}, ${event.mouseY}`, 8, 8)
})
```

Run `/nekojs reload client` on the client. Look for the HUD text while playing and mouse coordinates over an open Screen. Remove the file and reload CLIENT to remove the listeners. `painter`, `event.painter`, and native graphics objects belong to the current callback: do not retain them in timers, global state, or later frames.

Painter provides text, rectangles/outlines, gradients, item icons, textures, transforms and clipping. It does not create buttons, focus, text inputs, layout, or a networked container menu. Keep render callbacks short; avoid blocking I/O and server/world mutations.

<a id="wiki-section-4"></a>
## Register a named HUD renderer (NeoForge)

```javascript
// nekojs/client_scripts/named-hud.js
ClientEvents.hudRender("nekojs:status", {
  layer: "foreground",
  priority: 10
}, ctx => {
  ctx.rect(8, 32, 120, 18, 0xB020252B)
  ctx.text("Named HUD renderer", 12, 36, 0xFFFFFFFF)
})
```

The supported forms are `(id, callback)` and `(id, options, callback)`. Options can be `{ layer, priority }`, a layer string, or a numeric priority. The default is `normal`, priority `0`. The object is explicit scheduling data, **not** a builder callback or an event payload. It accepts only optional `layer` and `priority` fields; unknown fields, inappropriate layers, and wrong types reject registration. Priority must be a finite signed 32-bit integer (`-2147483648` through `2147483647`); fractions and out-of-range values are no longer silently truncated. Probe emits fields and layer literals from the same runtime schema. TypeScript `number` alone cannot enforce integer bounds.

- HUD layers: `background` before the vanilla HUD; `normal` then `foreground` after it.
- Within each layer, lower priorities draw first. Equal-priority order is not a stable ordering contract.
- The callback receives `(ctx, graphics)`; use the version-independent helpers on `ctx` when possible. Raw graphics differ between 1.21.1 and 26.x. Read-only Bean properties include `ctx.width`, `ctx.height`, and `ctx.partialTick`. Drawing remains method calls such as `ctx.text(...)`, `rect(...)`, `texture(...)`, and `item(...)`.
- Registering the same id again replaces that id in the generation. On successful full CLIENT reload, the whole renderer batch is replaced; omitted ids retire. A failed candidate keeps the previous active batch.

After loading, observe the text below the Painter example. For cleanup, remove this file and run `/nekojs reload client`. There is no documented script-side renderer builder or per-id removal Binding; reload the intended complete batch.

<a id="wiki-section-5"></a>
## World-space lines (NeoForge)

```javascript
// nekojs/client_scripts/world-lines.js
ClientEvents.worldRender("nekojs:origin-box", {
  layer: "late",
  priority: 0
}, ctx => {
  ctx.box(0, 64, 0, 1, 65, 1, 0xFF00FF00)
})
```

Run `/nekojs reload client` in a disposable world and view the block-sized box at world coordinates `(0, 64, 0)`. No block is placed. NeoForge 1.21.1, 26.1.2, and 26.2.0 submit the outline; on 26.2 one `box` batches all twelve edges into one independently owned staged submission. Each helper call owns a temporary stage with GPU allocations rounded to at least 262,144 bytes; limit calls per frame and prefer one `box` over twelve `line` calls. Source/geometry regressions are not actual GPU visual acceptance. Remove the file and reload CLIENT to retire it.

World layers are `early` after translucent blocks, `normal` after weather, and `late` after the level render. The default is `normal`; priorities sort ascending within a layer. On 26.2 vanilla may clear the main depth buffer before `AfterLevel`, so `late` does not guarantee original world occlusion; prefer the earlier `normal` stage for depth-sensitive outlines and verify the actual node visually. `ctx.cameraPos` and `ctx.partialTick` describe the frame. `line(...)` and `box(...)` take world coordinates; the helper subtracts the camera position. They do not register entities, change world blocks, or provide an arbitrary GPU pipeline.

<a id="wiki-section-6"></a>
## Declare post effects separately from activating them (NeoForge)

The declaration event collects an inert candidate plan. For example, **26.x only**:

```javascript
// nekojs/client_scripts/effect-declarations.js
ClientEvents.postEffects(event => {
  event.register("nekojs:sample_blur", build => {
    build.blurRadius = 4
    build.blurRounds = 1
  })
})
```

Identifying arguments come first and configuration last. Set the builder properties inside its callback; it is sealed afterward and cannot be retained for later mutation. No extra `build()` call is needed. The legacy object/Map overload remains, with node-specific fields and strict validation: misspelled fields and wrong value types fail instead of being ignored. On 26.x, `blurRadius` must be finite in `[0, 64]` and `blurRounds` must be an integer in `[1, 8]`; invalid values are no longer clamped.

Load with `/nekojs reload client`. `register` returning true means the declaration was collected, not that a shader is visible or that the whole candidate committed. Invalid declarations fail batch preflight; a rejected candidate retains the previous declarations. Query the committed set with `PostEffects.hasDefinition(id)`, `installed()`, and `activeGeneration()`. Remove the file and reload CLIENT to retire the declaration. `event.unregister(id)` also declares retirement at commit; the old `PostEffects.register/unregister` runtime entry is absent.

| Version | Accepted declaration configuration | Resource-backed activation path |
|---|---|---|
| NeoForge 1.21.1 | `chainJson` or `program` | `assets/<namespace>/shaders/post/<path>.json` |
| NeoForge 26.x | `chainJson`, or `fragmentShader` with optional `fragmentShaderId` / `uniformsJson`, or `blurRadius` with optional `blurRounds`; optional `fragmentShaders` / `vertexShaders` tables | `assets/<namespace>/post_effect/<path>.json` |

The chain JSON schemas differ by Minecraft version. A declaration alone does not mount a corresponding activatable resource: declaration-only ids are refused by `set`. Supply the correct resource-pack chain or choose an available vanilla/resource-pack effect. `PostEffects.hasDefinition(id)` and `PostEffects.isAvailable(id)` answer different questions.

A separate **NeoForge 26.x runtime** example toggles an existing resource using G (`KeyBindEvents` is absent on 1.21.1; Fabric has its own key-binding implementation but no `PostEffects`):

```javascript
// nekojs/client_scripts/effect-key.js
KeyBindEvents.register("nekojs:toggle_invert", "key.keyboard.g")
KeyBindEvents.pressed("nekojs:toggle_invert", event => {
  if (PostEffects.isAvailable("minecraft:invert")) {
    PostEffects.toggle("minecraft:invert")
  }
})
```

After CLIENT reload, press G in-game (or the rebound key). Availability depends on active resources. Press it again to clear the same effect. `PostEffects.clear()` clears the current effect explicitly; `current()` reports the selected id, and the read-only `active` Bean property reports application state. A true `set`/`toggle` result is acceptance, not GPU compilation or visual completion. Runtime `set`, `clear`, and `toggle` are immediate client actions, outside the reload declaration transaction; avoid invoking them during candidate top-level evaluation. Clear the picture before removing the key script; removing a listener is not an undo of its prior runtime effect.

<a id="wiki-section-7"></a>
## Client bindings and current API style

CLIENT exposes `Minecraft`, `Screen`, `Window`, `KeyMapping`, and `InputConstants` class bindings. Their presence is an advanced native access path, not a cross-version wrapper promise. `ClientUI` has the narrower NeoForge 26.x scope above. On the NeoForge/Fabric 26.x key-binding surface, `KeyBindEvents.register(id, key[, category])` is a positional registration entry; `pressed`, `released`, and `tick` are event subscriptions and may select an id before the callback. Native registration events such as `registerMenuScreens` and `registerKeyMappings` are lifecycle-specific; a late reload cannot replay the loader startup phase automatically. See [Event reference](event-reference_us).

Configurable post-effect definitions now have a real `event.register(id, build => ...)` overload; the validated object overload remains compatible. Renderer options stay explicit scheduling data in `{ layer, priority }`, not an invented builder. JSX props/VNodes, Painter data, and shader JSON retain their own data contracts. Use published methods for actions and read-only Bean properties for exposed getters.

<!-- wiki-nav -->

---

[Previous: JSX client UI](jsx-client-ui_us) · [Contents](Home) · [Next: Node.js compatibility](nodejs-compatibility_us)
