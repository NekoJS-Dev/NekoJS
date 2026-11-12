<!-- wiki-page: faq; locale: us -->

> **English** · [中文](faq_cn)

<a id="wiki-section-1"></a>
# FAQ

<a id="wiki-section-2"></a>
## Installation and startup

<a id="wiki-section-3"></a>
### Q: The game crashes on startup with missing GraalJS / polyglot errors

A: The **Graal** dependency mod is missing or has the wrong version. NekoJS relies on Graal for the GraalJS runtime. Download a version that **exactly matches** your Minecraft / loader version from [Graal on CurseForge](https://www.curseforge.com/minecraft/mc-mods/graal). Graal 25.1.3.7 or later is required.

Earlier versions have another less obvious problem: they do not register the TRegex regex language, so using regular expressions in scripts (`/…/`, `String.replace(/…/)`, `RegExp`) throws `No language for id regex found`. If you see this error, check your Graal version first.

<a id="wiki-section-4"></a>
### Q: The `nekojs/` folder was not generated

A: It is generated in the game root directory after the first launch. If it is missing:
1. Confirm that NekoJS and Graal loaded correctly (check `mods/` and the startup log).
2. Check game directory permissions.
3. Try running `/nekojs probe` manually; it triggers workspace generation.

<a id="wiki-section-5"></a>
### Q: Which Minecraft versions are supported?

A: See [Platforms and compatibility](platform-compatibility_us). The current repository contains NeoForge 1.21.1, 26.1.2, and 26.2.0, plus Fabric 26.1.2 and 26.2.0 build nodes. The Fabric API is a subset, and Cleanroom belongs to a separate legacy branch.

<a id="wiki-section-6"></a>
## Script execution

<a id="wiki-section-7"></a>
### Q: My script has no effect

A: Check the following:
1. Is the file in the correct directory (`startup_scripts` / `server_scripts` / `client_scripts` / `test_scripts`)? See [Script basics](script-basics_us).
2. Is the extension correct (`.js`/`.mjs`/`.cjs`/`.ts`/`.jsx`/`.tsx`/`.py`)?
3. Is the script disabled by `// disable:`? See [Script properties](script-properties_us).
4. Are all mods listed by `// modloaded:` installed?
5. Did you reload the matching type (`/nekojs reload server` or `/nekojs reload client`) on its owner? Bare `/nekojs reload` defaults to SERVER.
6. Did you **restart the game** after changing startup registrations (new items/blocks)?
7. Run `/nekojs error` to check for errors.

<a id="wiki-section-8"></a>
### Q: I get `事件X is not defined` / `XXX is not a function`

A: This is usually caused by the wrong script type or a misspelled event name:
- Server events (such as `ServerEvents.recipes`) must be written in `server_scripts/`.
- Client events (such as `ClientEvents.tick`) must be written in `client_scripts/`.
- Look up event names in [Event reference](event-reference_us) and binding names in [Global bindings](global-bindings_us).

<a id="wiki-section-9"></a>
### Q: Old code still runs / behavior does not change after reload

A:
- **Shared state**: `global` is shared only within the current script type and survives ordinary reload. Use `shared` for sharing across script types such as SERVER/CLIENT. Both exist only in the same NekoJS runtime process; neither provides network synchronization.
- **Event listeners**: a successful full-type reload replaces generation-owned listeners. If candidate loading fails, the previous active generation intentionally remains. Inspect `/nekojs error` and fix the failing source instead of assuming a second reload will clean it up. External Java/world/file effects are not automatically undone.
- **Recipes**: NeoForge and Fabric 26.x both support `/nekojs reload server` for recipe scripts. NeoForge's JEI/REI recipe viewer may need a client refresh. Cleanroom behavior belongs to a separate legacy branch and is not verified by the current repository. See [Commands](commands_us).
- **Registries (startup)**: reloading startup registrations does not actually add new content to the game; a restart is required.

<a id="wiki-section-10"></a>
### Q: Top-level await / dynamic import fails

A: Confirm that your file is treated as ESM:
- Using the `.mjs` extension is the most reliable option.
- Alternatively, confirm `enableEsmAuthoring = true` in `nekojs/config/engine.toml` (enabled by default).
- Top-level await is only available in ESM modules, not CJS (`.cjs`).

If loading never finishes, inspect the awaited promise and synchronous top-level work. Normal scripts no longer have configurable evaluation timeouts, statement limits or runaway watchdogs; the retired keys do not rescue a pending `await` or infinite loop. See [Configuration](configuration_us) for migration and restart requirements.

<a id="wiki-section-11"></a>
## TypeScript / JSX

<a id="wiki-section-12"></a>
### Q: My `enum` / `namespace` fails

A: NekoJS's TS frontend **already supports** `enum`/`const enum` (compiled to runtime objects) and `namespace`/`module` (compiled to IIFEs). Please open an issue if a particular syntax is unsupported.

<a id="wiki-section-13"></a>
### Q: `.ts` files have no type checking

A: The TS frontend only erases types; it does not check them. Type checking is provided by your IDE (VS Code) using `.neko_probe` declarations. Run `/nekojs probe` to refresh declarations. Add `// @ts-check` at the top of a `.js` file to enable checking.

<a id="wiki-section-14"></a>
### Q: How do I use the automatic JSX runtime?

A: Set `jsxAutomaticRuntime = true` in `nekojs/config/engine.toml`. During workspace generation and probe, NekoJS updates each script directory's `jsconfig.json` to `"jsx": "react-jsx"` and `"jsxImportSource": "nekojs"`, removing classic factory configuration. The `nekojs/jsx-runtime` runtime is built in; you do not need to create `node_modules/nekojs/jsx-runtime.js`. See [TypeScript and JSX](typescript-and-jsx_us) for syntax and configuration; see [JSX client UI](jsx-client-ui_us) to create Minecraft Screens.

Configuration is maintained by NekoJS's workspace/probe flow. If you manually change JSX-related keys, the next generation or probe may restore values consistent with `engine.toml`. Do not keep `jsxFactory` / `jsxFragmentFactory` alongside automatic runtime configuration.

<a id="wiki-section-15"></a>
## Python

<a id="wiki-section-16"></a>
### Q: My Python script runs, but why is there no NekoJS type completion?

A: Runtime global bindings and editor type analysis are different. First run `/nekojs probe python`, then add this at the top of every file using NekoJS APIs:

```python
from nekojs import *
```

Named imports such as `from nekojs import Item, ServerEvents` also work. The transpiler strips these imports; they exist only so the editor can resolve Python stubs. `/nekojs probe` without arguments does not generate Python stubs.

If completion is still missing, confirm that Pylance is enabled in VS Code, the game or script directory is opened as the workspace, and `extraPaths` points to `.neko_probe/python`. You do not need to install NekoJS with pip, and you should not replace this special import with `import nekojs`. See [Python scripts](python-scripts_us) and [Probe type generation](probe-type-generation_us).

<a id="wiki-section-17"></a>
## Recipes

<a id="wiki-section-18"></a>
### Q: I added a recipe, but it does not appear in the recipe viewer (JEI/HEI)

A:
- **NeoForge**: `/nekojs reload` reruns recipe scripts and rebuilds recipes from the cache (server crafting behavior takes effect immediately); JEI/REI viewers may need a client refresh, such as rejoining the world or refreshing the viewer. See [Commands](commands_us).
- **Fabric 26.x**: use the full `/nekojs reload server` path, then inspect real crafting results and current client resources. Recipe-viewer integration is not the same as server recipe application.
- **Cleanroom 1.12.2**: outside the current version graph; this repository does not establish HEI/JEI refresh behavior.

<a id="wiki-section-19"></a>
### Q: A namespaced recipe call fails, or Probe declares it as `never`

A: Check the installed mods, current recipe definition and diagnostics first. A name such as `create:mixing` does not by itself mean that no schema exists. Distinguish these cases:

- **No dedicated constructor was discovered**: check mod loading, native serializer discovery and the relevant plugin or schema registration. You can use `event.custom(...)` with the mod's actual complete recipe format, but must still validate the serializer and real recipe result; it does not synthesize arbitrary recipe semantics.
- **Recognized but explicitly unsupported**: Probe emits `recipeName: never`, and the corresponding runtime path rejects calls. Inspect the source resource, JSON path and `NEKO-52xx` reason. Do not hide the problem by changing the declaration to `any` or manually adding overloads.

In older builds, `NEKO-5242 Unsupported inherited schema: minecraft:shaped` / `minecraft:special` identifies a parent integration gap, not an unsupported entire mod. Current NekuJS supplies the supported native contracts for those two parents and corrects the mistaken use of `hidden` metadata as a runtime call ban. Upgrade, restart and regenerate Probe before checking the result. Other unknown parents or unsupported factory semantics can still yield `never`; editing generated files is not a fix. See [Recipe system](recipe-system_us), [Probe type generation](probe-type-generation_us) and [Error reference](error-reference_us).

<a id="wiki-section-20"></a>
### Q: What is a recipe id, and how do I set it?

A: Every recipe has a unique id (such as `minecraft:stone`). Set it with `.id('yournamespace:name')`; if omitted, it is generated as `nekojs:<prefix>_<hash>`. Use `event.ids()` to list all ids, `event.get(id)` to retrieve by id, and `event.removeById(id)` to remove by id.

<a id="wiki-section-21"></a>
## Registering content

<a id="wiki-section-22"></a>
### Q: My registered item has no texture / appears as a purple-and-black block

A: Resource files are missing. Registered items resolve assets through vanilla resource paths by default:
- Texture: `nekojs/assets/<mod>/textures/item/<name>.png`
- Model: `nekojs/assets/<mod>/models/item/<name>.json`

Place the corresponding files in `nekojs/assets/`. See [Registering new content: assets and localization](registering-new-content_us#wiki-section-14).

<a id="wiki-section-23"></a>
### Q: My registered item's name appears as `item.mymod.xxx`

A: The language file is missing. Add this to `nekojs/assets/<mod>/lang/zh_cn.json`:
```json
{ "item.mymod.xxx": "中文名" }
```

<a id="wiki-section-24"></a>
### Q: My new item did not appear after reload startup

A: Startup scripts execute during game startup. `/nekojs reload startup` currently rejects execution; changing item, block, or other registrations requires a game restart. See [Script basics](script-basics_us).

<a id="wiki-section-25"></a>
## Modules / npm

<a id="wiki-section-26"></a>
### Q: `require('xxx')` reports `module not found`

A:
1. Did you put the package in `nekojs/node_modules/<package-name>/`?
2. Is the package **pure JS**? Native bindings (compiled C/C++) are **not supported**.
3. Is the path correct? `require('./xxx')` is relative to the current file; `require('xxx')` resolves through node_modules.

<a id="wiki-section-27"></a>
### Q: I want to use an npm package with native bindings

A: They are not supported. Use a pure-JS alternative or import a Java equivalent with `java:`.

<a id="wiki-section-28"></a>
### Q: Is an import such as `java:java/lang/Integer` correct?

A: Yes. The rule is the `java:` prefix followed by a slash-separated path. See [Module system: importing Java classes](module-system_us).

<a id="wiki-section-29"></a>
## IDE / type information

<a id="wiki-section-30"></a>
### Q: VS Code has no completion

A:
1. Run `/nekojs probe` to generate `.neko_probe/`.
2. **Open `nekojs/server_scripts/` (or the relevant directory) as the root** in VS Code, rather than the entire game directory. This allows `jsconfig.json` to associate `.neko_probe` correctly.
3. Confirm that `.neko_probe/` is in the game root directory (alongside `nekojs/`).

<a id="wiki-section-31"></a>
### Q: Completion types do not match runtime behavior

A: Run `/nekojs probe` to regenerate them. Adding a mod or registering new content can make declarations outdated. This is a known limitation (probe is triggered manually).

<a id="wiki-section-32"></a>
## Performance

<a id="wiki-section-33"></a>
### Q: My tick event is very slow

A: `ServerEvents.tickPre`/`tickPost` runs on server ticks (nominally 20 TPS, not a guaranteed 50ms wall-clock interval). Keep owner-thread callbacks short. Cache or split work across ticks; timers do not move blocking work onto a safe worker thread.

<a id="wiki-section-34"></a>
### Q: Script loading is slow

A: GraalJS startup and script compilation can have warm-up costs; later speed depends on the workload. Check package count and top-level I/O before drawing conclusions, and measure the actual slow path.

<a id="wiki-section-35"></a>
## Security

<a id="wiki-section-36"></a>
### Q: My script was denied file access

A: This is a sandbox restriction: access is limited to the game directory. See [Node.js compatibility: access restrictions](nodejs-compatibility_us#wiki-section-9). To relax this (single-player/trusted environments only), see `nekojs/config/engine.toml`.

<a id="wiki-section-37"></a>
### Q: Is NekoJS safe on multiplayer servers?

A: Treat scripts as **semi-trusted code**: only run scripts you trust. The sandbox restricts Java class access and file scope, but a name-based blacklist only blocks class lookup. Object graphs returned by Java methods are controlled by Graal's `HostAccess` (currently `HostAccess.ALL`), so instances of blacklisted classes may still reach scripts through method return values. Client script uploads through the in-game editor have been removed. The read-only error UI and script network events are not remote editing services; trusted maintainers must still deploy server scripts.

AI authoring HTTP and batch queries are read-only, but separately authorized live MCP tools can execute allowlisted commands or reload a selected runtime. Keep default-off controls off unless deliberately needed, never expose or commit the bearer token, and do not retry a timed-out action whose effects are unknown. See [AI authoring security and live controls](ai-support_us).

<a id="wiki-section-38"></a>
## Still unresolved?

- Check [Event reference](event-reference_us), [Global bindings](global-bindings_us), and [Recipe system](recipe-system_us) to confirm API usage.
- Open an issue (see the link in the repository README).

<!-- wiki-nav -->

---

[Previous: Configuration](configuration_us) · [Contents](Home) · [Next: Script basics](script-basics_us)
