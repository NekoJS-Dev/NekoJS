<!-- wiki-page: faq; locale: us -->

> **English** · [中文](faq_cn)

<a id="wiki-section-1"></a>
# FAQ

<a id="wiki-section-2"></a>
## Installation

<a id="wiki-section-3"></a>
### The game crashes at startup saying GraalJS or polyglot is missing

The **Graal** dependency is missing or the wrong version. Download the file that matches your Minecraft version and loader from [Graal on CurseForge](https://www.curseforge.com/minecraft/mc-mods/graal). It must be at least 25.1.3.7.

<a id="wiki-section-4"></a>
### Any regular expression fails with `No language for id regex found`

Graal is too old. Update to 25.1.3.7 or newer.

<a id="wiki-section-5"></a>
### There is no nekojs folder in the game folder

1. Check that both NekoJS and Graal are in `mods/` and the startup log shows no errors.
2. Check that the game folder is writable.
3. Enter a world and run `/nekojs probe` once; it also creates the folder.

<a id="wiki-section-6"></a>
### Which versions are supported?

NeoForge 1.21.1, 26.1.2 and 26.2.0, plus Fabric 26.1.2 and 26.2. The Fabric builds are missing some features; see [Platforms and compatibility](platform-compatibility_us).

<a id="wiki-section-7"></a>
## Scripts have no effect

<a id="wiki-section-8"></a>
### I wrote a script but nothing happens

Check these in order:

1. **Is it in the right folder?** Recipe changes go in `server_scripts`; item registration goes in `startup_scripts`. See [Script basics](script-basics_us).
2. **Did you reload?** Saving a file does not apply it. Run `/nekojs reload` for server scripts and `/nekojs reload client` for client scripts.
3. **Is it a startup script?** Startup scripts need a game restart.
4. **Is there an error?** Run `/nekojs error`.
5. **Is it disabled?** A script with `// disable:` at the top does not run.
6. **Is a mod missing?** If the top of the file says `// modloaded: xxx` and xxx is not installed, the script does not run.

<a id="wiki-section-9"></a>
### Error: `xxx is not defined` or `xxx is not a function`

Usually one of these:

- **The script is in the wrong folder.** For example, `ServerEvents.recipes` only works in `server_scripts`, and `ClientEvents` only works in `client_scripts`.
- **A name is misspelled.** Check the [Event reference](event-reference_us) or [Global bindings](global-bindings_us). Turning on [code completion](#wiki-section-21) prevents most typos.
- **The feature is not available on this platform.** Some events and bindings exist only on NeoForge; see [Platforms and compatibility](platform-compatibility_us).

<a id="wiki-section-10"></a>
### Nothing changed after a reload

- Run `/nekojs error` first. If a new script has an error, the whole reload is discarded and the game keeps the old scripts. Fix the error and reload.
- Did you change a startup script? Those need a game restart.
- Did you change a client script? Use `/nekojs reload client`; plain `/nekojs reload` only reloads server scripts.

<a id="wiki-section-11"></a>
### The game freezes while loading scripts

A script may contain an infinite loop or `await` a Promise that never completes. NekoJS does not forcibly stop such scripts. Check your recent changes, especially loops and top-level `await`.

<a id="wiki-section-12"></a>
### Top-level await or import fails

`import`, `export` and top-level `await` only work in ES modules. Renaming the file to `.mjs` is the safest fix. Files ending in `.cjs` do not support this syntax. See [Module system](module-system_us).

<a id="wiki-section-13"></a>
## Recipes

<a id="wiki-section-14"></a>
### I added a recipe but JEI does not show it

After `/nekojs reload`, the recipe already works on the server; try it in a crafting table. Recipe viewers such as JEI or REI sometimes do not refresh on their own; re-enter the world.

<a id="wiki-section-15"></a>
### How do I give a recipe an ID?

Add `.id('namespace:name')` after the recipe:

```javascript
event.shaped('4x minecraft:stick', ['C'], { C: 'minecraft:cobblestone' })
  .id('mypack:cobble_to_sticks')
```

Without it, an ID starting with `nekojs:` is generated. To remove a recipe by ID, use `event.removeById(id)`.

<a id="wiki-section-16"></a>
### A mod's recipe type cannot be called, or completion shows `never`

NekoJS tries to recognize recipe types from other mods automatically, but it cannot recognize all of them:

- **The recipe method is missing from completion**: use `event.custom({...})` and write the recipe's full JSON, following the format of that mod's own data pack.
- **Completion shows it as `never`**: NekoJS recognized the recipe type but determined it cannot support it safely. Look up the `NEKO-52xx` code in the log for the reason, and use `event.custom(...)` instead.

Do not edit the generated files in `.neko_probe` to "fix" this. The next probe overwrites them, and the call still fails at runtime. See [Recipe system](recipe-system_us).

<a id="wiki-section-17"></a>
## New items and blocks

<a id="wiki-section-18"></a>
### My item shows a purple-and-black texture

The texture or model is missing. Put the texture PNG at:

```text
nekojs/assets/<namespace>/textures/item/<name>.png
```

For example, the texture for `mymod:cool_gem` goes in `nekojs/assets/mymod/textures/item/cool_gem.png`. Press F3+T after changing resources. See [Registering new content - Resources and localization](registering-new-content_us#wiki-assets).

<a id="wiki-section-19"></a>
### The item name shows as `item.mymod.xxx`

The language file is missing. Write this to `nekojs/assets/mymod/lang/en_us.json`:

```json
{ "item.mymod.xxx": "Item display name" }
```

<a id="wiki-section-20"></a>
### I changed a startup script and the new item did not appear

Startup scripts run once while the game starts, so you must restart the game. `/nekojs reload startup` is not available.

<a id="wiki-section-21"></a>
## Code completion

<a id="wiki-section-22"></a>
### VS Code shows no completion

1. Run `/nekojs probe` in game.
2. In VS Code, open **the `nekojs/server_scripts` folder** (or another script folder), not the whole game folder.
3. Check that the game folder has a `.neko_probe/` folder (next to `nekojs/`).

<a id="wiki-section-23"></a>
### Completion does not match what actually exists

Completion reflects the moment you ran `/nekojs probe`. Run it again after adding mods or registering new content.

<a id="wiki-section-24"></a>
### Python scripts run, but there is no completion

1. Run `/nekojs probe python` (plain `/nekojs probe` does not generate Python declarations).
2. Put this at the top of each `.py` file:

```python
from nekojs import *
```

You can also import only what you use, such as `from nekojs import Item, ServerEvents`. This line is only for the editor and is removed automatically when the script runs. There is nothing to install with pip, and do not write `import nekojs` instead.

3. Make sure the Pylance extension is installed in VS Code.

See [Python scripts](python-scripts_us).

<a id="wiki-section-25"></a>
### A .ts file with type errors still runs

That is expected. NekoJS removes type annotations when running TypeScript and does not check types; VS Code reports type errors. Adding `// @ts-check` at the top of a `.js` file also makes VS Code check its types.

<a id="wiki-section-26"></a>
## npm and modules

<a id="wiki-section-27"></a>
### `require('xxx')` fails with module not found

1. The package must be in `nekojs/node_modules/<package name>/`.
2. Only **pure JavaScript** packages work. Packages with compiled C/C++ code (native modules) cannot be used.
3. `require('./xxx')` is relative to the current file; only `require('xxx')` looks in `node_modules`.

NekoJS is not a full Node.js. See [Node.js compatibility](nodejs-compatibility_us) for the built-in modules you can use.

<a id="wiki-section-28"></a>
## Performance

<a id="wiki-section-29"></a>
### Code in a tick event makes the game lag

Events such as `ServerEvents.tickPost` fire 20 times per second, so keep their code light. Cache results, or use a counter to run only every few ticks:

```javascript
let ticks = 0
ServerEvents.tickPost(event => {
  if (++ticks % 20 !== 0) return // run once per second
  // ...
})
```

<a id="wiki-section-30"></a>
### Scripts are slow to load the first time

The JavaScript engine needs to warm up the first time it runs; this is normal. If it stays slow, check for very large npm packages or heavy file reads and writes at the top level of a script.

<a id="wiki-section-31"></a>
## Security

<a id="wiki-section-32"></a>
### A script is denied access to a file

For safety, scripts can only write files inside the `nekojs/` folder by default. See [Node.js compatibility - Access limits](nodejs-compatibility_us#wiki-fs-access).

<a id="wiki-section-33"></a>
### Is NekoJS safe on a multiplayer server?

Scripts are powerful and can reach most of the game's internals. **Only run scripts written by people you trust.** The NekoJS sandbox blocks some dangerous operations, but it cannot guarantee that a malicious script is stopped.

If you enable remote control in the [AI-assisted authoring](ai-support_us) features, never commit the access token to a repository or share it.

<a id="wiki-section-34"></a>
## Still stuck?

- Look up the error code in the [Error and log reference](error-reference_us).
- Ask on [GitHub Issues](https://github.com/NekoJS-Dev/NekoJS/issues). Include the relevant log from `logs/nekojs/`, the failing script, and your Minecraft, loader and NekoJS versions.

<!-- wiki-nav -->

---

[Previous: Configuration](configuration_us) · [Contents](Home) · [Next: Script basics](script-basics_us)
