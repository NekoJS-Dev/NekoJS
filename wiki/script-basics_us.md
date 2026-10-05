<!-- wiki-page: script-basics; locale: us -->

> **English** · [中文](script-basics_cn)

<a id="wiki-section-1"></a>
# Script basics

NekoJS separates scripts into directories by **script type (`ScriptType`)**. Each type has different loading times, reload behavior, and available APIs.

<a id="wiki-section-2"></a>
## Script types

| Directory | ScriptType | Purpose | Automatic loading | Reload behavior |
|---|---|---|---|---|
| `nekojs/startup_scripts/` | STARTUP | Register items/blocks/entity types/Goals. Runs once at game startup. | Yes | Command reload is unsupported; changes require a game restart |
| `nekojs/server_scripts/` | SERVER | Modify recipes, listen to server events, and interact with world state. | Yes | Hot reload with `/nekojs reload` |
| `nekojs/client_scripts/` | CLIENT | Client-only features: GUI, particles, key bindings, and client events. | Yes | Hot reload with `/nekojs reload client` (integrated client only) |
| `nekojs/test_scripts/` | TEST | Explicitly executed test scripts. | No | `/nekojs test` or `/nekojs reload test` |

<a id="wiki-section-3"></a>
## API visibility (side gating)

To prevent misuse, each script type can only access APIs for its corresponding side:

| Script type | Available APIs |
|---|---|
| STARTUP | Server APIs + client APIs (both sides are visible during startup) |
| SERVER | Server APIs only |
| CLIENT | Client APIs only |
| TEST | Server APIs only |

For example, calling `ServerEvents.recipes(...)` in `client_scripts` causes an error.

<a id="wiki-section-4"></a>
## Supported file extensions

`.js`, `.mjs`, `.cjs`, `.ts` (erasable TypeScript), `.jsx`/`.tsx` (classic runtime JSX lowering), and `.py` (built-in Python subset transpilation).

See [TypeScript and JSX](typescript-and-jsx_us).

<a id="wiki-section-5"></a>
## Load order

Scripts are sorted and loaded according to these rules:

1. **`priority` property** (higher numbers run first; default 0).
2. **`after` property** (declares dependencies; see [Script properties](script-properties_us)).
3. Scripts with the same priority are sorted by path name.

```javascript
// priority: 100
// This script runs before scripts with the default priority=0
```

<a id="wiki-section-6"></a>
## Reload behavior in detail

<a id="wiki-section-7"></a>
### Server / client / test

**Transactional hot reload**: NekoJS first loads scripts into a candidate Context. Only after all scripts succeed does it replace and close the old Context. If loading fails, the old Context is retained, keeping the server out of a partially broken state.

- `/nekojs reload` (defaults to reloading `server`)
- `/nekojs reload server` / `client` / `test`
- `/nekojs reload server path/to/file.js` (reloads only one file; the path is relative to the script type's root directory; supports TAB completion)

> Transactional reload protects the commit of a managed script generation. A failed candidate retains the active environment, but external effects on the world, files, or arbitrary Java objects are not necessarily rolled back. Reload is not a transaction over game state.

<a id="wiki-section-8"></a>
### Startup scripts

Startup scripts execute only during game startup. Registration changes require a game restart. `/nekojs reload startup` currently rejects execution; it cannot rerun STARTUP scripts through an in-game command.

<a id="wiki-section-9"></a>
### Recipe hot reload

| Platform | Recipe reload |
|---|---|
| **Cleanroom 1.12.2** | Separate legacy branch; this repository does not verify recipe reload or viewer refresh behavior. |
| **Fabric 26.x** | Supports rerunning recipe scripts through `/nekojs reload server`; the current node implementation determines the recipe surface. |
| **NeoForge (26.x / 1.21.1)** | Supported. `/nekojs reload server` rebuilds the working set from cached `baseJsons`, reruns recipe scripts, and replaces `RecipeManager` (immediately effective on the server); the JEI/REI viewer may need a client refresh. |

<a id="wiki-section-10"></a>
## Global variables and scope

- Each script file has its own module scope (ESM) or function scope (CJS).
- **`global`** is a state container shared within the current `ScriptType` and retained after ordinary reload. A SERVER `global.counter` does not automatically appear as CLIENT `global.counter`.
- **`shared`** is an explicit state container for sharing across script types within the same NekoJS runtime. It only provides in-process memory sharing, not network synchronization or save persistence.

```javascript
// server_scripts/a.js
global.counter = 0
shared.message = 'server is ready'

// server_scripts/b.js (still readable after ordinary reload)
ServerEvents.tickPost(event => {
  global.counter = (global.counter || 0) + 1
})

// client_scripts/hud.js (cross-type reads must use shared)
const message = shared.message
```

See the `global` and `shared` entries in [Global bindings](global-bindings_us).

<a id="wiki-section-11"></a>
## Error handling

- NekoJS catches script exceptions and records them in `logs/nekojs/<type>.log`; they do not crash the game.
- Error reports include the file, line, and column, along with a source excerpt (`>` marks the error line and `^` points to the error column). Syntax errors (SyntaxError) also identify the relevant line/column.
- Use `/nekojs error` to check whether there are any script errors.
- Use `/nekojs view_all_errors` to send the full error list to chat.
- A script with an error is marked disabled so it does not repeatedly report errors; reloading after fixing it clears the error marker.

<a id="wiki-section-12"></a>
### Static checks before loading (preflight)

During script loading/reloading, NekoJS performs **advisory** static checks on the source (they do not block execution). Results also appear in the error panel and logs:

- **Member spelling checks** for global bindings (`Item`/`Ingredient`/`Utils`, etc.) and event callback parameters (for example, `event.rec` suggests similar members related to `recipes`).
- Type flow through chained calls (`Item.of('x').typo()`), local variable types, and unknown identifiers (`Util.x`, calls to undeclared functions).

These checks are heuristic. Dynamic member access (`Utils[key]`) and bare identifiers in cases such as `typeof x` are neither incorrectly flagged nor covered. To disable false positives or unwanted messages, set `scriptMemberValidation = false` in `nekojs/config/engine.toml`.

<a id="wiki-section-13"></a>
## Workspace and IDE configuration

When first creating a workspace, NekoJS generates `jsconfig.json` for script directories. Running `/nekojs probe` generates or updates:

- TypeScript `.d.ts` files under `.neko_probe/typescript/`;
- The `.neko_probe/python/nekojs/` stub package after running `/nekojs probe python`;
- VS Code snippets and editor configuration managed by each backend.

Automatic execution of the default TS probe on server startup is controlled by `runAtStartup` in `probe.toml` and is disabled by default. Open the relevant script directory directly in VS Code for completion. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-14"></a>
## Next steps

- [Script properties](script-properties_us): details of `priority`/`modloaded`/`disable`/`after`.
- [Global bindings](global-bindings_us): all top-level APIs.
- [Event reference](event-reference_us): listen to game events.

<!-- wiki-nav -->

---

[Previous: FAQ](faq_us) · [Contents](Home) · [Next: Script properties](script-properties_us)
