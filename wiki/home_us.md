<!-- wiki-page: home; locale: us -->

> **English** · [中文](home_cn)

<a id="wiki-section-1"></a>
# NekoJS Wiki

NekoJS is a Minecraft scripting mod. Write scripts in JavaScript (or TypeScript, or Python) to change recipes, react to game events, and add new items and blocks — no Java and no recompiling.

If you have used KubeJS, this will feel familiar: scripts live in your game folder, and one command applies your changes.

<a id="wiki-section-2"></a>
## Where should I start?

| You are… | Read first | Then |
|---|---|---|
| New to NekoJS | [Quick start](quick-start_us) | [Script basics](script-basics_us) |
| A modpack author changing recipes or adding items | [Recipe system](recipe-system_us), [Registering new content](registering-new-content_us) | [Event reference](event-reference_us), [Global bindings](global-bindings_us) |
| Using TypeScript or splitting code into files | [TypeScript and JSX](typescript-and-jsx_us) | [Module system](module-system_us) |
| Writing scripts in Python | [Python scripts](python-scripts_us) | [Python feature support](python-feature-support_us) |
| Extending NekoJS with a Java plugin | [Plugin development](plugin-development_us) | [Type adapters](type-adapters_us), [Event extensions](event-extensions_us) |
| Contributing to NekoJS itself | [Project architecture](project-architecture_us) | [Build system](build-system_us) |

If something goes wrong, check the [FAQ](faq_us). If you see a `NEKO-xxxx` code, look it up in the [Error and log reference](error-reference_us).

<a id="wiki-section-3"></a>
## Try it in one minute

1. Put NekoJS and its dependency [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) (25.1.3.7 or newer) in `mods/` and start the game.
2. A `nekojs/` folder appears in your game folder. Create `nekojs/server_scripts/hello.js`:

```javascript
console.info('Hello, NekoJS!')
```

3. Enter a world, run `/nekojs reload`, then open `logs/nekojs/server.log`. The line is there.

For the full walkthrough — changing a recipe, adding an item, and turning on code completion — see [Quick start](quick-start_us).

<a id="wiki-section-4"></a>
## What can NekoJS do?

| You want to… | Use | Details |
|---|---|---|
| Add, remove or change recipes | `ServerEvents.recipes` | [Recipe system](recipe-system_us) |
| Add new items, blocks or entities | `RegistryEvents.register` (startup scripts) | [Registering new content](registering-new-content_us) |
| Run code when players act, blocks break, and so on | `PlayerEvents`, `BlockEvents` and other events | [Event reference](event-reference_us) |
| Add custom commands | `CommandEvents.register` | [Event reference](event-reference_us#wiki-command-events) |
| Draw a HUD or build screens | `ClientEvents`, JSX screens | [Client rendering](client-rendering_us), [JSX client UI](jsx-client-ui_us) |
| Get code completion in your editor | `/nekojs probe` generates type declarations | [Probe type generation](probe-type-generation_us) |
| Use pure-JavaScript libraries from npm | `import` / `require` | [Module system](module-system_us) |

<a id="wiki-section-5"></a>
## Supported versions

| Loader | Minecraft | Java |
|---|---|---|
| NeoForge | 1.21.1 | 21 |
| NeoForge | 26.1.2, 26.2.0 | 25 |
| Fabric | 26.1.2, 26.2 | 25 |

The Fabric builds run scripts, change recipes and support most common events, but some NeoForge-only features are missing. Read [Platforms and compatibility](platform-compatibility_us) before using Fabric. Downloadable versions are listed on [Releases](https://github.com/NekoJS-Dev/NekoJS/releases).

<!-- wiki-nav -->

---

[Contents](Home) · [Next: Platforms and compatibility](platform-compatibility_us)
