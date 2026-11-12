<!-- wiki-page: script-packs; locale: us -->

> **English** · [中文](script-packs_cn)

<a id="wiki-section-1"></a>
# Versioned script packs

A script pack keeps a set of scripts, data and resources in one folder, so you can enable, disable and share them as a unit. NekoJS has two kinds of pack:

- **Simple directory packs** have a plain `manifest.json` and suit grouping your own scripts. See "Simple directory packs" below.
- **Versioned packs** have `"formatVersion": 1` in `manifest.json`. They have a version, can declare dependencies, can offer functions to other packs, and can be packaged as a `.nekopack` archive or a mod JAR.

The first half of this page covers versioned packs. If you only want to group scripts, simple directory packs are enough.

<a id="wiki-section-2"></a>
## Create a versioned pack

Run this in game:

```text
/nekojs packs create server economy
```

It creates `nekojs/packs/economy/`:

```text
nekojs/packs/economy/
├── manifest.json
└── server_scripts/
    ├── main.js          # Example entry that writes one log line
    └── pack-api.d.ts    # Code-completion declarations for the Pack object
```

You can use `startup`, `client` or `test` instead of `server`. If the folder already exists, the command fails rather than overwriting it.

Then run `/nekojs reload` and check with `/nekojs packs status server` that the pack ran. `logs/nekojs/server.log` should contain the example script's line.

<a id="wiki-section-3"></a>
## manifest.json

```json
{
  "formatVersion": 1,
  "id": "shop",
  "version": "1.0.0",
  "entries": { "server": ["main.js"] },
  "dependencies": { "economy": ">=1.0.0 <2.0.0" },
  "environment": { "loaders": ["neoforge"], "minecraft": ">=26.1 <27" },
  "capabilities": { "requires": { "economy": ["wallet"] } }
}
```

| Field | Required | Description |
|---|---|---|
| `formatVersion` | Yes | Always `1` |
| `id` | Yes | Pack ID: lowercase letters, digits, `_` and `-`, starting with a letter or digit |
| `version` | Yes | A three-part version such as `1.0.0`, optionally with a prerelease suffix such as `-beta.1` |
| `entries` | Yes | Entry files per script type; keys are `startup`, `server`, `client`, `test` |
| `dependencies` | No | Packs this one depends on, with version ranges |
| `environment` | No | Conditions to run: `loaders`, `minecraft` (a version range), `mods` (required mods with version ranges) |
| `capabilities` | No | `provides`: functions this pack offers; `requires`: functions it needs from other packs |
| `name`, `description`, `authors` | No | Display name, description and authors |
| `enabled` | No | Whether it is enabled by default |
| `clientSync` | No | `false` keeps it from being sent to multiplayer players |
| `signature` | No | The signature; see "Sending script packs to multiplayer players" below |
| `config` | No | Your own configuration object |

Versioned manifests are checked strictly: an extra field, a wrong field type or an invalid entry stops the pack from running. The full format is `docs/schema/script-pack-v1.schema.json` in the repository.

<a id="wiki-section-4"></a>
### Entry files

Paths in `entries` are relative to the matching script folder. For example, `"server": ["main.js"]` means `server_scripts/main.js`.

- Only files listed in `entries` run directly. Other scripts in the pack are libraries that entries `import`.
- `import` can only reach files inside the same pack. To use another pack, use capabilities (below).
- Paths cannot be absolute, cannot leave the folder with `..`, and cannot point into `node_modules`.

<a id="wiki-section-5"></a>
### Version ranges

Version ranges in `dependencies`, `environment.minecraft` and `environment.mods` look like this:

- `*`: any version
- `1.2.0` or `=1.2.0`: an exact version
- `>=1.0.0`, `<2`, `>1.1`, `<=1.5.3`: comparisons
- Separate with spaces to require all of them, for example `>=1.0.0 <2.0.0`

`^`, `~` and "or" are not supported. Dependencies are never downloaded automatically.

<a id="wiki-section-6"></a>
### Dependencies

A pack always runs after the packs it depends on. `// priority:` and `// after:` comments cannot change that order.

If a dependency is missing, the wrong version, disabled or part of a cycle, only the packs that depend on it stop; everything else runs normally. `/nekojs packs plan <type>` shows why.

A dependency ID can have a source prefix, such as `GLOBAL:economy`. Without a prefix, a pack from the same source is preferred. World packs can depend on global and mod packs; global and mod packs cannot depend on world packs.

<a id="wiki-section-7"></a>
## Calling between packs: capabilities

A pack can offer functions to other packs. These are called capabilities.

The provider writes `"capabilities": { "provides": ["wallet"] }` in its `manifest.json` and publishes the function from its entry script:

```javascript
// server_scripts/main.js in the economy pack
Pack.provide('wallet', request => ({ balance: 42 }))
```

The consumer writes `"capabilities": { "requires": { "economy": ["wallet"] } }` in its `manifest.json`, then gets the function and calls it:

```javascript
// server_scripts/main.js in the shop pack
const wallet = Pack.require('economy', 'wallet')
console.info(wallet({ player: 'example' }).balance)
```

- Calls are synchronous. Arguments and results must be JSON data (objects, arrays, strings, numbers, booleans, `null`). Functions and game objects cannot be passed.
- Data can nest at most 32 levels and 10000 nodes, with about one million characters across all strings.
- `Pack.selector` is this pack's selector (such as `GLOBAL:economy`), and `Pack.activation` numbers the current run. Both are read-only.
- Once the provider reloads or stops, a function you got earlier stops working. Call `Pack.require` again on the next run.

<a id="wiki-section-8"></a>
## Each pack runs on its own

- Every versioned pack has its own environment. Its top-level variables are invisible to other packs.
- Every pack has its own `global`, which survives reloads. A world pack's `global` is cleared when you leave the world.
- `shared` is shared by all packs and ordinary scripts.
- Simple directory packs without `formatVersion` and the ordinary scripts in `nekojs/` still share one `global` per script type.

<a id="wiki-section-9"></a>
## Management commands

| Command | What it does |
|---|---|
| `/nekojs packs` | Lists all script packs and whether they are enabled |
| `/nekojs packs enable <pack>` / `disable <pack>` | Enables or disables a pack; reload afterwards |
| `/nekojs packs plan <type>` | Previews load order and which packs cannot run and why. Runs no scripts |
| `/nekojs packs status <type>` | Shows how each pack actually ran |
| `/nekojs packs reload <type> <pack>` | Reloads only this pack and the packs that depend on it |
| `/nekojs packs create <type> <id>` | Creates a new versioned pack |
| `/nekojs packs validate <pack>` | Checks a pack's manifest and files |
| `/nekojs packs archive <pack>` | Packages it as a `.nekopack` archive |
| `/nekojs packs jar <loader> <version> <pack>` | Packages it as a mod JAR |

Write `<pack>` as "source:ID":

| Selector | Where the pack is |
|---|---|
| `GLOBAL:id` | `nekojs/packs/<id>/` |
| `WORLD:id` | `<save folder>/nekojs_packs/<id>/` |
| `MOD:id` | A pack installed as a mod JAR |
| `SERVER_CACHE:packs:id` / `SERVER_CACHE:worldpacks:id` | A pack received from a multiplayer server |

If only one pack has that ID, the ID alone is enough. Packs from `MOD` and `SERVER_CACHE` cannot be enabled, disabled or changed with these commands. Do not edit the server cache by hand to get around signature checks.

<a id="wiki-section-10"></a>
## Applying changes

| You changed | Do this |
|---|---|
| A pack's server or client scripts | `/nekojs packs reload server GLOBAL:economy`, or `/nekojs reload` for the whole type |
| Startup scripts | Restart the game |
| The pack's `data/` | Vanilla `/reload` |
| The pack's `assets/` | F3+T |

- Saving a file does not apply it.
- Files in a versioned pack cannot be reloaded one at a time with `/nekojs reload server <file>`; use `packs reload`.
- If a reload fails, none of its changes apply and the last working version stays in use.
- When a provider fails, the packs that depend on it stop too. After fixing it, reload them yourself; they do not recover on their own. A pack you disabled also stays disabled when its dependency comes back.

<a id="wiki-section-11"></a>
## Packaging and publishing

<a id="wiki-section-12"></a>
### As a .nekopack archive

```text
/nekojs packs validate GLOBAL:economy
/nekojs packs archive GLOBAL:economy
```

The archive is written to `nekojs/exports/economy-1.0.0.nekopack`. An existing file with the same name is never overwritten.

The archive holds the manifest, signature, the four script folders and `assets/` and `data/`. Your local enabled/disabled state is not included. The same content always produces an identical file.

<a id="wiki-section-13"></a>
### As a mod JAR

```text
/nekojs packs jar neoforge 26.1.2 GLOBAL:economy
```

The output goes to `nekojs/exports/`. The JAR is an ordinary content mod that depends on NekoJS: drop it in `mods/` and its pack shows up as `MOD:id`.

- Supported targets: NeoForge `1.21.1`, `26.1.2`, `26.2`; Fabric `26.1.2`, `26.2`.
- The mod ID is the pack ID, with stricter rules: 2–48 characters, only lowercase letters, digits and underscores, starting with a letter.
- Players still need NekoJS installed.
- Packs inside a mod JAR are not sent to players by multiplayer sync. If the client needs it too, players must install the JAR themselves.

<a id="wiki-section-14"></a>
### Batch work with the repository tool

With a NekoJS source checkout, you can also use `tools/nekojs-pack.ps1` from the command line. It can create several script types at once and install archives:

```powershell
./tools/nekojs-pack.ps1 -Action create -InputPath ./build/author/packs -Id economy -Types server,client
./tools/nekojs-pack.ps1 -Action validate -InputPath ./build/author/packs/economy
./tools/nekojs-pack.ps1 -Action archive -InputPath ./build/author/packs/economy -OutputPath ./build/economy.nekopack
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs
# Upgrade or roll back to the selected archive; the local enabled state is kept.
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs -Replace
./tools/nekojs-pack.ps1 -Action jar -InputPath ./build/author/packs/economy -OutputPath ./build/economy.jar -Loader neoforge -Minecraft 26.1.2
```

Installing replaces the whole pack, so files removed in the new version are deleted. If the target exists, `-Replace` is required. There is no in-game command to install an archive.

<a id="wiki-section-15"></a>
## Things to keep in mind

- **Scripts are never stopped automatically.** An infinite loop in a capability callback freezes the game.
- **A failed reload does not undo side effects.** Files written or world changes made before the failure stay.
- **A signature is not a safety guarantee.** It only proves who made the pack and that it was not changed; it does not make the code harmless. Install only packs you trust.
- **Resource order on Fabric**: later layers override earlier ones — `nekojs/assets` and `nekojs/data`, then global packs (by ID), then world packs (by ID), then packs received from a server (assets only). Packs that are disabled, or cannot run because of dependency or environment conditions, contribute no resources.
- World pack scripts run when the world starts and stop when it closes. Merely previewing a save in the world list does not run them.

<a id="wiki-section-16"></a>
## Simple directory packs (no formatVersion)

If you do not need dependencies or capabilities, use a simpler directory pack: put a `manifest.json` in `nekojs/packs/<pack-id>/`, plus the same script folders as the top-level layout.

```text
nekojs/packs/my_pack/
├── manifest.json           # {"id": "my_pack", "name": "My pack", "version": "1.0.0"}
├── server_scripts/
│   └── boss.js
└── client_scripts/
    └── hud.js
```

Any pack whose `manifest.json` has no `formatVersion` is a simple directory pack. The versioned-pack rules above do not apply to it.

- **Fields**: `id`, `name`, `version`, `description`, `authors`, `enabled`, `clientSync`. Misspelled or extra fields do not cause errors.
- **Load order**: global packs (alphabetically by id) → world packs → the top-level script folders in `nekojs/`.
- **Enabling and disabling**: `/nekojs packs disable GLOBAL:my_pack` overrides `enabled` in `manifest.json`. Reload the affected type to apply it; startup scripts need a restart.
- **World packs**: put them in `<save folder>/nekojs_packs/<pack-id>/`. They load only while you are in that world.
- **Data and resources**: the pack's `data/` and `assets/` folders are loaded too. After changing data, use vanilla `/reload`; after changing assets, press F3+T.
- **Code completion**: each script folder in the pack also gets a `jsconfig.json`.

<a id="wiki-section-17"></a>
## Sending script packs to multiplayer players

A server can send script packs to players automatically when they join, so they do not have to install anything. This is off by default.

<a id="wiki-section-18"></a>
### Server setup

Turn it on in the server's `nekojs/config/engine.toml`:

```toml
[packSync]
mode = "all"            # off (default, send nothing) | hashOnly (compare only, never run) | all (send and run)
allowUnsigned = false   # Whether unsigned packs may be sent; keeping this false is recommended
```

To keep a pack from being sent, write `"clientSync": false` in its `manifest.json`.

Players only accept signed packs (unless `allowUnsigned` is on). Add the signature to `manifest.json`:

```json
"signature": { "algorithm": "Ed25519", "keyId": "...", "publicKey": "<X.509 base64>", "signature": "<base64>" }
```

The signature covers the manifest without its `signature` field, plus the contents of every file in the pack.

<a id="wiki-section-19"></a>
### A player's first join

1. The first time a player connects to the server, they are disconnected and shown the server address.
2. The player enters any single-player world and runs `/nekojs trust <server address>`.
3. On reconnecting, they receive and run the server's script packs.

Trusting a server also remembers its current signing public key. If the server later changes its key without notice, the player is refused and must trust it again.

Received packs are cached in the player's `nekojs/server_packs/`. If a pack has not changed, the next connection uses the cache.

> Only trust servers you know. Scripts in these packs run on your client.

<!-- wiki-nav -->

---

[Previous: Error and log reference](error-reference_us) · [Contents](Home) · [Next: Recipe system](recipe-system_us)
