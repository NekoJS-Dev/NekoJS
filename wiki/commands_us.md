<!-- wiki-page: commands; locale: us -->

> **English** · [中文](commands_cn)

<a id="wiki-section-1"></a>
# Commands

NekoJS registers the root command `/nekojs`, which requires permission level LEVEL_GAMEMASTERS (op level 2 or higher).

<a id="wiki-section-2"></a>
## Command overview

| Command | Purpose | Platform |
|---|---|---|
| `/nekojs reload` | Reload **server** scripts by default. NeoForge and Fabric 26.x also process recipe scripts afterward. | NeoForge / Fabric 26.x |
| `/nekojs reload <type>` | Reload the specified script type. `<type>` is `server` / `client` / `test`; `startup` is currently rejected, and startup script changes require a game restart. | NeoForge / Fabric 26.x |
| `/nekojs reload <type> <file>` | Reload one script file (path relative to that script type's root directory; filenames support TAB completion). | NeoForge / Fabric 26.x |
| `/nekojs test` | Run all scripts under `nekojs/test_scripts/` in the TEST script context. | NeoForge / Fabric 26.x |
| `/nekojs error` | Report whether there are currently any script errors. | NeoForge / Fabric 26.x |
| `/nekojs view_all_errors` | List full errors; NeoForge can open the error UI, while Fabric outputs chat text. | NeoForge / Fabric 26.x |
| `/nekojs editor` (historical command) | Removed; edit scripts in an external editor | Not provided on current platforms |
| `/nekojs packs` | List all script packs (global `nekojs/packs/` and world `<世界>/nekojs_packs/`, including enabled state, version, and directory). | NeoForge / Fabric 26.x |
| `/nekojs packs enable\|disable <id>` | Write the pack state file (which takes precedence over the manifest's `enabled` field); apply it with `/nekojs reload`. | NeoForge / Fabric 26.x |
| `/nekojs trust <address>` | Multiplayer script pack distribution: add the server to this client's trust store (an untrusted first connection is disconnected and shows this command; reconnect after trusting it to receive packs; execute in a single-player world or client process). | NeoForge / Fabric 26.x |
| `/nekojs registry` | Show a dynamic registration health snapshot; Fabric's snapshot is currently empty. | NeoForge 26.x; Fabric has the command but the capability is empty |
| `/nekojs registry stale` | List only dynamic entries that scripts no longer register after the last reload. | NeoForge 26.x; Fabric has the command but the capability is empty |
| `/nekojs probe` | Regenerate TypeScript declarations (`.d.ts`). | NeoForge / Fabric 26.x |
| `/nekojs probe <语言> [<名字>]` | Generate for a specified language, optionally selecting an exact backend name. | NeoForge / Fabric 26.x |
| `/nekojs probe all` / `list` / `reload` / `enable` / `disable` / `reset_config` | Run all backends / list registered backends / reread probe.toml / persist the enabled switch / restore factory editor configuration. | NeoForge / Fabric 26.x |

> Cleanroom 1.12.2 is not in the current repository's version graph. Command differences for that branch are legacy information; this page makes no verification commitment about its runtime behavior.

<a id="wiki-section-3"></a>
## Reload in detail

<a id="wiki-section-4"></a>
### `/nekojs reload` (server by default)

The most commonly used command. It hot reloads server scripts (recipes and event listeners). Reload is **transactional**: scripts first load into a candidate Context, and replace the old one only if all loading succeeds. Failure preserves the old Context rather than leaving a partially broken environment.

<a id="wiki-section-5"></a>
### `/nekojs reload <type>`

| type | Behavior |
|---|---|
| `server` | As above. |
| `client` | Reload client scripts (available only on an integrated client). The command returns immediately; the actual reload is **dispatched to the client main thread**, which owns the client script environment (events/timers). This avoids cross-thread overlap between reload and render-time events. |
| `test` | Reload test scripts. |
| `startup` | Currently rejected by the command. Startup scripts run during game startup, and changes to registered content require a game restart. |

<a id="wiki-section-6"></a>
### `/nekojs reload <type> <file>`

Reload only one file, which is faster than a full reload. Examples:

```
/nekojs reload server recipes.js
/nekojs reload client tooltips.js
```

The file path is relative to the corresponding script type's root directory (for example, `recipes.js` is relative to `nekojs/server_scripts/`). Filenames support TAB completion.

<a id="wiki-section-7"></a>
## Hand / inventory in detail

<a id="wiki-section-8"></a>
### `/nekojs hand`

A development and debugging command that displays full information about the executor's main-hand item.

```
/nekojs hand
minecraft:diamond_sword x1          <- item id (click to copy), count
  damage: 5/1561                     <- shown only for damageable items
  components: {minecraft:enchantments=>{levels={minecraft:sharpness=5}}}   <- component patch (script-modified components appear here; {} = none)
```

An empty main hand displays `Empty hand`. Execution from the console fails because a player is required.

<a id="wiki-section-9"></a>
### `/nekojs inventory`

List all nonempty slots in the executor's main inventory (slots 0–35). Each line has the form `slot N: <物品id> x<数量>`; an entirely empty inventory displays
`Inventory is empty.`. This command also requires a player executor.

Both commands require game master permissions (permission level 2), like the other `nekojs` subcommands.

<a id="wiki-section-10"></a>
## Platform differences in recipe reload

| Platform | Effect of `/nekojs reload server` on recipes |
|---|---|
| **Fabric 26.x** | Supports recipe hot reload; the script surface is the current Fabric recipe subset. |
| **NeoForge (26.x / 1.21.1)** | Supports hot reload. `RecipeManagerMixin` caches the original recipe JSON from the data pack phase. Reload rebuilds the working set, reruns `ServerEvents.recipes`, and replaces the `RecipeManager`; recipe logic takes effect immediately, while viewers may need a client refresh. |

> Cleanroom 1.12.2 recipe behavior belongs to an independent legacy branch and is not verified by the current repository. |

> NeoForge hot reload uses `ReloadableServerResourcesMixin` (triggered when resource reload finishes) and the reentrant `RecipeManagerMixin.nekojs$applyScripts()`. `/nekojs reload server` calls it explicitly after script reload. Recipes added, changed, or removed by scripts are parsed back into the `RecipeMap`.

<a id="wiki-section-11"></a>
## Probe in detail

<a id="wiki-section-12"></a>
### Subcommands

| Command | Behavior |
|---|---|
| `/nekojs probe` | With no arguments, run only the built-in TS backend (`typescript:builtin`) and generate `.d.ts` files under `.neko_probe/typescript/`. |
| `/nekojs probe <语言>` | Run that language's default backend: `languages.<lang>.backend` in `probe.toml` takes precedence; otherwise choose the backend with the highest priority for that language. For example, `/nekojs probe python` generates the `.pyi` stub package under `.neko_probe/python/nekojs/`. |
| `/nekojs probe <语言> <名字>` | Select an exact backend, for example `/nekojs probe typescript builtin`. |
| `/nekojs probe all` | Run every registered backend across languages. |
| `/nekojs probe list` | List registered backends (`语言:名字 (来源)`). |
| `/nekojs probe reload` | Discard the probe.toml configuration cache and reread from disk next time (configuration changes do not require a game restart). |
| `/nekojs probe enable` / `disable` | Persist `enabled` in probe.toml and reload the cache. |
| `/nekojs probe reset_config` | Delete editor configuration managed by each backend (jsconfig/pyrightconfig/snippets), preserve user-defined entries, and then regenerate default TS declarations; supported on both NeoForge and Fabric 26.x. |

`<语言>` and `<名字>` support TAB completion.

> Successful generation shows the output directory, for example `Output: .neko_probe/typescript`. Another invocation during a run returns `probe already running` without queuing. `runAtStartup = true` in `probe.toml` runs the default probe on server startup; it is disabled by default. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-13"></a>
### What one probe run does

1. Collect all current bindings, events, adapters, recipe schemas, and registry contents.
2. Use BFS from seed classes to collect related Java classes (depth and package filters are controlled by `probe.toml`).
3. If `probe.modifyType`/`probe.assignType` have listeners, or the Python backend is selected, build the shared IR and trigger these SERVER events.
4. Render each backend's output: first render everything in memory, then **synchronize the output directory in place, file by file** (skip unchanged files, overwrite changed files, and delete files no longer produced). If rendering fails, do not touch the disk; preserve the old output.
5. Merge editor configuration (TS → jsconfig paths/include/typeRoots; Python → extraPaths in each directory's pyrightconfig and `python.analysis.extraPaths`/`python.languageServer` in each directory's `.vscode/settings.json`, through generic injection that changes only probe-owned keys).

> After adding a mod or event, or registering new content in scripts, run `/nekojs probe` to refresh declarations. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-14"></a>
## Historical editor command

The in-game workspace editor and `/nekojs editor` have been removed. Edit scripts in an external editor such as VS Code and use probe for type information. The read-only error UI does not edit or upload scripts.

<a id="wiki-section-15"></a>
## Error / view_all_errors

- `/nekojs error`: quickly report whether errors exist (yes/no and a short summary).
- `/nekojs view_all_errors`: list full errors. NeoForge passes the error list to its error UI; Fabric outputs a text list in chat.

Script errors do not crash the game. They are caught and recorded in `logs/nekojs/<type>.log`, and the failing script is marked disabled until a successful reload clears that mark.

<a id="wiki-section-16"></a>
## Permissions

Every `/nekojs` subcommand requires **op level 2 or higher** (`LEVEL_GAMEMASTERS`). In single-player, a player with cheats enabled satisfies this requirement automatically.

<a id="wiki-section-17"></a>
## Registering custom commands

Scripts can register their own commands in `CommandEvents.register`, which fires on each server startup. The API differs by platform:

- **NeoForge (26.x / 1.21.1)**: `event` is the Brigadier `RegisterCommandsEvent`; use `event.getDispatcher()` with a `.literal(...).executes(...)` chain.
- **Cleanroom 1.12.2**: `event` is `FMLServerStartingEvent`. There is no Brigadier in 1.12.2; scripts must implement `ICommand` (usually by extending `CommandBase`) and call `event.registerServerCommand(cmd)`.

For full examples and field differences, see [Event reference - CommandEvents](event-reference_us#wiki-section-18).

<a id="wiki-section-18"></a>
## Next steps

- [Script basics](script-basics_us): reload behavior in detail.
- [Quick start](quick-start_us).
- [FAQ](faq_us).

<!-- wiki-nav -->

---

[Previous: Event reference](event-reference_us) · [Contents](Home) · [Next: Error and log reference](error-reference_us)
