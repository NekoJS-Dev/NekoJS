<!-- wiki-page: commands; locale: us -->

> **English** · [中文](commands_cn)

<a id="wiki-section-1"></a>
# Commands

All NekoJS commands start with `/nekojs`. Except for `/nekojs browse`, every command needs operator permission (in single player, enable cheats).

<a id="wiki-section-2"></a>
## Common commands

| Command | What it does |
|---|---|
| `/nekojs reload` | Reloads server scripts. Use it after changing `server_scripts` |
| `/nekojs reload client` | Reloads client scripts |
| `/nekojs test` | Runs the tests in `test_scripts` |
| `/nekojs error` | Shows whether any script has an error |
| `/nekojs view_all_errors` | Shows the full error list |
| `/nekojs probe` | Generates type declarations for code completion |
| `/nekojs hand` | Shows the held item's ID, components and tags; click to copy |
| `/nekojs browse` | Opens the `nekojs` folder in your file manager |

<a id="wiki-section-3"></a>
## Reloading scripts

<a id="wiki-section-4"></a>
### /nekojs reload

| Form | What it does |
|---|---|
| `/nekojs reload` | Reloads server scripts; same as `/nekojs reload server` |
| `/nekojs reload server` | Reloads server scripts and recalculates recipes |
| `/nekojs reload client` | Reloads client scripts. Only works in single player or on a client |
| `/nekojs reload test` | Reloads and runs test scripts; same as `/nekojs test` |
| `/nekojs reload startup` | **Not supported**. Restart the game after changing startup scripts |
| `/nekojs reload server recipes.js` | Reruns one file. The path is relative to `server_scripts`; TAB completes it |

When you reload a whole type and any script fails, the whole reload is discarded and the game keeps the last working scripts. Fix the error and reload again.

Rerunning a single file has no such protection, and it may also rerun other files that import it. For everyday work, reload the whole type.

`/nekojs reload client` first replies that the reload is "scheduled"; the client finishes it shortly after. Watch for the result message that follows.

<a id="wiki-section-5"></a>
### How this differs from vanilla commands

| Action | What it reloads |
|---|---|
| `/nekojs reload` | NekoJS server scripts and the recipes they change |
| Vanilla `/reload` | Data packs; on NeoForge it also reloads server scripts |
| F3+T | Client resources (textures, models, languages); on NeoForge it also reloads client scripts |

After changing scripts, use `/nekojs reload`. If you only changed data pack files under `nekojs/data`, use vanilla `/reload`; if you only changed resources under `nekojs/assets`, press F3+T.

<a id="wiki-section-6"></a>
### /nekojs test

Runs every test in `test_scripts` and reports how many assertions passed and failed. A finished command does not mean every test passed — read the reported result. If no assertion ran at all, the result says "not observed" rather than "passed".

<a id="wiki-section-7"></a>
## Viewing errors

| Command | What it does |
|---|---|
| `/nekojs error` | Shows in chat whether there are errors, with a short summary |
| `/nekojs view_all_errors` | Shows the full error list |

On NeoForge, `view_all_errors` opens an error panel where you can search, read details, copy errors and click to jump to the failing line in VS Code. On Fabric it prints the list in chat.

The error panel only opens when a player runs the command. From a server console, use `/nekojs error`.

Errors are also written to `logs/nekojs/<type>.log`. See [Error and log reference](error-reference_us) for what each error code means.

<a id="wiki-section-8"></a>
## Inspecting items and tags

When writing scripts you often need exact item IDs and tags. Everything these commands print can be **clicked to copy** and pasted into a script.

<a id="wiki-section-9"></a>
### /nekojs hand

Shows the item in your main hand:

- The item string, such as `"minecraft:iron_sword[damage=2]"`, which you can pass straight to `Item.of(...)`
- The full item JSON
- All tags on the item
- For block items, the block's ID and tags
- For fluid containers (such as a water bucket), the fluid's ID and tags

<a id="wiki-section-10"></a>
### /nekojs hotbar and /nekojs inventory

Exports the items in your hotbar (`hotbar`) or whole inventory (`inventory`) as a list, for example:

```json
["2x minecraft:stone", "minecraft:apple"]
```

Empty slots are skipped. Armor slots and the off hand are not included.

<a id="wiki-section-11"></a>
### /nekojs list-tag

```text
/nekojs list-tag minecraft:item                  List every item tag
/nekojs list-tag minecraft:item minecraft:logs   List the items in this tag
```

The first argument is a registry (`minecraft:item`, `minecraft:block` and so on); the second is a tag. TAB completes both. This command also works from a server console.

<a id="wiki-section-12"></a>
## Code completion

| Command | What it does |
|---|---|
| `/nekojs probe` | Generates TypeScript declarations for VS Code completion |
| `/nekojs probe python` | Generates Python declarations |
| `/nekojs probe all` | Generates declarations for every language |
| `/nekojs probe reset_config` | Restores editor configuration to defaults if it gets messed up |

The generated files go to `.neko_probe/` in your game folder. Run `/nekojs probe` again after adding mods or registering new content.

To generate them automatically every time the server starts, set `runAtStartup = true` in `nekojs/config/probe.toml`.

Other subcommands (`list`, `reload`, `enable`, `disable`, choosing a generator by name, and so on) and the full configuration are in [Probe type generation](probe-type-generation_us).

<a id="wiki-section-13"></a>
## Script packs

These commands manage the script packs in `nekojs/packs/`.

| Command | What it does |
|---|---|
| `/nekojs packs` | Lists all script packs |
| `/nekojs packs enable <pack>` / `disable <pack>` | Enables or disables a pack; reload afterwards to apply it |
| `/nekojs packs status server` | Shows the current result for each pack |
| `/nekojs packs plan server` | Previews load order and which files are skipped, without running anything |
| `/nekojs trust <server address>` | Trusts a server so it can send script packs to your client |

`<pack>` is usually written as `GLOBAL:name`, or `WORLD:name` for a pack inside a world. When the name is unique, the name alone also works.

To create, package and distribute script packs in multiplayer, see [Script packs](script-packs_us).

<a id="wiki-section-14"></a>
## Other commands

| Command | What it does |
|---|---|
| `/nekojs registry` | Shows content registered dynamically by scripts (experimental; only NeoForge 26.x has content) |
| `/nekojs registry stale` | Lists entries that are no longer registered since the last reload |

<a id="wiki-section-15"></a>
## Adding commands from scripts

Scripts can register their own commands with `CommandEvents.register`. See [Event reference - CommandEvents](event-reference_us#wiki-command-events) for the syntax and a full example.

<a id="wiki-section-16"></a>
## Next steps

- [Script basics](script-basics_us): which reload to use when.
- [FAQ](faq_us): what to do when a command seems to have no effect.

<!-- wiki-nav -->

---

[Previous: AI-assisted authoring](ai-support_us) · [Contents](Home) · [Next: Error and log reference](error-reference_us)
