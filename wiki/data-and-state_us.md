<!-- wiki-page: data-and-state; locale: us -->

> **English** · [中文](data-and-state_cn)

<a id="wiki-section-1"></a>
# Data and state

Scripts often need to remember things: a counter, a player's progress, some settings. This page explains where to keep data and how long it lasts.

<a id="wiki-section-2"></a>
## Choose a mechanism

| Need | Use | How long it lasts |
|---|---|---|
| Share variables between files of one script type | `global` | Until the game closes |
| Share variables between script types (single player) | `shared` | Until the game closes |
| Settings or records that survive a restart | `JsonIO` | Permanently, in a file |
| Data that belongs to a player or entity | `entity.pdata()` | Saved with the entity in the world |
| Data that belongs to an item | `stack.pdata()` | Saved with the item in the world |
| The server pushes values for the client to display | `ClientData` → `clientData` | Until the client leaves the world |
| Client and server send each other messages | `Network` + `NetworkEvents` | Not saved; handled on receipt |

For every method of these APIs, see [Global bindings](global-bindings_us).

<a id="wiki-section-3"></a>
## global and shared: in-memory sharing

Variables in a script file are visible only inside that file. To share them between files, put them on `global` or `shared`:

```javascript
// server_scripts/counter.js
global.loads = (global.loads ?? 0) + 1
shared.mode = 'cooperative'
console.info('Server scripts loaded ' + global.loads + ' times')
```

- **`global`** is shared by scripts of one type. All server scripts share one `global`; client scripts have a different one, and neither sees the other.
- **`shared`** is shared by every script type, but only inside one game process. In single player, a server script can write a value and a client script can read it. In multiplayer, the server and each player's client are separate processes and cannot see each other's `shared`.

`global` is not JavaScript's `globalThis`. Values set on `globalThis` are not shared.

<a id="wiki-section-4"></a>
### When the data is cleared

- **Reload**: kept. Each `/nekojs reload` adds 1 to `global.loads` in the example above.
- **Leaving a world and joining another**: kept. Do not treat it as per-world data.
- **Closing the game**: everything is cleared. Use `JsonIO` or PData for data that must last.

After a reload, JavaScript objects, arrays and functions stored **by the previous load** are removed. Only numbers, strings and booleans remain. To keep complex data across reloads, store it as a JSON string or rebuild it on every load.

When a reload fails, its `global.xxx = ...` assignments are discarded. But if it changed the contents of an existing object (for example `global.box.v = 2`), that change stays.

<a id="wiki-section-5"></a>
## JsonIO: saving to a file

`JsonIO` saves JSON to files under `nekojs/data/`. They survive a restart:

```javascript
// server_scripts/visits.js
const saved = JsonIO.read('example_store/visits.json')
const visits = saved === null ? 0 : JSON.parse(saved.toString()).visits
JsonIO.write('example_store/visits.json', { visits: visits + 1 })
```

- `JsonIO.read(path)` returns `null` when the file does not exist. Otherwise it returns a JSON value; call `toString()` to get the JSON text.
- `JsonIO.write(path, value)` accepts a plain object, array, string, number, boolean or `null`, and creates folders as needed. The write is atomic: the file is either fully written or left unchanged, never half-written.

Path rules:

- Use a relative path with `/`, such as `mymod/settings.json`, starting from `nekojs/data/`.
- Absolute paths, drive letters, backslashes, `.` and `..` are rejected.

Read and write only when you need to. `JsonIO` does not save `global` for you and does not sync anything to clients.

Files are not per world: every world reads and writes the same file. If you need per-world data, put a distinguishing name in the file name yourself.

`nekojs/data/` is also NekoJS's data pack folder. Avoid data pack folder names such as `recipe`, `tags` or `loot_table` inside it, or the game will load your JSON as data pack content.

Limits: at most 64 levels of nesting, 10,000 values, and about one million characters of text. Exceeding a limit, invalid JSON, functions or circular references all throw an error.

<a id="wiki-section-6"></a>
## PData: data on entities and items

PData is a small piece of NBT attached to an entity or item and saved with the world. Call `pdata()` first, then use the typed methods.

<a id="wiki-section-7"></a>
### Entities and players

```javascript
// server_scripts/visits.js (NeoForge; on Fabric, replace event.entity with event.player)
PlayerEvents.loggedIn(event => {
  const data = event.entity.pdata()
  const visits = data.getInt('example:visits') + 1
  data.putInt('example:visits', visits)
  event.entity.sendMessage('Visit #' + visits)
})
```

Common methods:

- Read: `getInt`, `getLong`, `getDouble`, `getString`, `getBoolean`, `getCompound` and so on. A missing key returns 0, `''` or `false`. When "missing" must differ from "zero", check `contains(key)` first.
- Write: `putInt`, `putString`, `putBoolean`, `putCompound` and so on, plus `remove(key)` and `clear()`.
- Change several values at once: `data.edit(tag => { tag.putInt('a', 1); tag.putString('b', 'x') })`. If the callback throws, none of the batch is saved.

`getCompound` returns a copy. Write it back with `putCompound` after changing it.

How the data is saved and synced:

- After a write on the server, NekoJS syncs the data to players who can see the entity. You do not need to do anything else.
- The data is stored with the entity and written to disk by the game's normal world saving.
- When a player respawns after death or returns from the End, the data moves to the new player entity automatically.
- **Clients can read but not write.** Writing from a client script throws `NEKO-4013`. The client reads a copy synced from the server, which can lag slightly.

Prefix your keys, for example `example:visits`, to avoid clashing with other scripts.

<a id="wiki-section-8"></a>
### Items

```javascript
const stack = Item.of('minecraft:paper')
stack.pdata().putString('example:owner', 'Steve')
```

Item PData is stored in the vanilla `minecraft:custom_data` component and travels with the item: into a chest, onto the ground, to another player. The item must actually end up in an inventory or the world to be saved.

Other mods and data packs may use the same component. Prefix your keys; `clear()` removes the **whole** component, including other people's data.

Change item data on the server. Changing an item on the client does not change the real item on the server.

<a id="wiki-section-9"></a>
## ClientData: pushing values to the client

The server can push values to clients, and client scripts can use them for a HUD or screen.

```javascript
// server_scripts/status.js (NeoForge; on Fabric, replace event.entity with event.player)
PlayerEvents.loggedIn(event => {
  ClientData.syncTo(event.entity, 'example:status', { ready: true, score: 10 })
})
```

```javascript
// client_scripts/status.js
function readStatus() {
  if (!clientData.has('example:status')) return null
  return clientData.get('example:status')
}
```

- Server: `ClientData.sync(key, value)` sends to every online player; `ClientData.syncTo(player, key, value)` sends to one player. Sending the same key again replaces the old value.
- Client: `clientData.get(key)`, `clientData.has(key)`, `clientData.keys()`, `clientData.size()`. `get` returns `null` for a missing key.
- Values must be representable as JSON: objects, arrays, strings, numbers, booleans or `null`.
- Keys must not be blank and can be at most 256 characters. A value can be at most 32,768 characters as JSON; larger values throw an error.

All of this data is cleared when the client leaves the world or disconnects. A reload does not clear it. The server must send it again every time a player joins, so it usually goes in a login event.

<a id="wiki-section-10"></a>
## Network: messages between client and server

When receiving a message should trigger an action, send it with `Network` and receive it with `NetworkEvents`. Messages are told apart by channel name:

```javascript
// client_scripts/refresh.js
Network.sendToServer('example:refresh', { page: 1 })
```

```javascript
// server_scripts/refresh.js
NetworkEvents.server('example:refresh', event => {
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

- Sending: clients use `Network.sendToServer(channel, data)`; the server uses `Network.sendToPlayer(player, channel, data)` or `Network.sendToAll(channel, data)`.
- Receiving: server scripts use `NetworkEvents.server(channel, event => { ... })`; client scripts use `NetworkEvents.client(channel, event => { ... })`.
- `event.data` is the received NBT. On the server, `event.player` is the player who sent the message.
- `data` can be a plain object and is converted to NBT. Passing `null` sends empty data.
- Channel names must not be blank and can be at most 64 characters.

Messages are not saved. To keep something, write it to PData or `JsonIO` in your handler.

Messages the server receives come from a player's client and can be forged. Before acting on one, check that the player is allowed to do this, is not sending too often, and sent well-formed data. Trust `event.player` for the sender, never a player name inside the data.

<a id="wiki-section-11"></a>
## Notes

- **Large PData is not synced.** When an entity's PData exceeds 32,768 characters as text, it is not synced to clients, the log shows `NEKO-3010`, and clients keep the last value they received. Saving is not affected. PData is meant for small amounts of data; use `JsonIO` for large data.
- **You rarely need `markDirty()` or `sync()`.** Writes sync automatically. `sync()` sends immediately instead of waiting for the next tick; neither one saves to disk.
- **Startup registrations cannot be reloaded.** Registering new content needs a game restart. See [Script basics](script-basics_us).
- **Old Fabric worlds.** Early Fabric versions could save entity PData one level too deep, as a same-named entry under `NekoJSPersistentData`. Current versions do not change such data automatically. If old data seems missing after an upgrade, try `data.getCompound('NekoJSPersistentData')` and migrate once you have confirmed it. Back up the world first.

<!-- wiki-nav -->

---

[Previous: Global bindings](global-bindings_us) · [Contents](Home) · [Next: Python scripts](python-scripts_us)
