<!-- wiki-page: quick-start; locale: us -->

> **English** · [中文](quick-start_cn)

<a id="wiki-section-1"></a>
# Quick start

This page takes you from an empty setup to your first running NekoJS script.

<a id="wiki-section-2"></a>
## 1. Install prerequisites

NekoJS depends on [Graal](https://www.curseforge.com/minecraft/mc-mods/graal), which provides the GraalJS runtime. **25.1.3.7 or later** is required. Earlier versions lack the regex language, so scripts using regular expressions fail with `No language for id regex found`.

1. Download **NekoJS** and the corresponding **Graal** from [CurseForge](https://www.curseforge.com/minecraft/search?search=NekoJS) or [GitHub Releases](https://github.com/NekoJS-Dev/NekoJS/releases), matching your Minecraft / NeoForge or Fabric version.
2. Put both jars in the `mods/` folder.
3. Start the game.

> Fabric 26.x nodes in the current repository still provide an experimental API subset. See [Platforms and compatibility](platform-compatibility_us) for differences. Cleanroom 1.12.2 belongs to a separate legacy branch and is not part of this page's main setup process.

> Versions must match. Release pages list the Minecraft, loader, and Java versions for each NekoJS artifact.

For repository builds of **NeoForge 1.21.1**, clients use the ordinary `.jar`; dedicated servers use the matching `-server.jar`, which also contains the ICU module missing from that server distribution. Install one NekoJS variant per directory, together with the corresponding Graal mod. The server variant conflicts with Minecraft's existing ICU module on clients; the ordinary client artifact lacks ICU on dedicated servers. `./gradlew :1.21.1:build` produces and verifies both variants. Artifact selection for NeoForge26.x and Fabric stays unchanged. Consult the release page for the files actually published.

<a id="wiki-section-3"></a>
## 2. Directory structure

After the first launch, a `nekojs/` folder is generated automatically in the game root directory:

```text
.neko_probe/                # Type declaration output root (/nekojs probe generates it alongside nekojs)
nekojs/
├── startup_scripts/        # Startup scripts: register items/blocks, etc. (changes require a game restart)
│   └── jsconfig.json
├── server_scripts/         # Server scripts: recipes, event listeners (supports /nekojs reload)
│   └── jsconfig.json
├── client_scripts/         # Client scripts: GUI, particles, keys (supports client reload)
│   └── jsconfig.json
├── test_scripts/           # Test scripts: explicitly run through /nekojs test
├── packs/                  # Optional: script packs (see the next section)
├── node_modules/           # External pure-JS npm dependencies
├── assets/                 # Assets
├── data/                   # Data packs
└── config/                 # probe.toml and engine.toml (engine/sandbox configuration)
                              (legacy config/nekojs-engine.toml is a read-only fallback)
```

All four script directories support `.js`, `.mjs`, `.cjs`, `.ts`, `.jsx`, `.tsx`, and `.py`. `.py` files are transpiled from NekoJS's Python subset and do not require an external Python runtime. See [Script basics](script-basics_us).

<a id="wiki-section-4"></a>
### Script packs (optional)

Besides the four standalone script directories, you can organize scripts into packs: place a
`manifest.json` and the pack's own `startup_scripts/`, `server_scripts/`, and other subdirectories under `nekojs/packs/<pack-id>/`:

```text
nekojs/packs/my_pack/
├── manifest.json           # {"id": "my_pack", "name": "我的包", "version": "1.0.0"}
├── server_scripts/
│   └── boss.js
└── client_scripts/
    └── hud.js
```

- **Load order**: global packs (alphabetically by id) → world packs → standalone directories.
- **Enable/disable**: `/nekojs packs` lists all packs; `/nekojs packs disable my_pack` writes a state file
  (with precedence over the manifest's `enabled`), which takes effect after `/nekojs reload`.
- **World packs**: place them in the save directory at `<world-folder>/nekojs_packs/<pack-id>/` (the same structure as global packs).
  They load automatically when entering that world, and their event listeners and timers are unloaded automatically when leaving it. Different worlds can have different scripts.
- Manifest fields are parsed permissively: `id`/`name`/`version`/`description`/`authors`/`enabled`/`clientSync`
  (`clientSync` is reserved for multiplayer script distribution; see below). Each pack's script directories also get an automatically generated `jsconfig.json`,
  with the same editor completion as standalone directories.
- A pack's `data/<namespace>/**` directory is mounted as a mandatory data pack when the server starts/reloads (recipes, loot tables, tags, villager trade JSON, etc.). This is a NeoForge capability; Fabric does not currently provide equivalent datagen/mandatory data pack support.

<a id="wiki-section-5"></a>
### Multiplayer script pack distribution (server → client)

Servers can automatically distribute script packs to connecting clients. Clients verify and finish executing pack scripts **before vanilla registry validation**
(remote scripts still use the ClassFilter/Watchdog sandbox). Enable this in the server's `nekojs/config/engine.toml`:

```toml
[packSync]
mode = "all"           # off (default) | hashOnly (compare hashes without execution) | all
allowUnsigned = false   # Whether to accept unsigned packs
```

- **Signatures**: put `signature = { algorithm = "Ed25519", keyId = "...", publicKey = "<X.509 base64>", signature = "<base64>" }` in the manifest
  (the signature covers the canonical manifest without the signature key, plus all file contents).
- **Trust**: a client connecting to an untrusted server for the first time is **disconnected** and shown its address. Run
  `/nekojs trust <address>` in a single-player world on that client, then reconnect to receive packs. Trust also pins the server's current signing public key (silent key rotation is rejected).
- **Cache**: packs are cached in `nekojs/server_packs/`, bucketed by a hash of the server address. The local cache is reused if the hash has not changed.
- The Cleanroom legacy branch has no configuration phase, so it falls back to synchronization after login and only synchronizes scripts. The current repository does not verify that branch's behavior.

<a id="wiki-section-6"></a>
## 3. Your first script

Create `hello.js` under `nekojs/server_scripts/`:

```javascript
// server_scripts/hello.js

// Listen for the server-started event
ServerEvents.started(event => {
  console.info('[NekoJS] 服务端脚本已加载！')
})
```

Enter a world and wait for the server to finish starting; the log should show `[NekoJS] 服务端脚本已加载！`. If you add this example after the world has started, reopen the world to trigger `started`. An ordinary `/nekojs reload` re-registers the listener but does not fire this lifecycle event again.

<a id="wiki-section-7"></a>
## 4. Change a recipe

```javascript
// server_scripts/recipes.js

ServerEvents.recipes(event => {
  // Remove all vanilla stick recipes
  event.remove({ output: 'minecraft:stick' })

  // Add a recipe: craft 4 sticks from 1 cobblestone
  event.shaped('4x minecraft:stick', [
    'C'
  ], {
    C: 'minecraft:cobblestone'
  }).id('nekojs:cobble_to_sticks')
})
```

Run `/nekojs reload`. Both NeoForge and Fabric 26.x support server recipe hot reload. NeoForge's recipe viewer may require a client refresh; on Fabric, refer to the current node implementation and platform compatibility notes.

See [Recipe system](recipe-system_us).

<a id="wiki-section-8"></a>
## 5. Register a new item

```javascript
// startup_scripts/my_items.js

RegistryEvents.register(event => {
  event.item('mymod:cool_gem', b => {
    b.maxStackSize = 16
    b.rarity = 'rare'
  })
})
```

> Note: startup scripts execute during game startup. Registration changes require a game restart; `/nekojs reload startup` currently does not support reloading STARTUP scripts through an in-game command.

See [Registering new content](registering-new-content_us).

<a id="wiki-section-9"></a>
## 6. Enable IDE completion

1. Run `/nekojs probe` to generate TypeScript `.d.ts` files under `.neko_probe/typescript/`.
2. For Python completion, also run `/nekojs probe python` and put `from nekojs import *` (or named imports such as `from nekojs import Item, ServerEvents`) at the top of every `.py` file using NekoJS APIs. Run `/nekojs probe all` to run all backends.
3. Open `nekojs/server_scripts/` (or the relevant directory) in VS Code. Its `jsconfig.json` links the TypeScript type library.
4. To run the default TS probe automatically when the server starts, set `runAtStartup = true` in `nekojs/config/probe.toml`; this option is disabled by default.

<a id="wiki-section-10"></a>
## 7. Next steps

- [Script basics](script-basics_us): script types, lifecycle, and reload behavior.
- [Global bindings](global-bindings_us): all APIs, including `Item`, `Ingredient`, `Fluid`, `Text`, and `JsonIO`.
- [Event reference](event-reference_us): all available events.
- [Module system](module-system_us): split files, use npm dependencies, and import with `java:`.
- [TypeScript and JSX](typescript-and-jsx_us): write scripts in TS.

<!-- wiki-nav -->

---

[Previous: Platforms and compatibility](platform-compatibility_us) · [Contents](Home) · [Next: FAQ](faq_us)
