<!-- wiki-page: data-and-state; locale: us -->

> **English** · [中文](data-and-state_cn)

<a id="wiki-section-1"></a>
# Data, lifetime, and authority

Choose the **owner**, then the lifetime and transport. Sharing memory, saving data, and sending a packet are three different operations. For signatures, see [global/shared](global-bindings_us#wiki-section-41), [JsonIO](global-bindings_us#wiki-section-19), [entity/player PData](global-bindings_us#wiki-section-4), [ItemStack PData](global-bindings_us#wiki-section-3), [ClientData/clientData](global-bindings_us#wiki-section-25), and [Network](global-bindings_us#wiki-section-37).

<a id="wiki-section-2"></a>
## Choose a mechanism

| Need | Use | Authority and lifetime |
|---|---|---|
| Cache or counter shared by files of one script type | `global` | Private to that `ScriptType` in one runtime root; memory only |
| Deliberate sharing between script types in one runtime | `shared` | Same runtime root/process only; not client/server networking |
| Small explicit JSON file that survives restart | `JsonIO.read/write` | Local installation data directory; script decides when to read/write |
| Saved data belonging to an entity/player | `entity.pdata()` / `player.pdata()` | Server entity is authoritative; clients only read synchronized mirrors |
| Data traveling with an item stack | `stack.pdata()` | Vanilla `CUSTOM_DATA` component; normal stack save/sync rules |
| Latest server-provided value for a client UI | `ClientData.sync/syncTo` → `clientData` | Ephemeral client cache, not a save or request protocol |
| A request, action, or custom response | `Network` + `NetworkEvents` | Explicit messages; handlers validate and optionally store results |

<a id="wiki-section-3"></a>
## Memory is not a save

`global` is a NekoJS state container, **not** JavaScript's `globalThis`. Ordinary files of the same type share it; SERVER and CLIENT do not share their private `global`. `shared` explicitly crosses types only inside the same runtime root. A dedicated server and a remote client have separate roots and processes.

| Boundary | What to expect |
|---|---|
| Whole-type ordinary reload | Root-owned stores remain. Candidate top-level assignments/deletions/`clear()` publish only on successful commit; closing the old generation evicts its guest-owned values |
| Single-file reload / entry execution | Affected entries rerun in the active environment; code can overwrite or increment existing state again. This is not the whole-generation transaction |
| Server stop or world switch in the same root | Legacy-directory stores remain; never use them as implicit world-specific saves |
| Runtime-root close or process restart | Memory is released; a new independent root starts empty |

Opt-in script packs have private `global` scopes by script type **and source instance**. World/connection scope teardown releases those private stores; `shared` stays root-owned. Do not generalize the legacy-directory world-switch behavior to every pack.

Keep durable cross-reload state as simple values. Objects/functions owned by a closing GraalJS generation are not durable references. Candidate isolation covers managed **top-level** writes, not deep mutations of an existing object, file writes, packets, Java objects, or world changes.

```javascript
// server_scripts: every entry rerun increments this retained scalar.
global.exampleLoads = (global.exampleLoads ?? 0) + 1
shared.exampleMode = 'cooperative'
```

<a id="wiki-section-4"></a>
## JsonIO: explicit, constrained local persistence

Paths are relative to `<game directory>/nekojs/data`, **not the current world save**. Use separate filenames/keys if worlds or servers must not share data. A successful write replaces a file; it does not save `global`, subscribe to future changes, synchronize clients, or provide a multi-writer transaction. Reload rollback does not undo a completed file write.

```javascript
// An intentional one-time update, not an automatic save hook.
const saved = JsonIO.read('example/visits.json')
const visits = saved === null ? 0 : JSON.parse(saved.toString()).visits
JsonIO.write('example/visits.json', { visits: visits + 1 })
```

`read` returns a `$JsonValue`, or JavaScript `null` only when the file is absent. A file containing JSON `null` returns a value whose `toString()` is `"null"`. Malformed JSON/UTF-8, directories, and I/O failures throw; validate your own schema too.

- Use nonempty forward-slash relative paths. Absolute/drive paths, backslashes, empty segments, `.`/`..`, and symlink/reparse traversal are rejected.
- Values may be JSON primitives, arrays, plain objects, or `$JsonValue`; not functions, host objects, cycles, or non-finite numbers. Duplicate JSON keys are rejected.
- Bounds include depth 64, 10,000 nodes, and 1,048,576 characters for input/string/output; file reads also have a 3,145,728-byte bound. These are separate limits, not an allowance for arbitrary files.
- Writes use pretty UTF-8 JSON, a temporary file, and atomic replacement. If atomic replacement is unavailable, the write fails rather than falling back to a partial overwrite.

<a id="wiki-section-5"></a>
## Entity/player PData versus item-stack PData

<a id="wiki-section-6"></a>
### Entity/player: server-owned NBT plus a client mirror

Use the method `pdata()`, then typed methods such as `contains`, `getInt`, `putInt`, `putString`, `getCompound`, and `putCompound`. There is no generic `put(key, object)` or `save()` API. Missing typed reads return defaults; use `contains(key)` when absence differs from zero/false/empty.

```javascript
// NeoForge server_scripts: the event exposes a player property.
PlayerEvents.loggedIn(event => {
  const data = event.player.pdata()
  data.putInt('example:visits', data.getInt('example:visits') + 1)
})
```

The platform entity save container holds the `NekoJSPersistentData` payload; player-clone handling copies that payload. A server write saves to the in-memory entity container and automatically marks it dirty for synchronization; **Minecraft's entity/world save lifecycle** provides disk persistence, not an immediate flush or an external backup. The uninstalled platform-store fallback is memory only.

Arrays, compounds, and `copyTag()` are detached copies. Modify a compound and write it back with `putCompound`, or use `edit(tag => { ... })` for one copied batch. An exception in the edit callback prevents that batch from saving.

- Dirty synchronization processes at most **256 entities per server tick**; excess entries wait. `markDirty()` queues; `sync()` requests an immediate snapshot outside that budget (off-thread calls are scheduled on the server). Neither is a disk-save method.
- Snapshots go to tracking players plus the entity itself when applicable. Start tracking sends a snapshot; stop tracking/entity removal sends a clearing snapshot. Revisions prevent older packets restoring stale mirrors.
- A snapshot above **32,768 Java `String.length()` characters of SNBT** is skipped (`NEKO-3010`), not truncated. This is a synchronization limit, not a save-size limit. The dirty entry is already removed, so it is not automatically retried; a client can retain its last valid value.
- Client entity/player `pdata()` is read-only: writes, `edit`, `markDirty`, and `sync` throw `NEKO-4013`. Disconnect/world transitions clear mirrors; ordinary script reload does not. Treat the mirror as potentially absent or delayed, never as proof of authority.

**Fabric format and compatibility:** the entity mixin now retains the full `NeoForgeData` container and the bridge selects `NekoJSPersistentData` exactly once, matching the canonical NeoForge format while preserving other container fields. Regression coverage exercises the real bridge, script reads/writes, save/load, and synchronization service; it does not replace real-world restart or multiplayer acceptance.

Older Fabric saves can contain `NeoForgeData.NekoJSPersistentData.NekoJSPersistentData.<user keys>`. The correction does not guess, flatten, or delete this double wrapper: scripts see the nested compound rather than automatically promoted keys, which can require old scripts to adjust their read path. A same-named compound cannot distinguish historical wrapping from legitimate user data. Back up and manually inspect the affected entity/player saves before an intentional migration, or temporarily read the nested path; never unwrap all matching data indiscriminately.

<a id="wiki-section-7"></a>
### ItemStack: vanilla component ownership

```javascript
const stack = Item.of('minecraft:paper')
stack.pdata().putString('example:owner', 'guide')
```

This edits the stack's **entire** vanilla `CUSTOM_DATA` component, not an entity `NekoJSPersistentData` child. Keep your own namespaced keys; `clear()` removes the whole component, including other custom data. The stack must actually enter a saved inventory/world path to survive restart.

There is no entity-PData packet or client read-only guard here; inherited `markDirty()`/`sync()` have no-op hooks. Vanilla stack/component synchronization can still carry the data. A client-side edit is not an authoritative server inventory change, and creating a local stack does not distribute it.

<a id="wiki-section-8"></a>
## ClientData is a cache; Network is a protocol

On the server, `ClientData.sync(key, value)` broadcasts to connected players; `syncTo(player, key, value)` targets one. Re-pushing a key overwrites the client cache. Values are JSON-compatible data, not arbitrary Java/guest objects. Keys must be nonblank and at most **256 Java characters**; the serialized JSON sender check is **32,768 `String.length()` characters**, **not 32 KB**. Transport codecs have their own constraints; this character check alone does not guarantee delivery.

```javascript
// server_scripts: a targeted UI snapshot, not a durable save.
PlayerEvents.loggedIn(event => {
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

```javascript
// client_scripts: JSON null and a missing key both read as null.
if (clientData.has('example:status')) {
  console.log(clientData.get('example:status'))
}
console.log(clientData.keys(), clientData.size())
```

`get(key)`, `has(key)`, `keys()`, and `size()` are methods. `has` distinguishes missing from an explicitly pushed JSON `null`; `keys()` is an unordered snapshot. Receipt populates the cache without needing a script listener. It survives ordinary script reload but clears on client level unload/disconnect; the server must explicitly send fresh values for reconnect/new viewers. Sending once does not register automatic replay for future players.

Use `Network.sendToServer`, `sendToPlayer`, or `sendToAll` with `NetworkEvents.server/client` when receipt itself must trigger behavior. Payloads are `CompoundTag`; channel names are nonblank and at most 64 characters. These messages do not automatically save, synchronize PData, or populate `clientData`.

```javascript
// client_scripts: null becomes an empty CompoundTag.
Network.sendToServer('example:refresh', null)
```

```javascript
// server_scripts: publish server-selected data to the actual sender.
NetworkEvents.server('example:refresh', event => {
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

For real requests, validate permission, rate, payload shape, and current server state; derive the sender from `event.player`, not a client-supplied identity. Plain objects used as JSON/NBT/network **payloads** are legitimate data, not a replacement for event groups or named bindings.

<a id="wiki-section-9"></a>
## Script type and lifecycle limits

Binding installation filters script surfaces, but availability is not authority: entity PData follows the entity's logical side; ClientData sending and Network directions require the corresponding server/client context. Subscribe to the appropriate event group in the correct script directory; do not access a player/world at top level before one exists. STARTUP registrations are not ordinary reloadable state: registration changes require a restart. See [reload commands and boundaries](commands_us).

<a id="wiki-section-10"></a>
## Source evidence and verification boundary

These are implementation/test-source checks, not a claim of a live game test.

- [Memory ownership and pack scope](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/core/state/GlobalStateStores.java#L8-L92), [guest eviction and candidate writes](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/core/state/GlobalStore.java#L14-L29), [lifetime/guest tests](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/test/java/com/tkisor/nekojs/core/state/Ticket10GlobalStateTest.java#L548-L680), [file rerun](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java#L1432-L1502).
- [JSON path/I/O implementation](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/core/api/json/JsonFileStore.java#L49-L245), [value bounds](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/api/data/JsonValue.java#L30-L38), [file failure tests](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/test/java/com/tkisor/nekojs/core/api/json/JsonFileStoreTest.java#L28-L132).
- [Entity authority](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java#L67-L76), [typed/copy/read-only API](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/pdata/PersistentDataJS.java#L49-L164), [queue/snapshot limits](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/pdata/PDataSyncService.java#L27-L175), [boundary tests](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/test/java/com/tkisor/nekojs/wrapper/pdata/PDataSnapshotBehaviorTest.java#L44-L54).
- [NeoForge save-container bridge](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/NekoJSMod.java#L116-L133); corrected Fabric container and caller evidence: [bridge](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java#L98-L109), [save mixin](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/fabric/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataMixin.java#L53-L67), [save-shape fixture](https://github.com/NekoJS-Dev/NekoJS/blob/master/versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataSaveShapeTest.java#L99-L131), [real bridge and sync-service regressions](https://github.com/NekoJS-Dev/NekoJS/blob/master/versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricPDataPersistenceTest.java#L71-L195). These fixtures do not establish applied Mixin injection, live world persistence or multiplayer delivery.
- [Item component wrapper](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/item/PersistentDataJS.java#L9-L22), [same-stack/component-clear test](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/test/java/com/tkisor/nekojs/wrapper/pdata/ItemStackPersistentDataTest.java#L16-L27).
- [Client read methods](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/bindings/static_access/ClientDataJS.java#L29-L56), [key validation](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataStore.java#L35-L77), [serialized-character check](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataSyncJS.java#L24-L56), [client unload](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/client/NekoJSClient.java#L97-L102).
- [Network directions](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/network/NetworkJS.java#L22-L46), [channel validation](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/network/NekoScriptPayload.java#L13-L24), [sender properties](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/network/NetworkDataEventJS.java#L29-L39), [PData extension exposure](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/api/NeoForgeCatalogPlatformProvider.java#L53-L64).

<!-- wiki-nav -->

---

[Previous: Global bindings](global-bindings_us) · [Contents](Home) · [Next: Python scripts](python-scripts_us)
