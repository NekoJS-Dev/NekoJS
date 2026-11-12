<!-- wiki-page: script-api-style; locale: us -->

> **English** · [中文](script-api-style_cn)

<a id="wiki-section-1"></a>
# Script API conventions

The NekoJS script API uses only a few fixed patterns. Once you know them, you can guess how a new event or helper works. Code copied from other projects such as KubeJS may use different patterns; rewrite it to match this page.

<a id="wiki-section-2"></a>
## The four patterns

| Pattern | Example | Used for |
|---|---|---|
| Event Group | `ServerEvents.recipes(event => { ... })` | Subscribing to a game event |
| Binding | `Item.of('minecraft:stone')` | General helpers you call directly |
| Configuration callback | `event.item(id, build => { ... })` | Creating new content and setting its properties |
| Property | `event.player`, `build.maxStackSize = 16` | Reading or changing a value on an object |

<a id="wiki-section-3"></a>
## Event Groups: subscribing to events

Events are grouped by purpose, such as `ServerEvents`, `PlayerEvents` and `ClientEvents`. Add the event name after the group and pass a callback:

```javascript
// server_scripts/welcome.js
PlayerEvents.loggedIn(event => {
  event.entity.sendMessage('Welcome to NekoJS!')
})
```

The `event` your callback receives is the Event Object. Everything about this occurrence, data and actions alike, comes from it.

Some events can be filtered by ID. The filter goes before the callback: `PlayerEvents.inventoryChanged('minecraft:diamond', event => { ... })`.

To cancel a cancellable event, `return true` from the callback. There is no general `event.cancel()`.

The same event can have different members on different loaders. The login event above uses `event.entity` on NeoForge; on Fabric, use `event.player` instead. For each event's script type, cancellability and members, see the [Event reference](event-reference_us).

<a id="wiki-section-4"></a>
## Bindings: general helpers

Names like `Item`, `Text` and `JsonIO` are available in every script without `require`. These are Bindings. Helpers that do not belong to one event live on Bindings, for example `Item.of(...)` to create an item stack.

See [Global bindings](global-bindings_us) for the full list.

<a id="wiki-section-5"></a>
## Configuration callbacks: creating content

To create something new, pass the required arguments such as the ID first, and a configuration callback **last**. The `build` object the callback receives sets the new content's properties:

```javascript
// startup_scripts/content.js
RegistryEvents.register(event => {
  event.item('nekojs_example:ruby', build => {
    build.maxStackSize = 16
    build.rarity = 'rare'
  })
})
```

`event` creates, `build` configures. Keep them apart. The definition is complete when the callback returns: do not call `build()`, and do not keep `build` to change it later.

Item registration goes in `startup_scripts` and needs a game restart. See [Registering new content](registering-new-content_us).

<a id="wiki-section-6"></a>
## Properties: read and write like variables

A Java object's `getXxx()` / `setXxx(value)` methods can be used as a property in scripts:

- Read: `event.player` is the same as `event.getPlayer()`.
- Write: `build.maxStackSize = 16` is the same as `build.setMaxStackSize(16)`.

A property with only a getter is read-only; assigning to it is an error. Methods that take arguments stay method calls, such as `data.getInt('key')`.

When you are not sure whether a property is writable or what it is called, run `/nekojs probe` and trust the editor's completion. Do not guess from Java class names.

<a id="wiki-section-7"></a>
## When to pass an object

Some APIs take a plain object `{ ... }`. These objects are just data, not another pattern:

| Use | Example | What the object holds |
|---|---|---|
| Recipe filter | `event.remove({ id: 'minecraft:stick' })` | Filter conditions |
| Item input | `{ id: 'minecraft:stone', count: 2 }` | Item ID, count and components |
| Render options | `ClientEvents.hudRender(id, { layer: 'foreground', priority: 10 }, ctx => { ... })` | Layer and priority (NeoForge only) |
| Network data | `Network.sendToServer('example:ping', { a: 1 })` | NBT data to send |
| JSON data | `JsonIO.write('example/save.json', { visits: 1 })` | JSON content to save |

These objects accept only the documented fields. A misspelled field name usually fails with an error instead of being silently ignored.

For full render option and post-effect examples, see [Client rendering and post effects](client-rendering_us).

<a id="wiki-section-8"></a>
## When you meet an unfamiliar API

1. Run `/nekojs probe` and read the completion and parameter hints in your editor.
2. Check the [Event reference](event-reference_us) and [Global bindings](global-bindings_us).
3. With an AI assistant, let it query the current game instance's API. See [AI-assisted scripting](ai-support_us).

An event or method with the same name in another project does not mean NekoJS has it. If completion does not show a member, treat it as missing.

<!-- wiki-nav -->

---

[Previous: Script basics](script-basics_us) · [Contents](Home) · [Next: Script properties](script-properties_us)
