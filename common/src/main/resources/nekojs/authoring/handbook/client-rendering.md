# Client rendering and post effects

Use CLIENT scripts and the matching instance's Probe declarations. Painter `ClientEvents.hud` and `screenRender` are available on NeoForge and Fabric. Named `hudRender`, `worldRender`, and `postEffects` registrations are NeoForge-only; do not infer Fabric support from shared helper classes. Retained JSX Screens are a separate NeoForge 26.x capability; see [JSX UI](jsx-ui.md).

## Named HUD and world registrations

```javascript
// nekojs/client_scripts/render-example.js
ClientEvents.hudRender("nekojs:status", {
  layer: "foreground",
  priority: 10
}, ctx => {
  ctx.text("NekoJS status", 8, 8, 0xFFFFFFFF)
})

ClientEvents.worldRender("nekojs:origin-box", {
  layer: "late",
  priority: 0
}, ctx => {
  ctx.box(0, 64, 0, 1, 65, 1, 0xFF00FF00)
})
```

Install the file, run `/nekojs reload client`, and inspect the text and the box around world coordinates `(0, 64, 0)` in a disposable world. Removing the file and successfully reloading CLIENT retires these registrations. Source/geometry tests are not a visual acceptance result.

Accepted forms are `(id, callback)` and `(id, options, callback)`. The compatibility shorthands are a layer string or an integer priority. An options object has only optional `layer` and `priority` fields:

- HUD layers: `background`, `normal`, `foreground`.
- World layers: `early`, `normal`, `late`.
- Defaults: `normal`, priority `0`; priorities sort ascending within each layer. Equal-priority order is not guaranteed.
- Priority must be a finite signed 32-bit integer (`-2147483648` through `2147483647`). TypeScript `number` cannot enforce that range or integrality; runtime validation does.
- Unknown fields, inappropriate layer names, wrong value types, fractions, and out-of-range numbers fail registration instead of being ignored or truncated. Probe emits the same shape and layer literals used by runtime validation.

The options object is intentional scheduling data, not a configurable-definition builder. The callback remains last. A repeated id replaces its registration within the candidate. Full CLIENT reload publishes the complete batch only after successful preflight; failed candidates retain the previous active batch. Arbitrary Java, file, world, and immediate runtime effects are not rolled back.

Contexts are frame-owned. Do not store them, their raw graphics, matrices, or native buffers for later callbacks. World `line` and `box` accept world coordinates and subtract the camera position. NeoForge 26.2 uses an owned staged line submission rather than a removed immediate buffer source; it does not grant arbitrary GPU pipeline access. Keep callbacks short and avoid per-frame blocking work. Each 26.2 helper call owns a temporary stage whose GPU allocation is rounded to at least 262,144 bytes; prefer one box call over twelve line calls and keep draw counts small. The `late` stage does not guarantee original world occlusion: vanilla may clear the main depth buffer before `AfterLevel`. Prefer the earlier `normal` stage for depth-sensitive outlines and verify the actual target node visually.

## Post-effect declarations use a real builder

```javascript
// NeoForge 26.x: nekojs/client_scripts/effect-declarations.js
ClientEvents.postEffects(event => {
  event.register("nekojs:sample_blur", build => {
    build.blurRadius = 4
    build.blurRounds = 1
  })
})
```

Identifying arguments come first, configuration last. Set supported Bean properties on the provided builder; do not call an invented `build()` operation or retain the builder after its callback. The builder is sealed after configuration. The legacy object/Map overload remains for compatibility but has explicit node-specific input fields and strict validation, not an arbitrary `Object` escape hatch. On 26.x, `blurRadius` must be finite in `[0, 64]` and `blurRounds` must be an integer in `[1, 8]`; invalid values are rejected, not clamped.

NeoForge 1.21.1 supports its legacy `chainJson` or `program` format. NeoForge 26.x supports modern `chainJson`, fragment-shader declarations, or blur declarations. Query the exact node-specific builder before using shader fields; do not copy a legacy program field into a modern declaration. Chain JSON and shader source remain resource data, not extra builder methods. Invalid declarations reject the candidate plan rather than partially replacing healthy definitions.

Declaration collection is not activation. `PostEffects.hasDefinition(id)` checks committed declarations; `PostEffects.isAvailable(id)` checks activatable resources. Declaration-only ids cannot be activated just because registration succeeded. Resource chains use `assets/<namespace>/shaders/post/<path>.json` on 1.21.1 and `assets/<namespace>/post_effect/<path>.json` on 26.x. Supply the matching resource pack and version-specific format.

`PostEffects.set`, `toggle`, and `clear` are immediate client runtime operations outside the reload declaration transaction. Do not invoke them during candidate top-level evaluation expecting rollback. A true result is request acceptance, not shader compilation or visual completion. Clear an active effect before removing its controlling script.

## Validate and report evidence

Regenerate Probe for the matching profile and type-check valid calls plus deliberately invalid layer/field examples. Use runtime tests for numeric bounds, builder lifetime, and failed-candidate retention. Then reload CLIENT in a real game and verify HUD ordering, camera-relative lines/boxes, resource activation, cleanup, and rendering on each target node. A headless geometry fixture does not establish GPU, depth, or multiplayer behavior.
