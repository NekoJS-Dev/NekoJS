<!-- wiki-page: home; locale: us -->

> **English** · [中文](home_cn)

<a id="wiki-section-1"></a>
# NekoJS Wiki

NekoJS is a Minecraft scripting runtime based on GraalJS. You can write scripts in JavaScript, TypeScript, JSX, or the Python subset supported by NekoJS to modify recipes, listen to events, register content, and get type information from the game.

This Wiki is organized by use case:

- **Script authors**: start with [Quick start](quick-start_us), then read [Script basics](script-basics_us), [Global bindings](global-bindings_us), and [Event reference](event-reference_us).
- **Modpack authors**: focus on [Recipe system](recipe-system_us), [Registering new content](registering-new-content_us), [Module system](module-system_us), and [TypeScript and JSX](typescript-and-jsx_us).
- **Plugin developers**: start with [Plugin development](plugin-development_us), together with [Type adapters](type-adapters_us), [Event extensions](event-extensions_us), and [Annotations](annotations_us).
- **Project contributors**: read [Project architecture](project-architecture_us), [Probe type generation](probe-type-generation_us), and [Build system](build-system_us).

> **Check platform status first**: if you use Fabric, read [Platforms and compatibility](platform-compatibility_us) first. Fabric already has 26.x build nodes, but its API remains a subset of NeoForge's. Do not assume that every event and binding is available.

<a id="wiki-section-2"></a>
## What you can do with NekoJS

| Capability | Description |
|---|---|
| JavaScript / TypeScript / JSX | Place `.js`, `.mjs`, `.cjs`, `.ts`, `.jsx`, and `.tsx` files directly in script directories; `.py` files are handled by the built-in Python transpiler. |
| JSX client Screen UI | NeoForge 26.x only; see the [JSX client UI](jsx-client-ui_us) guide. |
| ESM and CommonJS | Supports `import` / `export`, dynamic `import()`, top-level await, and `require()`. |
| Recipes and data | Modify recipes with `ServerEvents.recipes`; NeoForge also provides a more complete set of data and asset generation events. |
| Type information | `/nekojs probe` generates TypeScript declarations by default; `/nekojs probe python` generates Python stubs; `/nekojs probe all` runs every backend. |
| Transactional reload | The previous environment is usually retained when SERVER, CLIENT, or TEST scripts fail to load; startup script changes still require a game restart. |
| Node.js core modules | Provides sandbox-restricted shims for `fs`, `path`, `buffer`, `crypto`, `process`, `timers`, `util`, `events`, `assert`, `os`, `test`, and other modules. |

<a id="wiki-section-3"></a>
## Get started in thirty seconds

1. Install NekoJS and [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) versions that match your Minecraft version and loader. Graal 25.1.3.7 or later is required.
2. Start the game and wait for the `nekojs/` workspace to be generated in the game root directory.
3. Write the following in `nekojs/server_scripts/hello.js`:

```javascript
console.info('[NekoJS] Server script loaded')
```

4. Enter a world or run `/nekojs reload`. Once you see the log output, continue with [Quick start](quick-start_us).

<a id="wiki-section-4"></a>
## Current platforms

| Platform | Status |
|---|---|
| NeoForge 1.21.1 | Currently supported, Java 21 |
| NeoForge 26.1.2 | Currently supported, Java 25 |
| NeoForge 26.2.0 | Currently supported, Java 25 |
| Fabric 26.1.2 | Build and runtime smoke coverage exists; not yet published as an official artifact; the API is a subset |
| Fabric 26.2.0 | Build and runtime isolation gates exist; not yet published as an official artifact; the API is a subset |
| Cleanroom 1.12.2 | Separate legacy branch, not part of the current repository's version graph |

See [Platforms and compatibility](platform-compatibility_us) for detailed differences. Refer to [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) for publication status.

<a id="wiki-section-5"></a>
## Where to go next

- **Writing your first script**: [Quick start](quick-start_us)
- **Understanding when scripts run**: [Script basics](script-basics_us)
- **Looking up bindings and methods**: [Global bindings](global-bindings_us)
- **Listening to or canceling events**: [Event reference](event-reference_us)
- **Changing recipes**: [Recipe system](recipe-system_us)
- **Registering items, blocks, or entities**: [Registering new content](registering-new-content_us)
- **Writing client Screens with JSX**: [JSX client UI](jsx-client-ui_us)
- **Splitting modules or using npm packages**: [Module system](module-system_us)
- **Troubleshooting errors**: [FAQ](faq_us)
- **Writing Java plugins**: [Plugin development](plugin-development_us)

<a id="wiki-section-6"></a>
## Maintenance notes

The Wiki still contains some Cleanroom legacy material and English pages that are being translated. For platform differences, refer to [Platforms and compatibility](platform-compatibility_us), the platform labels on the relevant reference page, and the current Release.

<!-- wiki-nav -->

---

[Contents](Home) · [Next: Platforms and compatibility](platform-compatibility_us)
