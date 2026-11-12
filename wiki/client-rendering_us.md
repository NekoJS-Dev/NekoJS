<!-- wiki-page: client-rendering; locale: us -->

> **English** · [中文](client-rendering_cn)

<a id="wiki-section-1"></a>
# Client rendering and post effects

This page shows how client scripts draw on screen: HUD text, overlays on open screens, outlines in the world, and full-screen post effects such as invert or blur.

All of these scripts go in `nekojs/client_scripts/`. Run `/nekojs reload client` after a change. A dedicated server has no screen, so none of this works there.

Everything here redraws every frame, which suits displaying information. For interactive screens with buttons and text fields, see [JSX client UI](jsx-client-ui_us).

<a id="wiki-section-2"></a>
## What each platform supports

| Feature | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.2.0 | Fabric 26.x |
|---|---|---|---|---|
| `ClientEvents.hud` / `screenRender`: draw on the HUD and screens | Yes | Yes | Yes | Yes |
| `ClientEvents.hudRender`: named HUD renderers | Yes | Yes | Yes | No |
| `ClientEvents.worldRender`: outlines in the world | Yes | Yes | Yes | No |
| `ClientEvents.postEffects` / `PostEffects`: post effects | Yes (legacy format) | Yes | Yes | No |
| `KeyBindEvents`: custom key bindings | No | Yes | Yes | Yes |

Check [Platforms and compatibility](platform-compatibility_us) before copying an example to another loader.

<a id="wiki-section-3"></a>
## Draw on the HUD and screens

Create `nekojs/client_scripts/painter.js`:

```javascript
// Every frame, draw a translucent box and a line of text in the top-left of the HUD
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text('NekoJS HUD', 14, 14, 0xFFFFFFFF)
})

// While any screen is open (such as the inventory), show the mouse position
ClientEvents.screenRender(event => {
  event.painter.text(`Mouse: ${event.mouseX}, ${event.mouseY}`, 8, 8)
})
```

Run `/nekojs reload client`. The text appears on the HUD, and opening your inventory shows the mouse position. To remove them, delete the file and reload again.

Colors are `0xAARRGGBB`; the first two digits are the alpha. The `screenRender` event also has `event.screen` and `event.screenTitle`, so you can check which screen is open.

<a id="wiki-section-4"></a>
### What Painter can draw

| Method | What it does |
|---|---|
| `text(text, x, y[, color])` / `centerText(...)` | Draw text / draw text centered on x |
| `rect(x, y, w, h[, color])` / `outline(...)` | Filled rectangle / rectangle outline |
| `gradient(x, y, w, h, top, bottom)` / `gradientH(...)` | Vertical / horizontal gradient |
| `item(stack, x, y)` | Draw an item icon |
| `texture(id, x, y, w, h, ...)` / `textureFull(...)` | Draw part of a texture / a whole texture |
| `push()` / `translate(x, y)` / `pop()` | Save, offset and restore the coordinates |
| `scissor(x, y, w, h)` / `resetScissor()` | Draw only inside an area / remove the limit |
| `textWidth(text)` / `wrapText(text, maxWidth)` / `lineHeight()` | Measure and wrap text |

You can also read `painter.width` and `painter.height` (the screen size) and `painter.partialTick`.

> `painter` is only valid during the current callback. Do not keep it in `global` or a timer for later.

<a id="wiki-section-5"></a>
## Named HUD renderers (NeoForge)

`ClientEvents.hudRender` gives a piece of HUD an id and lets you choose whether it draws before or after the vanilla HUD:

```javascript
// nekojs/client_scripts/named-hud.js
ClientEvents.hudRender('nekojs:status', {
  layer: 'foreground',
  priority: 10
}, ctx => {
  ctx.rect(8, 32, 120, 18, 0xB020252B)
  ctx.text('Named HUD renderer', 12, 36, 0xFFFFFFFF)
})
```

After a reload, this text appears below the previous example.

- **Forms**: `hudRender(id, callback)` or `hudRender(id, options, callback)`. `options` can be `{ layer, priority }`, just a layer name string, or just a priority number.
- **Layers**: `background` draws before the vanilla HUD (so the vanilla HUD covers it); `normal` and then `foreground` draw after it. The default is `normal`.
- **Priority**: within a layer, lower numbers draw first. The default is `0`; it must be an integer.
- **Callback**: `ctx` has Painter-like methods (`text`, `rect`, `outline`, `gradient`, `texture`, `item`, `scissor` and so on), plus `ctx.width`, `ctx.height` and `ctx.partialTick`. The second argument is the native graphics object, which differs between versions; avoid it when you can.
- **Updating and removing**: registering the same id again replaces it. After a reload, any id your scripts no longer register disappears. So deleting the script and reloading removes it.

Unknown fields in `options`, an unknown layer name, or a non-integer priority make the registration fail with an error.

<a id="wiki-section-6"></a>
## Outlines in the world (NeoForge)

```javascript
// nekojs/client_scripts/world-lines.js
// Draw a green one-block box at world position (0, 64, 0)
ClientEvents.worldRender('nekojs:origin-box', {
  layer: 'normal',
  priority: 0
}, ctx => {
  ctx.box(0, 64, 0, 1, 65, 1, 0xFF00FF00)
})
```

After a reload, go near `(0, 64, 0)` to see the box. It is only drawn lines; no block is placed.

- `ctx.box(minX, minY, minZ, maxX, maxY, maxZ, color)` draws the 12 edges of a box.
- `ctx.line(x1, y1, z1, x2, y2, z2, color[, width])` draws one line.
- All coordinates are world coordinates; you do not subtract the camera position yourself. `ctx.cameraPos` and `ctx.partialTick` describe the current frame.
- There are three layers: `early` (after translucent blocks), `normal` (after weather, the default) and `late` (after the whole world is drawn). Lower priorities draw first within a layer.
- Forms, priority and id rules are the same as for `hudRender`.

Use one `box` call rather than twelve `line` calls. Every call has a fixed cost, so fewer calls per frame is better.

> On 26.2, lines drawn in the `late` layer may not be hidden behind blocks. If an outline should be hidden by walls, use the `normal` layer.

<a id="wiki-section-7"></a>
## Post effects (NeoForge)

Post effects change the whole picture, for example invert, blur or the creeper view.

<a id="wiki-section-8"></a>
### Turning effects on and off

The simplest use is toggling an effect that ships with vanilla. This example toggles invert with the G key (`KeyBindEvents` needs 26.x; NeoForge 1.21.1 does not have it):

```javascript
// nekojs/client_scripts/effect-key.js
KeyBindEvents.register('nekojs:toggle_invert', 'key.keyboard.g')

KeyBindEvents.pressed('nekojs:toggle_invert', event => {
  if (PostEffects.isAvailable('minecraft:invert')) {
    PostEffects.toggle('minecraft:invert')
  }
})
```

After a reload, press G to invert the picture and press it again to restore it. Players can rebind the key in the controls menu.

Common `PostEffects` methods:

| Method | What it does |
|---|---|
| `set(id)` | Turn an effect on |
| `toggle(id)` | Turn it on if it is off, or off if it is on |
| `clear()` | Turn off the current effect |
| `current()` | The current effect id, or `null` |
| `isAvailable(id)` | Whether resource packs provide this effect; it must be available to turn on |
| `presets()` | Effects that ship with vanilla: `minecraft:invert`, `spider`, `creeper`, `blur`, `entity_outline`, `transparency` |

`PostEffects.active` tells you whether an effect is currently applied.

A `true` result from `set` or `toggle` only means the request was accepted; the picture changes on a later frame.

> An effect you turned on does **not** turn off when you delete the script and reload. Call `PostEffects.clear()` before removing such a script. Also, do not call `set` at the top level of a script; call it from a key press or another event.

<a id="wiki-section-9"></a>
### Declaring your own effects

To use your own shader settings, declare them in `ClientEvents.postEffects`. This example works **on 26.x only**:

```javascript
// nekojs/client_scripts/effect-declarations.js
ClientEvents.postEffects(event => {
  event.register('nekojs:sample_blur', build => {
    build.blurRadius = 4   // 0 to 64
    build.blurRounds = 1   // an integer from 1 to 8
  })
})
```

Run `/nekojs reload client`. Check that the declaration took effect with `PostEffects.hasDefinition('nekojs:sample_blur')`; `PostEffects.installed()` lists every declared id. Remove the declaration and reload to drop it, or call `event.unregister(id)` inside the event.

The supported settings differ by version:

| Version | Settings you can use | Effect resource file location |
|---|---|---|
| NeoForge 1.21.1 | `chainJson` or `program` | `assets/<namespace>/shaders/post/<path>.json` |
| NeoForge 26.x | `chainJson`; or `fragmentShader` (optionally with `fragmentShaderId` and `uniformsJson`); or `blurRadius` (optionally with `blurRounds`); plus optional `fragmentShaders` and `vertexShaders` | `assets/<namespace>/post_effect/<path>.json` |

**Declaring an effect does not make it available.** A declaration only supplies shader content. To turn an effect on with `set`, a resource pack must also contain an effect file with the same id (see the table for the path). If there is a declaration but no resource file, `set` returns `false` and logs a warning. That is why `hasDefinition` (is it declared?) and `isAvailable` (can it be turned on?) are different checks.

The chain JSON format differs between the two versions; do not copy one into the other.

A misspelled setting, a wrong type or an out-of-range value makes the reload fail, and the previous declarations stay in use.

<a id="wiki-section-10"></a>
## Native client classes

Client scripts can also use the native classes `Minecraft`, `Screen`, `Window`, `KeyMapping` and `InputConstants` directly. These are Minecraft's own classes, so their methods can change between versions; check them yourself when you switch versions.

Registration events such as `registerKeyMappings` and `registerMenuScreens` fire only once, while the game starts, and a reload does not fire them again. Restart the game after changing such scripts. See the [Event reference](event-reference_us).

<!-- wiki-nav -->

---

[Previous: JSX client UI](jsx-client-ui_us) · [Contents](Home) · [Next: MixinJS (experimental)](mixinjs_us)
