<!-- wiki-page: commands; locale: us -->

> **English** · [中文](commands_cn)

<a id="wiki-section-1"></a>
# Commands

The server command root `/nekojs` requires game master permissions (op level 2 or higher). The client command `/nekojs browse` opens a local directory and requires no server permission.

<a id="wiki-section-2"></a>
## Command overview

| Command | Purpose | Platform |
|---|---|---|
| `/nekojs reload` | Reload **server** scripts by default. NeoForge and Fabric 26.x also process recipe scripts afterward. | NeoForge / Fabric 26.x |
| `/nekojs reload <type>` | Reload the specified script type. `<type>` is `server` / `client` / `test`; `startup` is currently rejected, and startup script changes require a game restart. | NeoForge / Fabric 26.x |
| `/nekojs reload <type> <file>` | Rerun a legacy file in the active Context; dependencies may rerun affected entries. Paths are relative to the script-type root; filenames support TAB completion. | NeoForge / Fabric 26.x |
| `/nekojs test` | Run all scripts under `nekojs/test_scripts/` in the TEST script context. | NeoForge / Fabric 26.x |
| `/nekojs error` | Report whether there are currently any script errors. | NeoForge / Fabric 26.x |
| `/nekojs view_all_errors` | List full errors; NeoForge can open the error UI, while Fabric outputs chat text. | NeoForge / Fabric 26.x |
| `/nekojs editor` (historical command) | Removed; edit scripts in an external editor | Not provided on current platforms |
| `/nekojs packs` | List packs from mounted sources, configured enablement, versions, and directories. Remote cache packs are authorization controlled; configuration is not runtime activation. | NeoForge / Fabric 26.x |
| `/nekojs packs enable\|disable <selector>` | Save local enablement using `GLOBAL:my_pack` / `WORLD:my_pack`, or a unique bare ID. Save failures return failure. Reload the relevant type to apply; STARTUP requires a restart. Remote cache packs are read-only. | NeoForge / Fabric 26.x |
| `/nekojs packs status <type>` | Show completed entry outcomes, original versions, and failure isolation for the current type generation without rescanning or evaluating scripts. | NeoForge / Fabric 26.x |
| `/nekojs packs plan <type>` | Show current file order, exclusions, and ordering diagnostics without evaluating or compiling scripts. This is not candidate activation validation. | NeoForge / Fabric 26.x |
| `/nekojs trust <address>` | Multiplayer script pack distribution: add the server to this client's trust store (an untrusted first connection is disconnected and shows this command; reconnect after trusting it to receive packs; execute in a single-player world or client process). | NeoForge / Fabric 26.x |
| `/nekojs registry` | Show a dynamic registration health snapshot; Fabric's snapshot is currently empty. | NeoForge 26.x; Fabric has the command but the capability is empty |
| `/nekojs registry stale` | List only dynamic entries that scripts no longer register after the last reload. | NeoForge 26.x; Fabric has the command but the capability is empty |
| `/nekojs hand` | Copy main-hand item/component data and item/block/fluid tags | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs hotbar` / `inventory` | Export component strings and complete JSON arrays | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs list-tag <registry> [<tag>]` | List registry tags or their members, with completion | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs browse` | Open the local `nekojs` folder; client only, no administrator permission | NeoForge 1.21.1/26.x / Fabric 26.x |
| `/nekojs probe` | Regenerate TypeScript declarations (`.d.ts`). | NeoForge / Fabric 26.x |
| `/nekojs probe <language> [<name>]` | Generate for a specified language, optionally selecting an exact backend name. | NeoForge / Fabric 26.x |
| `/nekojs probe all` / `list` / `reload` / `enable` / `disable` / `reset_config` | Run all backends / list registered backends / reread probe.toml / persist the enabled switch / restore factory editor configuration. | NeoForge / Fabric 26.x |

> Cleanroom 1.12.2 is not in the current repository's version graph. Command differences for that branch are legacy information; this page makes no verification commitment about its runtime behavior.

<a id="wiki-section-3"></a>
## Reload in detail

<a id="wiki-section-4"></a>
### `/nekojs reload` (server by default)

This explicitly requests a full SERVER generation reload. For ordinary SERVER/CLIENT/TEST reloads, preparation and execution happen in candidate Contexts before activation; a failed candidate preserves the healthy active generation. Managed listeners, timers and staged state switch at commit. The runtime root and Plugin Runtime are not rebuilt.

This is not rollback of arbitrary Java calls, world changes, filesystem writes or nested mutations of shared objects made by script code. After a successful SERVER script reload, the command separately applies recipe changes and refreshes pack data resources where supported; a later resource failure does not undo the script commit. Versioned packs also follow their dependency/domain affected-set rules; see [Script packs](script-packs_us).

A scheduled CLIENT command is not a completed reload: wait for its later result. A committed SERVER/CLIENT/TEST generation is not certification that every script behavior or assertion is correct; inspect output and errors. The separately authorized [live authoring controls](ai-support_us#wiki-live-controls) expose full `server`/`client`/`test` reloads only when the corresponding live runtime is available, not STARTUP or file reloads. Their wait timeout is not a new timeout on ordinary slash commands: after execution starts, timeout/interruption may report unknown effects while the operation continues and commits later. **Do not automatically retry an action with unknown effects**; inspect runtime state and diagnostics first.

<a id="wiki-section-5"></a>
### `/nekojs reload <type>`

| type | Behavior |
|---|---|
| `server` | As above. |
| `client` | Reload client scripts (available only on an integrated client). The command returns immediately; the actual reload is **dispatched to the client main thread**, which owns the client script environment (events/timers). This avoids cross-thread overlap between reload and render-time events. |
| `test` | Run the TEST reload-and-run flow, as `/nekojs test` does, including the timer flush. Completion is not a promise that every `Test` assertion passed; inspect the test output and script errors. |
| `startup` | Currently rejected by the command. Startup scripts run during game startup, and changes to registered content require a game restart. |

The TEST command reports execution and assertion outcomes separately, with per-attempt counts: a caught failed assertion remains failed, and zero assertions is reported as unobserved rather than passed. Live `context.testRunReport` exposes the last finished attempt (including legacy and package scripts), not progress or all future callbacks; see [queryable runtime results](ai-support_us#wiki-live-results).

<a id="wiki-section-6"></a>
### `/nekojs reload <type> <file>`

Rerun a legacy file in the **existing active Context**. Dependency tracking may also invalidate modules and rerun affected entry scripts; this is not necessarily one-file execution, and there is no full-generation rollback on failure. Examples:

```
/nekojs reload server recipes.js
/nekojs reload client tooltips.js
```

The file path is relative to the corresponding script type's root directory (for example, `recipes.js` is relative to `nekojs/server_scripts/`). Filenames support TAB completion. Versioned pack files must use package/type reload instead. Use a full reload when the dependency cannot be matched or an active generation was invalidated by host cancellation or resource exhaustion; a file rerun cannot recover that generation.

**Reload triggers are separate:** saving a file does not automatically reload it; there is no documented filesystem watcher or auto-reload switch. Module dependency invalidation (sometimes called HMR internally) is part of the explicit reload/load path, not a save watcher. Native `/reload` refreshes server data packs, and F3+T refreshes client resources; neither is a replacement for script generation reload. Pack selection/enablement and pack data/resource refresh are distinct from activating script generations.

<a id="wiki-section-7"></a>
## Items, tags, and the local directory

The four server information commands share one implementation across NeoForge 1.21.1, NeoForge 26.x, and Fabric 26.x. They retain the existing `/nekojs` administrator requirement.

<a id="wiki-section-8"></a>
### `/nekojs hand`

Inspect the executor's main-hand item. Click a row to copy it; hover for its description:

- An item string such as `"minecraft:iron_sword[damage=2]"` or `"3x minecraft:stone"`, accepted by `Item.of(...)`. Component values use vanilla SNBT; `!component` preserves removals. JSON quoting safely escapes copied JS/TS literals.
- Complete item JSON, including count and component overrides/removals, without truncating it to a debug summary.
- Item tags with member counts, the namespace with its item count, and populated creative tabs with their item counts.
- For block items, the block ID and tags. For fluid containers, the first contained fluid's ID and tags, using each loader's native container API without consuming items or fluid.

Creative tabs copy their raw tab IDs rather than KubeJS `%tab` selectors, which NekoJS does not currently accept.

An empty hand produces a localized message. A player is required; console execution fails.

Complete JSON uses Minecraft's `ItemStack.CODEC` format. For reconstruction, copy the component string: all five nodes support it through `Item.of(string)` and the ItemStack adapter, also inside `{id: "minecraft:iron_sword[damage=2]", count: 1}`. Full JSON objects remain a separate inspection format; they are not a cross-version `Item.of(object)` contract. The 1.21.1 object adapter still does not read a `components` field.

Parsing uses live server or client connection registries, falling back to built-in registries before a world exists. Unknown/duplicate components, malformed values and trailing input are rejected. `Item.id` still accepts only an item type ID.

<a id="wiki-section-9"></a>
### `/nekojs hotbar` / `/nekojs inventory`

`hotbar` reads slots 0–8; `inventory` reads main inventory slots 0–35. Neither includes equipment or the offhand. Nonempty stacks keep their order and duplicates; empty slots are filtered, and an empty inventory exports `[]`.

Each command emits two copyable arrays: item strings retaining components and complete item JSON retaining components. For example, `["2x minecraft:stone","minecraft:apple"]` is an array whose entries can be passed to `Item.of`. Empty slot positions are not retained. Both commands require a player.

**`/nekojs list-tag <registry> [<tag>]`**

Query the current server's live registries; console execution is supported. Both arguments provide TAB completion. Use `minecraft:item`, `minecraft:block`, or another loaded registry.

```text
/nekojs list-tag minecraft:item
/nekojs list-tag minecraft:item minecraft:logs
```

With only a registry, list its tags and their total count. Clicking a tag runs the correctly spelled `/nekojs list-tag ...` command. With a tag, list copyable member IDs and the entry count. Unknown registries and missing tags fail explicitly; an existing empty tag reports zero entries normally.

**`/nekojs browse`**

A client command that opens the **local game directory's `nekojs` folder** in the system file manager, creating it if absent. In multiplayer it still opens the local folder rather than the remote server's directory. Available on NeoForge 1.21.1/26.x and Fabric 26.x clients, with no arguments or administrator requirement. Dedicated servers do not register it.

<a id="wiki-section-10"></a>
## Platform differences in recipe reload

| Platform | Effect of `/nekojs reload server` on recipes |
|---|---|
| **Fabric 26.x** | Supports recipe hot reload; the script surface is the current Fabric recipe subset. |
| **NeoForge (26.x / 1.21.1)** | Supports hot reload. `RecipeManagerMixin` caches the original recipe JSON from the data pack phase. Reload rebuilds the working set, reruns `ServerEvents.recipes`, and replaces the `RecipeManager`; recipe logic takes effect immediately, while viewers may need a client refresh. |

> Cleanroom 1.12.2 recipe behavior belongs to an independent legacy branch and is not verified by the current repository.

> NeoForge hot reload uses `ReloadableServerResourcesMixin` (triggered when resource reload finishes) and the reentrant `RecipeManagerMixin.nekojs$applyScripts()`. `/nekojs reload server` calls it explicitly after script reload. Recipes added, changed, or removed by scripts are parsed back into the `RecipeMap`.

<a id="wiki-section-11"></a>
## Probe in detail

<a id="wiki-section-12"></a>
### Subcommands

| Command | Behavior |
|---|---|
| `/nekojs probe` | With no arguments, run only the built-in TS backend (`typescript:builtin`) and generate `.d.ts` files under `.neko_probe/typescript/`. |
| `/nekojs probe <language>` | Run that language's default backend: `languages.<lang>.backend` in `probe.toml` takes precedence; otherwise choose the backend with the highest priority for that language. For example, `/nekojs probe python` generates the `.pyi` stub package under `.neko_probe/python/nekojs/`. |
| `/nekojs probe <language> <name>` | Select an exact backend, for example `/nekojs probe typescript builtin`. |
| `/nekojs probe all` | Run every registered backend across languages. |
| `/nekojs probe list` | List registered backends (`language:name (source)`). |
| `/nekojs probe reload` | Discard the probe.toml configuration cache and reread from disk next time (configuration changes do not require a game restart). |
| `/nekojs probe enable` / `disable` | Persist `enabled` in probe.toml and reload the cache. |
| `/nekojs probe reset_config` | Delete editor configuration managed by each backend (jsconfig/pyrightconfig/snippets), preserve user-defined entries, and then regenerate default TS declarations; supported on both NeoForge and Fabric 26.x. |

`<language>` and `<name>` support TAB completion.

> Successful generation shows the output directory, for example `Output: .neko_probe/typescript`. Another invocation during a run returns `probe already running` without queuing. `runAtStartup = true` in `probe.toml` runs the default probe on server startup; it is disabled by default. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-13"></a>
### What one probe run does

1. Collect all current bindings, events, adapters, recipe schemas, and registry contents.
2. Use BFS from seed classes to collect related Java classes (depth and package filters are controlled by `probe.toml`).
3. Build the shared IR when a backend requires it (both built-in TypeScript and Python do), or when `ProbeEvents.modifyType`/`ProbeEvents.assignType` have listeners. Registered listeners run as SERVER events; register them in SERVER scripts.
4. With the default backend generation flow, render declaration files in memory, then **synchronize that backend's output directory in place, file by file** (skip unchanged files, overwrite changed files, remove stale files). Rendering failure preserves that declaration output; a write failure can leave a partially updated directory, and stale-file deletion failures produce warnings. This does not roll back workspace/editor artifacts already generated earlier in the run.
5. Merge editor configuration (TS → jsconfig paths/include/typeRoots; Python → extraPaths in each directory's pyrightconfig and `python.analysis.extraPaths`/`python.languageServer` in each directory's `.vscode/settings.json`, through generic injection that changes only probe-owned keys).

> After adding a mod or event, or registering new content in scripts, run `/nekojs probe` to refresh declarations. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-14"></a>
## Historical editor command

The in-game workspace editor and `/nekojs editor` have been removed. Edit scripts in an external editor such as VS Code and use probe for type information. The read-only error UI does not edit or upload scripts.

<a id="wiki-section-15"></a>
## Error / view_all_errors

- `/nekojs error`: quickly report whether errors exist (yes/no and a short summary).
- `/nekojs view_all_errors`: list full errors. NeoForge passes the error list to its error UI; Fabric outputs a text list in chat.

On NeoForge, opening the error UI requires a player when errors exist; a non-player source cannot receive that packet. Use `/nekojs error` for a console summary. Fabric's text list also works without a player.

NeoForge 26.1.2/26.2.0 use a bundled JSX dashboard with real error snapshots, search, independent detail scrolling, verified copy actions and checked asynchronous VS Code opening. Players running `/nekojs view_all_errors` always open the dashboard, showing an empty state when there are no errors; `/nekojs error` remains a chat summary, and passive snapshot updates do not open a closed dashboard. It is independent of user CLIENT script generations; a bundled UI failure falls back to the existing native dashboard. NeoForge 1.21.1 retains its native UI. Command permissions, packet fields and the chat-link opening flow remain unchanged.

Entry evaluation failures are caught, recorded in `logs/nekojs/<type>.log` and mark that entry disabled; successful entry execution clears its mark. Callback/event errors are recorded separately, so not every error means a disabled entry. A terminated Context isolates its generation and requires explicit full reload recovery; an ordinary failure is not a promise of automatic recovery.

<a id="wiki-section-16"></a>
## Permissions

Server `/nekojs` subcommands require **op level 2 or higher** (26.x: `LEVEL_GAMEMASTERS`; NeoForge 1.21.1: `hasPermission(2)`). In single-player, a player with cheats enabled satisfies this requirement. Lifecycle commands require a server command source, not necessarily a player: a suitably permitted server console can request SERVER or TEST reloads, while CLIENT reload requires an integrated client runtime. Player-only commands such as `hand`, `hotbar` and `inventory` cannot run from a non-player source. Authoring command execution uses a fixed non-player “NekoJS Authoring” source with gamemaster/level-2 permissions, no entity, zero position/rotation and the overworld; it does not impersonate a player or grant unrestricted console permissions. The local client `/nekojs browse` command requires no administrator permission.

<a id="wiki-section-17"></a>
## Registering custom commands

Scripts can register commands in `CommandEvents.register` during server command-tree construction. The payload differs by platform; the dispatcher registers a builder created with `Commands.literal(...)`, rather than providing a `dispatcher.literal(...)` method:

- **NeoForge (26.x / 1.21.1)**: `event` is the native `RegisterCommandsEvent`; access its dispatcher as `event.dispatcher` and register the Brigadier builder.
- **Fabric 26.x**: `event` is `CommandRegistryEventJS`, exposing `event.dispatcher` and `event.registryAccess` Bean properties. Register the builder with the dispatcher; `registryAccess` supplies the command build context for argument types.

Cleanroom custom-command APIs are not verified by the current repository.

For full examples and field differences, see [Event reference - CommandEvents](event-reference_us#wiki-section-18).

<a id="wiki-section-18"></a>
## Next steps

- [Script basics](script-basics_us): reload behavior in detail.
- [Quick start](quick-start_us).
- [FAQ](faq_us).

Versioned packs also provide creation, strict validation, archives, mod JAR exports and package replacement. See [Versioned script packs](script-packs_us.md) for commands, dependencies, capabilities and compatibility boundaries.

<!-- wiki-nav -->

---

[Previous: AI-assisted authoring](ai-support_us) · [Contents](Home) · [Next: Error and log reference](error-reference_us)
