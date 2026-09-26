<!--
  Translated document. Keep the metadata block below up to date.
  See docs/translation-guide.md for conventions and the term glossary,
  and docs/translation-workflow.md for the procedure.
-->

> **English** · [中文](README.md)
>
> | | |
> |---|---|
> | Source document | [`README.md`](README.md) |
> | Source version | commit `7dd94cc7`, 2026-09-16 |
> | Translation updated | 2026-09-13 |
>
> If `README.md` has changed since that commit, the Chinese version is correct and this one may be out of date.

# NekoJS

<img src="icon.png" width="256" height="256" alt="NekoJS icon">

**A modern, fast, and elegant Minecraft scripting engine**

NekoJS is a Minecraft JavaScript scripting runtime built on **NeoForge** and **GraalVM/GraalJS**. It is aimed at modpack authors and mod developers. The goal is to provide a scripting experience inside Minecraft that is close to modern frontend engineering practice.

**The [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) mod (25.1.3.6+) is a required dependency. Use the Minecraft and NeoForge versions listed on the current release page.**

(Some of the code was generated with ChatGPT and GLM 5.2. The mascot image was generated with ChatGPT.)

## Key features

* **Powered by GraalVM**: supports the latest ECMAScript standard. It replaces the older Rhino and Nashorn engines, so you get modern JavaScript syntax and the capabilities of the GraalJS runtime.
* **Built-in TypeScript and JSX support**: NekoJS includes an erasable TypeScript frontend for `.ts` and lightweight classic runtime lowering for `.jsx` and `.tsx`. More advanced TS, TSX, and JSX syntax will also be added to the NekoJS language frontend itself. A Python subset transpiler frontend is also built in: `.py` files load automatically, no external runtime is needed, and probe generates `.pyi` stubs.
* **Native ESM runtime**: supports `import` and `export`, live bindings, circular dependencies, top-level await, `import.meta`, dynamic `import()`, and ESM/CJS interoperability.
* **Node.js compatible API**: includes shims for the core modules `fs`, `path`, `buffer`, `process`, `timers`, `util`, `events`, `assert`, `os`, and `test`.
* **Developer experience first**: on startup, NekoJS generates the workspace directory, the editor configuration (`jsconfig.json`), and catalog metadata that external tools can consume. The built-in probe walks the catalog directly to generate declaration files. It has several backends: TypeScript `.d.ts` and Python `.pyi` stubs, selected through the `/nekojs probe` subcommands and the `languages.typescript` and `languages.python` settings in `probe.toml`. You get editor hints and code completion without installing an external mod such as ProbeJS.
* **Modern modules and the npm ecosystem**: supports multi-file development with `require()` and `module.exports`, and you can use pure JavaScript npm dependencies inside the `nekojs` directory. Packages containing native bindings are not supported, and this is not a complete Node.js runtime.
* **Server-side hot reload**: server scripts can be reloaded with `/nekojs reload`. Scripts that register content at startup still require a game restart.
* **Recipe hot reload**: on Cleanroom 1.12.2, `/nekojs reload server` unfreezes the registry, removes the old NekoJS recipes, re-runs the recipe scripts, and freezes it again. A mixin refreshes the HEI or JEI recipe panel automatically. The NeoForge platforms (26.x and 1.21.1) also support hot reload: a reload re-runs the recipe scripts and replaces `RecipeManager.recipes` wholesale (mixin `RecipeManagerMixin#nekojs$applyScripts`, rebuilding the working set from the base recipe JSON that was cached permanently during the prepare phase).
* **Restricted security sandbox**: NekoJS limits which files a script can reach and filters access to dangerous Java classes. Scripts should still be treated as trusted code, especially when the remote sync feature is used on a multiplayer server.
* **Multi-platform support**: supports NeoForge 26.1, 26.2, and 1.21.1 as well as Cleanroom 1.12.2 (Forge), sharing the `common` infrastructure.
* **Script member validation**: at load time, NekoJS statically scans member access on global bindings and event callbacks, and reports spelling mistakes immediately (for example `Utils.randmInt` gives "Did you mean 'randomInt'?"). It can be turned off with the `scriptMemberValidation` option in `config/nekojs-engine.toml`, which removes the cost entirely.
* **Replaceable probe implementation**: the built-in probe uses `ProbeCoordinator` to collect types once and dispatch them to pluggable `ProbeBackend` backends. Third-party plugins can register their own backend with `ProbeBackendRegistry.register(backend, source)`. The registry is locked at bootstrap, and a conflict fails fast with an error.

---

## Directory layout

The first time you start the game with NekoJS installed, a `nekojs` folder is created in the game root directory:

```text
.neko_probe/                # NekoProbe type declaration library: generated declarations, in one subdirectory per language (typescript/ and python/ by default, configurable with [languages.<id>].outputDir in probe.toml). Sits next to the nekojs directory.
nekojs/
├── startup_scripts/   # Game startup scripts: register items, blocks, and other core content (changes require a game restart)
│   └── tsconfig.json  # Editor configuration: links to the .neko_probe type library in the root directory
├── server_scripts/    # Server scripts: recipe changes and event listeners. Supports /nekojs reload
│   └── tsconfig.json
├── client_scripts/    # Client scripts: GUI rendering, particle effects, key bindings, and other visual logic
│   └── tsconfig.json
├── test_scripts/     # Test scripts: run explicitly with /nekojs test
│   └── tsconfig.json
├── node_modules/      # External library directory: standard Node module resolution, for pure JavaScript dependencies
├── assets/            # Assets directory
├── data/              # Data pack directory
└── config/            # probe.toml (type generation). The engine configuration is at config/nekojs-engine.toml in the game root directory (the old nekojs/config/engine.toml is a read-only fallback)
```

The directories loaded automatically are `startup_scripts/`, `server_scripts/`, and `client_scripts/`. `test_scripts/` is a test environment that runs explicitly with `/nekojs test`. Script files can be `.js`, `.mjs`, `.cjs`, the built-in erasable `.ts`, lightweight `.jsx` and `.tsx` with classic runtime lowering, and `.py` (a Python subset, loaded automatically from the same script directories). More complex TS and TSX syntax will be consolidated into the NekoJS language frontend over time. A script file can declare properties in a first-line comment: `// priority: <n>` and `// after: <path>`. `after:` is enforced: scripts with the same priority load in topological order, an unresolved reference produces a warning, and a cycle falls back to the original order.

## Source layout

```text
common-api/                      # Data contracts and the conversion SPI incubation layer (no Minecraft, Forge, or Graal dependencies; the plugin entry API is still in common)
common-api-processor/            # Compile-time annotation processor: spec coverage checks for the common-api contract
common/                          # Cross-platform shared code
└── src/main/java/com/tkisor/nekojs/
    ├── core/                    # Core runtime: Graal Context and Engine, ClassFilter, VFS
    ├── script/                  # Script management: NekoJSScriptManager, ScriptType, reload
    ├── api/                     # Public API: NekoJSPlugin, JSTypeAdapter, event declarations, catalog
    ├── bindings/                # JavaScript global bindings
    ├── probe/                   # Type declaration generation (.d.ts and .pyi, several backends)
    ├── eventbus/                # Event bus implementation
    ├── plugin/                  # Plugin system: extension points, bootstrap snapshot
    ├── network/                 # Network sync
    └── wrapper/                 # Script-friendly wrappers

platforms/
├── cleanroom-1.12.2/            # Cleanroom 1.12.2 (Forge)
│   └── src/main/java/...
├── neoforge-shared/             # Source set shared by the three NeoForge platforms (1.21.1, 26.1, 26.2; not a Gradle module)
│   └── src/main/java/...
├── neoforge-26-shared/          # Source set shared by 26.x (main sources for 26.1 and 26.2; src/test is reused by all three NeoForge platforms; not a Gradle module)
│   └── src/main/java/...
├── neoforge-26.1/               # NeoForge 26.1
│   └── src/main/java/...
├── neoforge-26.2/               # NeoForge 26.2
│   └── src/main/java/...
└── neoforge-1.21.1/             # NeoForge 1.21.1
    └── src/main/java/...
```

---

## Importing Java modules

NekoJS treats Java packages and classes as special `java:` modules. ESM rewrites a Java import into a synthetic module. In CommonJS, `require()` returns the Java namespace or class proxy directly.

### Package-level modules

```ts
import { Integer, $Integer, Math as JavaMath } from 'java:java/lang'
const { Integer, $Integer, Math: JavaMath } = require('java:java/lang')
```

- A package-level module is a lazily loaded namespace proxy. An ordinary name is looked up as a property, and `$Class` maps directly to `Java.type('java.lang.Class')`.
- This means `Integer`, `$Integer`, and `Math` or `JavaMath` all work.

### Class-level modules

```ts
import IntegerClass, { $Integer } from 'java:java/lang/Integer'
const IntegerClass3 = require('java:java/lang/Integer')
```

- A class-level module returns the Java class proxy directly, and also exposes `default` and `$Class`.
- This is the most direct form when you want one specific Java class.

### Compatibility boundaries

- Only the `java:` prefix is accepted.
- Only slash-separated paths are accepted: `java:java/lang`, `java:java/lang/Integer`.
- `import('java:java/lang')` and `import('java:java/lang/Integer')` produce a synthetic ESM module with `default` and `namespace`.
- For ESM static imports and dynamic imports, prefer the `java:` slash form.
- The type generator writes `java:package/path` plus `$Class` first, then adds class-level modules of the form `java:package/path/Class` as needed.

## Quick start

### 1. Write a module library (`utils.ts`)

```typescript
// server_scripts/utils.ts

function calculateDamage(base: number, multiplier: number): number {
    return base * multiplier;
}

const MOD_NAME: string = "NekoJS";

module.exports = {
    calculateDamage,
    MOD_NAME
};
```

### 2. Write the main logic and event listeners (`main.ts`)

```typescript
// server_scripts/main.ts

const { calculateDamage, MOD_NAME } = require('./utils.ts');

console.log(`[${MOD_NAME}] Loading custom logic...`);

ServerEvents.tickPre(event => {
    // Your tick logic
});
```

---

## Editor type checking

The type declarations NekoJS generates in `.neko_probe/` expose global objects such as `ServerEvents` and `BlockEvents`, together with their event parameter types, to the editor. Most mistakes can therefore be found **before the event fires**:

* Writing scripts in `.ts` gives you full editor type checking. An existing `.js` script only needs `// @ts-check` on its first line to enable checking for that file.
* Spelling mistakes, such as writing `event.rec` instead of `event.recipes`, are marked immediately. You do not need to `import` any types, because the signature of the global event object infers the type of `event` automatically.
* The same mistakes are caught at runtime in the game. Accessing a member that does not exist in an event callback, or using an undefined variable, is recorded in the error panel. View it with `/nekojs view_all_errors`.

> Note: the TypeScript frontend built into NekoJS supports erasable syntax (type annotations, `type` and `interface`, generics, `as` and `satisfies`, `import type` and `export type`, `declare`, parameter properties such as `constructor(public x)`, `enum` and `const enum`, `namespace` and `module`, class member modifiers, `?.` and `!`, function overload signatures, and so on). These are erased before the script runs, or lowered to a runtime IIFE or assignment. Decorators (`@Decorator`) are **not supported**. NekoJS is a scripting engine, not a TypeScript framework; a decorator produces a clear error, so use an ordinary function wrapper instead.

---

## Security model

NekoJS scripts run in a restricted GraalJS environment, but this is not a platform for executing untrusted code. Run only scripts you trust, and in particular do not grant remote editing rights to unknown players on a public server.

The current security boundary includes:

* Filesystem access is restricted to the game directory, and symbolic link escapes are detected for paths that already exist.
* Java class access is filtered by `ClassFilter` against a blocklist of names. It intercepts class lookup through `Java.type` and `java:` modules. Threads, reflection, ASM, processes, networking, and low-level IO are blocked by default, as are AWT and Swing (`java.awt`, `javax.swing`, `javax.imageio`), RMI and JNDI (`java.rmi`, `javax.naming`), JDBC (`java.sql`, `javax.sql`), `java.lang.Module`, the Graal and Truffle internals (`org.graalvm`, `com.oracle.truffle`), and the NekoJS internals (`com.tkisor.nekojs.core`).
* `allowThreads`, `allowReflection`, and `allowAsm` in `config/nekojs-engine.toml` (in the game root `config` directory; the old location `nekojs/config/engine.toml` is a read-only fallback, and an old file is read with a migration warning) are dangerous capability switches and are off by default.
* `scriptMemberValidation` (on by default) statically scans member access on global bindings and event callbacks when a script loads, and reports spelling mistakes immediately. Turning it off skips the cost of parsing the AST. Leave it on during development; it can be turned off when shipping a modpack.
* `scriptEvaluationTimeoutSeconds` (default 30; zero or negative means no limit) limits how long a script entry point may take to evaluate. If a top-level await or a module load never completes, it fails with a timeout instead of hanging the server thread.
* `scriptStatementLimit` (default 50,000,000, a generous limit; setting it to 0 disables it) limits the total number of statements one script context may execute. When exceeded, Graal closes that context, which prevents an infinite loop from consuming the CPU.
* In-game workspace sync should only be given to trusted administrators. The sync feature is restricted to the script directories and to script file extensions.

> A note on the sandbox boundary: the name blocklist only intercepts **class lookup** through calls such as `Java.type`. The object graph returned from a Java method is governed by the Graal `HostAccess` setting, which is currently `HostAccess.ALL`, so an instance of a blocked class can still reach a script as a method return value. Scripts should therefore be treated as **semi-trusted code**, and untrusted third-party scripts should not be run.

---

## Extending the ecosystem

### Language frontends

The NekoJS core aims to stay lightweight and stable, with TypeScript support for `.ts` built in: type annotations, `type` and `interface`, `import type` and `export type`, generics (including generic arrow functions such as `<T>(x: T) => T`), `as` and `satisfies`, inline `import { x, type T }`, parameter properties, `enum` and `namespace`, and class member modifiers are erased or lowered in the Java frontend, and the result then goes through the NekoJS ESM and CJS pipeline. NekoJS also has `.jsx` and `.tsx` lowering built in. The default is the classic runtime (`globalThis.__nekoJsxFactory(...)` and `globalThis.__nekoJsxFragment(...)`), with support for HTML entity decoding, namespaced tags (`<svg:rect/>`), and generic components (`<Foo<T>/>`). Setting `jsxAutomaticRuntime = true` in `config/nekojs-engine.toml` switches to the standard automatic runtime, which imports `jsx`, `jsxs`, and `Fragment` from `nekojs/jsx-runtime` and puts children in `props.children`. Inside the `nekojs/` workspace, put the runtime module at the bare module path `node_modules/nekojs/jsx-runtime.js`.

The direction from here is to keep improving the NekoJS language frontend itself, rather than depending on an external NekoSWC mod for advanced TS, TSX, and JSX conversion. The script language plugin registry remains available for third-party language extensions, but the TypeScript, JSX, source map chain, and diagnostics in NekoJS will be implemented in NekoJS itself first.

NekoJS also has a Python subset transpiler frontend built in, with no external runtime. It supports `match`/`case`, `@staticmethod`, `@classmethod`, and `@property`, generators, `for`/`else`, `**kwargs`, f-strings, and source maps. `.py` files load automatically from the same script directories as `.js` and `.ts`, and the `PythonProbeBackend` generates `.pyi` stubs for Python scripts.

### Plugin extension point example

A NekoJS plugin registers its capabilities through typed hooks with several entry points, such as `registerBindings`, `registerAdapters`, and `registerEvents`. If an external mod needs to define a new plugin type, it can register an extension point descriptor with `NekoPluginExtensionProvider` during the first bootstrap phase. Bootstrap only moves on to the second phase, collecting the contributions themselves, once all plugin types have been registered.

For example, a mod that wants to provide a startup-only bindings plugin type can first define a new typed plugin interface:

```java
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

public interface StartupBindingsPlugin extends NekoJSPlugin {
    void registerStartupBinding(BindingRegistry registry);
}
```

Then register that new extension point from a plugin that NekoJS discovers:

```java
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.api.ScriptType;

@RegisterNekoJSPlugin
public final class MyExtensionPointPlugin implements NekoJSPlugin, NekoPluginExtensionProvider {
    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        registry.register(NekoPluginExtensionPoint.of(
                "mymod:startup_bindings",
                StartupBindingsPlugin.class,
                (plugin, context) -> plugin.registerStartupBinding(context.bindings().at(ScriptType.STARTUP))
        ));
    }
}
```

After that, any other plugin that implements the interface is collected by the same extension point:

```java
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

@RegisterNekoJSPlugin
public final class MyStartupApiPlugin implements StartupBindingsPlugin {
    @Override
    public void registerStartupBinding(BindingRegistry registry) {
        registry.register("MyStartupApi", MyStartupApi.class);
        if (registry.scriptType() != ScriptType.STARTUP) { // always false, see extension point registry
            registry.register("NotStartup", new NotStartupValue());
        }
    }
}
```

The lifecycle of this process is fixed: first every `@RegisterNekoJSPlugin` class is scanned and instantiated, then every plugin type registered by a `NekoPluginExtensionProvider` is collected, and only after the extension point registry is frozen does each plugin's typed hook run. An extension point collector can only reach the restricted `NekoPluginExtensionContext` registry; it never receives the internal collections of `NekoPluginRuntime`. Every registry accepts writes only during the bootstrap collection phase, and fails fast on a late registration once bootstrap has finished.

### Plugin load order and conditions

`@RegisterNekoJSPlugin` takes three parameters that control loading:

- `priority` (int, default 1000): **a higher number loads earlier**. The built-in core plugin uses `NekoJSPlugin.CORE_PRIORITY` (`Integer.MAX_VALUE`) to make sure infrastructure such as adapters and bindings is registered first.
- `clientOnly` (boolean, default false): load only in the client process. A dedicated server skips the plugin.
- `requiredMods` (String[], default empty): load only when every listed mod is present (AND semantics).

```java
@RegisterNekoJSPlugin(priority = 500, clientOnly = true, requiredMods = {"jei", "mekanism"})
public final class MyIntegrationPlugin implements NekoJSPlugin { ... }
```

Filtering and ordering happen in one place, `NekoJSBasePluginManager.registerClass` in `common`. The plugin loaders on the four platforms only discover the annotated classes.

The recipe lifecycle uses the same typed hooks: an external plugin can implement `RecipeLifecyclePlugin`, or register `beforeRecipeLoading` and `afterRecipes` in `registerRecipeLifecycleHooks`. These two hooks run before and after the server recipe script event respectively, and operate on a controlled `RecipeLifecycleContext` rather than the internal mutable map of the recipe manager.

### Data-driven recipe methods

NekoJS can add lightweight method definitions to `event.recipes.<namespace>.<type>(...)` from data pack resources, at this path:

```text
data/<namespace>/nekojs/recipe_types/<type>.json
```

For example:

```json
{
  "type": "create:mixing",
  "constructors": [["result", "ingredients"]],
  "fields": {
    "result": { "path": "results", "kind": "item_stack", "array": true },
    "ingredients": { "path": "ingredients", "kind": "ingredient", "array": true }
  }
}
```

A script can then write:

```js
event.recipes.create.mixing('create:brass_ingot', [
  'minecraft:copper_ingot',
  'create:zinc_ingot'
])
```

This is only a lightweight, JSON-first facade: fields are written through a JSON path, and `kind` converts a script value into data pack JSON. An unknown namespace or type can still use the raw JSON fallback.

### NekoProbe

Type generation is built into this repository. `ProbeCoordinator` collects classes once, then dispatches the shared IR to each `ProbeBackend`, built in or registered by a third party, to render on its own (the built-in backends are TypeScript `.d.ts` and Python `.pyi`). Third-party plugins can customise types and code completion through the `probe.assign_type`, `probe.modify_type`, `probe.add_global`, and `probe.snippets` events, and can merge editor configuration through an editor-config contributor. The `NekoScriptCatalog` metadata and the workspace layout are still provided, and kept stable, by NekoJS itself.

Generation can be triggered manually with `/nekojs probe`. With no arguments it runs only the built-in TypeScript backend. The subcommands are `all` (run every registered backend), `list` (list the backends), `reload` (re-read the configuration), `enable` and `disable` (persist a switch), and `<language> [name]` (choose a language or backend). The configuration file is `<game>/nekojs/config/probe.toml`.

---

## Event system

NekoJS provides an event listener mechanism for responding to state changes in Minecraft.

### Implemented events

NekoJS provides 16 event groups (based on NeoForge 26.x, including `ScriptEvents` and `KeyBindEvents`; use probe as the reference for which platforms support what). The list below shows only the common events in each group:

```text
Server events (ServerEvents)          about 13  tickPre / tickPost / recipes / afterRecipes / tags ...
Player events (PlayerEvents)          about 17  loggedIn / loggedOut / chat / tickPre / tickPost /
                                                cloned / respawned / changedDimension / advancement /
                                                container* / inventory* / entityInteract /
                                                crafted / smelted / destroyed
Entity events (EntityEvents)          about 13  damagePre / damagePost / death ...
Block events (BlockEvents)            about 11  broken / rightClicked / placed ...
Item events (ItemEvents)              about 8   rightClicked / tooltip / crafted ...
Registry events (RegistryEvents)      about 12  item / block / entityType / fluid / creativeModeTab / soundEvent / mobEffect / potion / particleType / paintingVariant / villagerType / enchantment (startup events)
Command events (CommandEvents)        about 2   register ...
Goal events (GoalEvents)              about 1
Level events (LevelEvents)            about 10  loaded / unloaded / tick ...
Network events (NetworkEvents)        about 2
Capability events (CapabilityEvents)  about 1   register (startup event)
Recipe viewer events (RecipeViewerEvents)  about 5  addEntries / removeEntries / removeRecipes / removeCategories / addInformation (client, requires JEI)
Probe events (ProbeEvents)            about 4   modifyType / assignType / addGlobal / snippets (server, fired during /nekojs probe)
Client events (ClientEvents)          about 13
```

> The complete and authoritative event signatures are the ones probe generates in `.neko_probe/@side-only/<type>/events/index.d.ts`. Event groups keep growing, and this table lists only representative events.

### Event types

- **Ordinary events (EventHandler)**: for global event listening.
- **Targeted events (TargetedEventHandler)**: events with a specific target, such as an entity, block, or item.
- **Startup events (startup)**: fired once, when the game starts.
- **Server events (server)**: run when the server or the save loads. These support hot reload.

### Usage examples

```typescript
ServerEvents.tickPre(event => {
    console.log('Server tick starting');
});

PlayerEvents.loggedIn(event => {
    const player = event.player;
    console.log(`Player ${player.name} logged in`);
});

EntityEvents.damagePre(event => {
    const entity = event.entity;
    const damage = event.damage;
    console.log(`Entity ${entity.type} is about to take ${damage} damage`);
});
```

### Custom startup event methods

`startup_scripts` can use `ScriptEvents` to register a native NeoForge event as a friendlier server or client event method:

```js
// startup_scripts/src/events.js
ScriptEvents.server(event => event.register('CustomServerEvents', 'playerTick', 'net.neoforged.neoforge.event.tick.PlayerTickEvent.Post'))
ScriptEvents.client(event => event.register('CustomClientEvents', 'screenOpening', 'net.neoforged.neoforge.client.event.ScreenEvent.Opening'))
```

You can then listen in the matching environment:

```js
// server_scripts/src/main.js
CustomServerEvents.playerTick(event => {
  console.info(`player tick: ${event.getEntity().getName().getString()}`)
})
```

The object form can set the priority and whether cancelled events are received:

```js
ScriptEvents.server(event => event.register({
  group: 'CustomServerEvents',
  name: 'rightClickBlock',
  event: 'net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock',
  priority: 'normal',
  receiveCancelled: false
}))
```

A custom event is not written into the static event table of the plugin bootstrap, but it does enter the probe event catalog and type generation (`.d.ts` and `.pyi`, with the payload declaration generated by reflecting on the event class). A startup reload refreshes the event definitions, and a server or client reload clears the listeners of the matching scripts, which avoids duplicate callbacks.

## Generating data and assets

A script can generate data pack and resource pack JSON (loot tables, advancements, models, lang files, and so on) during a resource reload, writing to `<gameDir>/nekojs/data` and `<gameDir>/nekojs/assets`. Both are registered as TOP-position data and resource packs, and are read lazily so that the reload order is correct:

```js
ServerEvents.generateData('after_mods', event => {
  event.json('minecraft:loot_tables/blocks/stone.json', {
    type: 'minecraft:block',
    pools: []
  });
  event.text('minecraft:nekojs/hello.txt', 'content');
});

ClientEvents.generateAssets('after_mods', event => {
  event.json('minecraft:models/block/foo.json', { parent: 'minecraft:block/cube_all' });
});

ClientEvents.lang('en_us', event => {
  event.add('minecraft:item.foo', 'Foo Item');
});
```

- `generateData` and `generateAssets` are targeted by phase (currently `after_mods`). `lang` is targeted by language code (`en_us` and so on), and entries are merged into `lang/<lang>.json`, keeping existing entries.
- Every server reload, and every client resource reload (F3+T), fires these again, so scripts must be idempotent. Writing the same file again overwrites it.
- An external mod can generate data through the `NekoJSPlugin.generateData`, `generateAssets`, and `generateLang` hooks, which run before the script events.

Differences on the Cleanroom 1.12.2 platform:

- `generateData` writes to `<worldDir>/data` (loot tables, advancements, functions). It is triggered by `/nekojs reload server`, which calls `server.reload()`, the equivalent of vanilla `/reload`, to apply the content.
- `generateAssets` writes to `<gameDir>/nekojs/assets`, which `MinecraftMixin` registers as a `FolderResourcePack`. It takes effect on every F3+T, or the first time you enter the game. Because `LanguageManager` is the first resource reload listener, generation happens before models and textures load.
- `lang` uses the `.lang` text format rather than JSON files. Entries are injected directly into the `Locale` of the current language by `LanguageManagerMixin` (a mixin injection, no reflection), and synchronised to `I18n` with `LanguageMap.replaceWith`.

## Recipe viewer integration (JEI)

The NeoForge platforms (1.21.1, 26.1, 26.2) include JEI integration. Scripts listen in CLIENT scripts (aligned with the KubeJS `RecipeViewerEvents`, in a reduced form):

```js
// Hide a specific item from JEI (it is not removed completely)
RecipeViewerEvents.removeEntries('item', event => {
  event.add('minecraft:stone');
});

// Add an entry to JEI, such as a custom item generated by a script
RecipeViewerEvents.addEntries('item', event => {
  event.add('minecraft:stone');
});

// Hide a specific recipe (a category can be targeted)
RecipeViewerEvents.removeRecipes(event => {
  event.remove('minecraft:stone_from_cobblestone');
  event.removeFromCategory('minecraft:crafting', 'minecraft:stick');
});

// Hide a whole viewer category
RecipeViewerEvents.removeCategories(event => {
  event.remove('minecraft:crafting');
});

// Attach a tooltip to an item (applied during JEI registration, updated after F3+T)
RecipeViewerEvents.addInformation(event => {
  event.add('minecraft:stone', '§7This is stone.');
});
```

- Entry events are targeted by type (`'item'` or `'fluid'`, taking an item or fluid ID or object). Recipes and categories are targeted by ID.
- The events fire when JEI rebuilds at runtime, on every resource reload, so scripts must be idempotent.
- This only works when JEI is installed. REI and EMI integration are out of scope for now.

## Registering content (fluids and creative tabs)

As well as items, blocks, and entity types, startup scripts can register fluids and creative tabs (NeoForge 1.21.1, 26.1, 26.2):

```js
RegistryEvents.fluid(event => {
  event.create('nekojs:molten_iron')
    .displayName('Molten Iron')   // Translation key; provide the text with a lang event
    .density(2000)
    .viscosity(2000)
    .temperature(1500)
    .lightLevel(12);
});

RegistryEvents.creativeModeTab(event => {
  event.create('nekojs:custom')
    .title('Custom Tab')
    .icon('minecraft:iron_ingot')
    .add('minecraft:stone')
    .add('minecraft:diamond');
});
```

- Fluids are implemented as simple fluids: a single source fluid, no flowing, no liquid block, and the bucket returns air. Rendering on 26.x is model-driven, with textures provided by a resource pack model. Textures on 1.21.1 are also left to a resource pack or a later extension.
- `FluidRegistryEventJS` uses a PENDING map to handle the two registry branches, `FLUID_TYPES` and `FLUID`. Entries are de-duplicated by ID, so it is idempotent.
- Creative tab entries are captured when the tab is registered. Adding entries after registration requires registering again, or re-entering the save.

---

## Roadmap

For future plans, see [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Contributing

NekoJS is under active development. Issues reporting bugs, feature suggestions, and pull requests are all very welcome.

* **QQ group**: 1158525822 [click to join the group chat "NekoJS 魔改交流群（？"](https://qm.qq.com/q/rbryak0K6k)

---

## License

This project is open source under the [LGPL-3.0 License](LICENSE).
