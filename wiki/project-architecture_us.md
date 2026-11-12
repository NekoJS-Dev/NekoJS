<!-- wiki-page: project-architecture; locale: us -->

> **English** · [中文](project-architecture_cn)

<a id="wiki-section-1"></a>
# Project architecture

This page is for people contributing to NekoJS itself, and for plugin authors who want to understand how it is put together. Script authors don't need it.

It starts with one example that touches every part, then covers each module.

<a id="wiki-section-2"></a>
## How a script runs

Take this code in `server_scripts/recipes.py`:

```python
from nekojs import ServerEvents

def add_recipes(event):
    event.shapeless('minecraft:dirt', ['minecraft:sand'])

ServerEvents.recipes(add_recipes)
```

From game start to a working recipe, it goes through these steps:

1. **Plugin discovery**: the loader entry finds every `@RegisterNekoJSPlugin` plugin, including the built-in `PythonTranspilerPlugin`.
2. **Plugin Runtime**: `NekoPluginBootstrap` collects plugin contributions one Extension Point at a time and produces an immutable `NekoPluginRuntime`. This is where `PythonTranspilerPlugin` registers `.py` with `PythonToJsCompiler` through `ScriptCompilersPoint`, and where the `ServerEvents` Event Group and all Bindings are collected.
3. **Runtime root**: `NekoRuntimeRoot` creates one `ScriptManager` per Script Type and owns the shared Graal `Engine`, the sandbox factory and the module cache.
4. **Compile**: `ScriptManager` finds `recipes.py` and hands it to `PythonToJsCompiler` by extension, which returns JavaScript plus a source map.
5. **Evaluate**: a sandboxed GraalJS Context runs the code. `ServerEvents.recipes` is an `EventBusJS`; calling it only registers the callback.
6. **Dispatch**: when the server loads recipes, `RecipeManagerMixin` posts `ServerEvents.recipes`. The callback receives a `RecipeEventJS`, and `event.shapeless` adds the recipe.

`/nekojs reload` repeats only steps 4 to 6: it builds a new script generation and swaps it in when it succeeds. The Plugin Runtime from step 2 is not rebuilt.

<a id="wiki-section-3"></a>
## Source layout

```text
NekoJS/
├── common/                  # Engine layer, no Minecraft or loader dependencies
│   └── src/main/java/com/tkisor/nekojs/
│       ├── api/             #   Public contracts: plugin interfaces, adapters, events, catalog
│       ├── core/            #   Graal Context, compilers, modules, Node shims, plugin bootstrap
│       ├── script/          #   Script lifecycle: ScriptManager, reload
│       ├── probe/           #   Type declaration generation
│       ├── eventbus/        #   Event bus implementation
│       ├── bindings/        #   Bindings with no Minecraft dependency
│       └── platform/        #   Platform abstraction (IPlatform)
├── common-api-processor/    # Annotation processor: compile-time spec coverage checks
├── src/                     # Version tree: one source copy shared by every Minecraft node
│   ├── main/java/           #   NeoForge and shared code
│   ├── main/resources*/     #   AT, mixins, interface injection (split into 26.x / 1.21.1)
│   └── fabric/              #   Fabric-only code and resources, not preprocessed by Stonecutter
├── versions/<node>/         # Per-node parameters and node-only sources
├── addons/nekujs/           # Separately shipped NeoForge 1.21.1 compatibility addon
└── buildSrc/                # Node build conventions
```

The build enforces two boundaries:

- `common` (including `com.tkisor.nekojs.api.*`) must not import Minecraft or loader classes. It may use Graal directly. Both `guardLint` and `:common:checkCommonIsolation` check this.
- Minecraft-facing code lives in the version tree. Prefer version facades (such as `McPlatformCompat` and `McClientCompat`) or node-only files for version differences, not inline version guards in business code.

For nodes, guards and build tasks, see [Build system](build-system_us).

<a id="wiki-section-4"></a>
## Startup order

Each loader entry (`NekoJSMod` on NeoForge, `NekoJSFabricMod` on Fabric) first discovers plugins, then calls the shared `NekoRuntimeAssembly`. It assembles things in a fixed order:

1. `NekoPluginRuntime.bootstrapOwned`: bootstrap the Plugin Runtime.
2. Fire the plugins' `init` hook.
3. Wire the loader's own event surface (NeoForge `IEventBus` or Fabric callbacks).
4. Build the engine context: compiler table, `SandboxConfig`, `ClassFilter`, error tracker, `NekoCoreContext` (with the shared `Engine`), `NekoSandboxFactory`.
5. Build the module pipeline and module cache, shared by the sandbox factory and the runtime root.
6. Construct `NekoRuntimeRoot`.
7. Create a `ScriptManager` for each auto-loaded Script Type and discover its scripts.
8. Run startup scripts, then fire the `initStartup` hook.

<a id="wiki-plugin-runtime"></a>
<a id="wiki-section-5"></a>
## Plugin Runtime

Built-in features and third-party plugins use the same extension mechanism. Here is the built-in `PythonTranspilerPlugin`, which registers `.py` as a script language:

```java
@RegisterNekoJSPlugin
public final class PythonTranspilerPlugin implements NekoJSPlugin, ScriptCompilersPoint.Contributor {

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
    }
}
```

`registerScriptCompilers` is a Plugin Hook on `NekoJSPlugin`. Behind it is the Extension Point `nekojs:script_compilers`, defined in `ScriptCompilersPoint`. A plugin can override the hook on `NekoJSPlugin` or implement `ScriptCompilersPoint.Contributor` explicitly. The same point collects both forms, so they behave the same.

<a id="wiki-section-6"></a>
### Plugin discovery

| Loader | How plugins are found |
|---|---|
| NeoForge | `NeoForgePluginLoader` scans FML annotation data for every `@RegisterNekoJSPlugin` class |
| Fabric | `FabricPluginLoader` registers a fixed list of built-in plugins, then reads `nekojs` entrypoints declared in `fabric.mod.json` |

Fabric has no classpath-wide annotation scan. When you add a built-in plugin without a platform guard, also add it to the built-in list in `FabricPluginLoader`, or it won't load on Fabric.

The discovered classes go to `NekoJSBasePluginManager`, which filters them by `clientOnly` and `requiredMods`, instantiates them, and sorts them by `priority`, highest first (default 1000).

<a id="wiki-section-7"></a>
### Bootstrap

`NekoPluginBootstrap` works one Extension Point at a time. For each point it creates an accumulator, lets every plugin contribute, runs the finisher to produce and freeze the result, then moves to the next point.

- **Built-in points come first.** `NekoBuiltinPointsPlugin` is the full list of built-in Extension Points. Bootstrap constructs it directly and runs it first, regardless of plugin priority. Adding a built-in Extension Point means one self-contained `XxxPoint` file plus one line in that list.
- **Points can depend on each other.** A later point can read an earlier point's result. When bootstrap freezes the point list it sorts the points by dependency; a cycle or a dependency on an unregistered point fails immediately.
- **Third parties can define their own points.** Implement `NekoPluginExtensionProvider`, register a `NekoPluginExtensionPoint` in `registerPluginExtensionPoints`, and read the result from the returned handle after bootstrap. See [Plugin development](plugin-development_us).

All results end up in `NekoPluginRuntime`. It is bootstrapped once per process and is read-only afterwards. Ordinary reload only switches script generations and never bootstraps the Plugin Runtime again, so plugin products (Bindings, adapters, Event Groups, compilers and so on) stay the same across reloads.

When you add a new collecting hook to `NekoJSPlugin`, you must add a paired Point. `PluginHookPairingTest` checks this.

<a id="wiki-section-8"></a>
## Script runtime and reload

`NekoRuntimeRoot` is the single owner of the runtime. Only the loader entry and the command, reload and shutdown boundaries hold it. It exposes lifecycle methods such as `reload`, `reloadFile`, `runTests`, `errors` and `close`. It has no global `get()` and should not be passed into ordinary business classes.

Resources fall into two lifetimes:

| Owner | Contents | Released when |
|---|---|---|
| Runtime root | Plugin Runtime, `ScriptManager`s, shared `Engine`, module cache | The game shuts down |
| Script generation | The GraalJS Context of one load, plus its listeners, timers and so on | A new generation replaces it, or its load fails |

A full reload is transactional. NekoJS builds a candidate generation and, once every phase passes, switches to it in one step on the owning thread. If any step fails, all of the candidate's resources are closed and the current generation stays in use. Startup scripts don't use this flow.

Core classes:

- **`NekoSharedEngine`**: the process-wide shared Graal `Engine`.
- **`NekoCoreContext`**: an immutable record holding the engine, `SandboxConfig`, `ClassFilter` and the error tracker.
- **`NekoSandboxFactory`**: configures `HostAccess`, class filtering, the restricted file system and JS options, and creates the Context. Script `console` output goes to the log for that Script Type.
- **`NekoSharedHostAccess`**: builds `HostAccess` and registers type conversions as `targetTypeMapping`s.
- **`ClassFilter`**: dangerous Java classes that scripts can't reach, such as `Runtime`, `Process`, `ClassLoader`, reflection, `java.io`, `java.nio` and `java.net`.

<a id="wiki-section-9"></a>
## Compilers and modules

Each script language is an `IScriptCompiler`, registered through the `nekojs:script_compilers` Extension Point and chosen by file extension.

| Frontend | Class | What it does |
|---|---|---|
| TypeScript | `NekoTypeScriptCompiler` | Erases type annotations, `type` / `interface`, generics and `as` / `satisfies`; turns `enum` and `namespace` into IIFEs. No type checking |
| JSX | `NekoJsxCompiler` | Supports the classic factory and the automatic mode that imports `nekojs/jsx-runtime` |
| Python | `PythonToJsCompiler` | Lexer → parser → emitter; turns a Python subset into JavaScript with a source map. Registered by `PythonTranspilerPlugin` |

Modules:

- **ESM**: `core/module/esm/` has its own lexer, parser, linker and evaluator.
- **Module invalidation**: `ModuleReloadCoordinator` invalidates the cache, links and dependency records of affected modules within the current generation. It is not a file watcher; saving a file triggers nothing.
- **`java:` modules**: `NekoModuleResolver` resolves `java:package/path` to a synthetic ESM module.
- **Node compatibility**: `core/node/` implements modules such as `fs`, `path`, `buffer`, `crypto`, `process`, `timers` and `os` in Java and JS. See [Node.js compatibility](nodejs-compatibility_us).

<a id="wiki-event-system"></a>
<a id="wiki-section-10"></a>
## Event system

Interfaces live in `api/event/`; implementations live in `eventbus/`.

- **`EventGroup`**: a named Event Group, such as `ServerEvents`. Declare events with `group.server("name", EventClass)`, `client(...)` or `startup(...)`.
- **`EventBus` / `CancellableEventBus` / `DispatchEventBus`**: the three bus kinds. The dispatch bus routes by key, such as an item or block id.
- **`EventBusJS`**: the script-facing `ProxyExecutable` that lets scripts write `ServerEvents.recipes(cb)`.
- **Loader bridges**: `EventBusForgeBridge` connects events on a NeoForge `IEventBus` to `EventBusJS`; Fabric uses `FabricEventBusBridge` for Fabric callbacks.

To add your own events, see [Event extensions](event-extensions_us).

<a id="wiki-section-11"></a>
## Recipe system

- **`RecipeTypeDefinition`**: the schema of a recipe type (namespace, type, fields, constructors).
- **`RecipeTypeDefinitionStorage`**: merges four layers of definitions, from lowest to highest priority: auto-discovered < plugin overrides (`registerRecipeSchemas`) < data pack JSON < script-registered (`event.registerSchema`).
- **`RecipeSchemaAutoDiscovery` + the per-version `MinecraftRecipeSchemaScanner`**: reflect on `RecipeSerializer.codec()` to generate schemas automatically.
- **`RecipeNamespaceProxy` / `SchemaRecipeBuilder` / `FallbackNamespaceProxy`**: the proxy layer behind `event.recipes.<ns>.<type>(...)` in scripts. It tries handler methods, then the schema, and falls back to raw JSON.
- **`RecipeManagerMixin`**: caches raw recipe JSON and applies script recipes.

<a id="wiki-section-12"></a>
## Probe

`ProbeCoordinator` runs one shared class collection and hands the result to each registered `ProbeBackend`, which renders its own declaration files. Backends register through `ProbeBackendRegistry`, configuration comes from `probe.toml` (`ProbeConfig` / `ProbeConfigLoader`), and all writes go through `ProbeOutputCommitter`. TypeScript and Python backends are built in. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-13"></a>
## Platform abstraction

The engine layer gets platform information through a few interfaces. They are not Java `ServiceLoader` services; the platform entry initializes them once at startup, for example `Platform.init(new NeoForgePlatform())`.

| Interface | Location | Purpose |
|---|---|---|
| `IPlatform` + `Platform` | `common/.../platform/` | Environment, versions, loaded mods, platform capabilities, default probe scan packages and more |
| `NekoIdCompat` | `common/.../platform/` | Converts between `NekoId` and the platform's native ID (`Identifier` / `ResourceLocation`) |
| `NekoCatalogPlatformProvider` | `common/.../api/catalog/` | Supplies registry literals, recipe namespace handlers and similar data to probe; one implementation per platform |

`IPlatform.capabilities()` returns a set of `PlatformCapability` values. There are currently 11: `TAGS`, `RESOURCE_PACKS`, `CLIENT_SCREENS`, `CLIENT_KEYBINDS`, `CLIENT_RENDERERS`, `RECIPE_HOT_RELOAD`, `RECIPE_SCHEMA_AWARE`, `NETWORK_CUSTOM_CHANNEL`, `NBT_BINARY_IO`, `RECIPE_VIEWER` and `CAPABILITIES`. Scripts read them through the global `Platform.capabilities()` as lowercase, hyphenated strings such as `"recipe-viewer"`. They exist only so scripts can check whether a feature is available. The engine itself trims features through `IPlatform` default methods (a platform that doesn't implement one returns empty or `null`), not through these flags.

Spec platform scope is a separate mechanism, declared with `@PlatformAvailability(Scope)`: `ALL`, `NF_ONLY` (26.x and 1.21.1), `NF26_ONLY`, and `CR_ONLY` (a legacy value that matches no current node). NeoForge nodes run `SpecCoverageProcessor` at compile time with `-Anekojs.platform=nf26|nf121`. Fabric nodes don't use this annotation processor; `platformGateTest` checks coverage instead.

<a id="wiki-section-14"></a>
## Adding interfaces to vanilla classes

| Node | Approach |
|---|---|
| NeoForge 26.x | `interfaceInjectionData` (`nekojs.interface_injection.json`) plus an empty `@Mixin` class that `implements XxxExtension` |
| NeoForge 1.21.1 | Mixin `implements` only; interface injection isn't available on this version |
| Fabric | Mixin `implements` only; mixin configs live in `src/fabric/resources` |

For example, `MixinBlock` is an empty abstract `@Mixin(Block.class)` class that only declares `implements BlockExtension`.

<a id="wiki-section-15"></a>
## Design principles

1. **Data first**: keep recipes and data in JSON where possible; scripts hold logic.
2. **Expose vanilla classes directly when you can**: avoid unnecessary wrappers.
3. **Don't leak Graal `Value`**: use concrete Java types at API boundaries; `Value` belongs only in the interop layer.
4. **The engine layer doesn't depend on Minecraft**: enforced by build-time checks.
5. **Plugins are the API**: built-in features and third-party plugins use the same Extension Points.

<a id="wiki-section-16"></a>
## Unfinished work

- The documentation annotations `@Doc`, `@Param`, `@Return`, `@Overload` and `@DeprecatedNekojs` are implemented; `@Example`, `@TypeOverride` and `@Since` are not. `@Since` needs a versioned public API first. See [Annotations](annotations_us).
- The compilers contain two large hand-written rewriters: the TypeScript eraser (`Eraser` in `NekoTypeScriptCompiler`) and `PythonEmitter`. Splitting them would only improve readability and wouldn't change any API.
- The public API has no SemVer freeze line yet.

<a id="wiki-section-17"></a>
## Next steps

- [Plugin development](plugin-development_us): write a NekoJS plugin mod.
- [Build system](build-system_us): nodes, guards, build tasks and CI.
- [Type adapters](type-adapters_us), [Event extensions](event-extensions_us), [Probe type generation](probe-type-generation_us), [Annotations](annotations_us).

<!-- wiki-nav -->

---

[Previous: Annotations](annotations_us) · [Contents](Home) · [Next: Probe type generation](probe-type-generation_us)
