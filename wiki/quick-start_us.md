<!-- wiki-page: quick-start; locale: us -->

> **English** · [中文](quick-start_cn)

<a id="wiki-section-1"></a>
# Quick start

This page walks you through installing NekoJS, writing your first script, changing a recipe and adding a new item. It takes about 15 minutes.

<a id="wiki-section-2"></a>
## 1. Install

You need two mods:

- **NekoJS**: download it from [CurseForge](https://www.curseforge.com/minecraft/search?search=NekoJS) or [GitHub Releases](https://github.com/NekoJS-Dev/NekoJS/releases).
- **[Graal](https://www.curseforge.com/minecraft/mc-mods/graal)**: the dependency that actually runs JavaScript. It must be **25.1.3.7 or newer**.

Pick the files that match your Minecraft version and loader (NeoForge or Fabric). Put both in the `mods/` folder and start the game.

> **For a NeoForge 1.21.1 dedicated server**, use the jar whose name ends in `-server`; clients use the normal jar. Never install both. Other versions ship a single jar.

> If Graal is too old, any script that uses a regular expression fails with `No language for id regex found`. Update Graal when you see this.

<a id="wiki-section-3"></a>
## 2. The nekojs folder

After the first launch, your game folder contains a new `nekojs/` folder. These are the folders you will use most:

```text
nekojs/
├── startup_scripts/   # Startup scripts: register new items and blocks. Restart the game after changes
├── server_scripts/    # Server scripts: change recipes, listen to game events. Apply with /nekojs reload
├── client_scripts/    # Client scripts: HUD, key bindings, screens. Apply with /nekojs reload client
├── test_scripts/      # Test scripts: only run when you execute /nekojs test
├── assets/            # Resources: textures, models, language files
├── data/              # Data pack content
├── packs/             # Optional: package scripts for distribution, see "Script packs"
└── config/            # Configuration files, see "Configuration"
```

Script files can be `.js`, `.ts`, `.jsx`, `.tsx`, `.mjs`, `.cjs` or `.py`. If you are new, use `.js`.

> A script in the wrong folder is the most common reason a script "does nothing". Recipe changes belong in `server_scripts`; item registration belongs in `startup_scripts`. See [Script basics](script-basics_us).

<a id="wiki-section-4"></a>
## 3. Your first script

Create `nekojs/server_scripts/hello.js`:

```javascript
console.info('Hello, NekoJS!')
```

Enter a world. If a world is already open, run:

```text
/nekojs reload
```

Then open `logs/nekojs/server.log` in your game folder and you will see `Hello, NekoJS!`. Each script type has its own log: `startup.log`, `server.log`, `client.log` and `test.log`.

> Saving a file does not apply it. Run a reload after every change.

<a id="wiki-section-5"></a>
## 4. Change a recipe

Create `nekojs/server_scripts/recipes.js`:

```javascript
ServerEvents.recipes(event => {
  // Remove every recipe that outputs sticks
  event.remove({ output: 'minecraft:stick' })

  // Add: 1 cobblestone makes 4 sticks
  event.shaped('4x minecraft:stick', [
    'C'
  ], {
    C: 'minecraft:cobblestone'
  }).id('nekojs:cobble_to_sticks')
})
```

Run `/nekojs reload`, put one cobblestone in a crafting table, and you get 4 sticks. The original stick recipes are gone.

If a recipe viewer such as JEI still shows the old recipes, re-enter the world; crafting already uses the new ones. To undo the change, delete the file and reload again.

See [Recipe system](recipe-system_us) for more.

<a id="wiki-section-6"></a>
## 5. Add a new item

New items must be registered in a **startup script**. Create `nekojs/startup_scripts/items.js`:

```javascript
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.maxStackSize = 16
    item.rarity = 'rare'
  })
})
```

**Restart the game** (startup scripts cannot be reloaded), then run `/give @s mymod:cool_gem`.

The item shows a purple-and-black texture and the name `item.mymod.cool_gem` because it has no texture or name yet:

- Texture: put a PNG at `nekojs/assets/mymod/textures/item/cool_gem.png`
- Name: write `{ "item.mymod.cool_gem": "Cool Gem" }` to `nekojs/assets/mymod/lang/en_us.json`

> Once an item has been used in a save, do not delete its registration script, or the items in that save are lost. Try new content in a test world first.

See [Registering new content](registering-new-content_us) for more.

<a id="wiki-section-7"></a>
## 6. Turn on code completion

NekoJS can generate type declarations from your current game, including the mods you have installed, so VS Code can suggest events, methods and item IDs.

1. Run `/nekojs probe` in game.
2. In VS Code, **open the `nekojs/server_scripts` folder** (not the whole game folder).
3. Type `ServerEvents.` and the suggestions appear.

Run `/nekojs probe` again after adding mods or registering new content.

For Python scripts, run `/nekojs probe python` and put `from nekojs import *` at the top of each `.py` file. See [Python scripts](python-scripts_us).

<a id="wiki-section-8"></a>
## 7. Next steps

- [Script basics](script-basics_us): the four script types, load order, and how to find errors.
- [Commands](commands_us): every `/nekojs` command.
- [Event reference](event-reference_us): which game events you can listen to.
- [Global bindings](global-bindings_us): ready-to-use helpers such as `Item`, `Text` and `JsonIO`.
- [FAQ](faq_us): start here when something goes wrong.

<!-- wiki-nav -->

---

[Previous: Platforms and compatibility](platform-compatibility_us) · [Contents](Home) · [Next: Configuration](configuration_us)
