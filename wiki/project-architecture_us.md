<!-- wiki-page: project-architecture; locale: us -->

> **English** · [中文](project-architecture_cn)

<a id="wiki-section-1"></a>
# Project architecture

> This page is for developers **contributing to NekoJS, writing plugin mods, or exploring its internals**. Script authors do not need to read it.

<a id="wiki-section-2"></a>
## Overview

NekoJS is a multi-platform Minecraft scripting engine based on **GraalVM/GraalJS**.

```text
                  ┌─────────────────────────────────────────┐
                  │           Scripts (JS/TS/JSX)           │
                  └────────────────────┬────────────────────┘
                                       │ GraalJS Polyglot Context
                  ┌────────────────────▼────────────────────┐
                  │             NekoJS runtime              │
                  │  ┌──────────────────────────────────┐   │
                  │  │ core/   Frontends/modules/sandbox│   │
                  │  │ script/ Lifecycle/reload         │   │
                  │  │ api/    Public API/extension pts │   │
                  │  │ probe/  Type declaration output  │   │
                  │  │ plugin/ Plugin bootstrap         │   │
                  │  └──────────────────────────────────┘   │
                  └────────────────────┬────────────────────┘
                                       │ Platform abstraction SPI (IPlatform)
                  ┌────────────────────▼────────────────────┐
                  │          Platform implementations       │
                  │      NeoForge 26.x / 1.21.1 / Fabric     │
                  └─────────────────────────────────────────┘
```

<a id="wiki-section-3"></a>
## Module structure

The engine consists of two ordinary Gradle subprojects. Public contract types and engine implementations share `common`, separated by packages and lint boundaries. Minecraft-specific code lives in a Stonecutter-managed shared version tree.

```text
NekoJS/
├── common-api-processor/    # Annotation processor: compile-time Spec coverage checks
├── common/                  # Cross-platform engine, no Minecraft dependency; embedded in platform fat jars
│   └── src/main/java/com/tkisor/nekojs/
│       ├── api/             # Public contracts: plugin interfaces, adapters, events, catalog
│       │                    # No MC/Loader imports; Graal allowed (guardLint L1, ADR-0007)
│       ├── core/            # Runtime core: Graal Context, compilers, modules, Node shims
│       ├── script/          # Script lifecycle: ScriptManager, reload, workspace
│       ├── probe/           # Type declaration generation
│       ├── eventbus/        # Event bus implementations
│       ├── bindings/        # Minecraft-independent binding classes
│       └── platform/        # Platform abstraction SPI
├── buildSrc/                # Node build conventions: nekojs.neoforge-node / nekojs.fabric-node
├── src/                     # One shared version tree for all five nodes
│   ├── main/java/           # Version differences: //? if >=26 guards and replacements
│   │                        # Loader differences: whole-file //? if neoforge / //? if fabric guards
│   ├── main/resources*/     # AT / mixins / interface injection (26.x and 1.21.1 resource layers)
│   └── test/java/           # Shared tests
├── versions/                # Per-node gradle.properties and node-specific sources
│   ├── 1.21.1/  26.1.2/  26.2.0/     # NeoForge nodes
│   ├── 26.1.2-fabric/                # Fabric 26.1.2 node
│   └── 26.2.0-fabric/                # Fabric 26.2.0 node (currently reuses the source bridge)
└── settings.gradle.kts      # Stonecutter version graph and the two engine subprojects
```

Node-specific sources serve two purposes: version-specific compat implementations (facades under `platform/compat/`) and paired files for differences between eras that are too large to express conveniently with guards. The shared tree holds the 26.x implementation; `versions/1.21.1/src/` holds the same-named 1.21.1 implementation, and Stonecutter selects one for each node.

See [Build system](build-system_us).

<a id="wiki-section-4"></a>
## Three core subsystems

<a id="wiki-section-5"></a>
### 1. Runtime (`core/`)

Assembly and lifecycle of GraalJS Polyglot `Context`:

- **`NekoSharedEngine`**: a process-wide singleton Graal `Engine`, shared by all Contexts.
- **`NekoCoreContext`**: an immutable record containing engine + sandboxConfig + classFilter + errorTracker.
- **`NekoSandboxFactory`**: a ContextBuilder that creates one Context per ScriptType. It configures `HostAccess`, `ClassFilter`, IO restricted to the game directory, and JS options such as `js.ecmascript-version=latest` and `js.commonjs-require=true`.
- **`NekoSharedHostAccess`**: builds `HostAccess` and registers all `JSTypeAdapter` instances as `targetTypeMapping`.
- **`ClassFilter`**: `Predicate<String>`, blocking high-risk Java classes such as Runtime/Process/ClassLoader/reflection/io/nio/net/polyglot itself.

<a id="wiki-section-6"></a>
### 2. Language frontends and module system (`core/compiler/`, `core/module/`)

- **TypeScript**: `NekoTypeScriptCompiler` is a handwritten lexer-based **erasable TS** frontend. It removes type annotations, `type` / `interface`, generics, `as` / `satisfies`, and lowers `enum` / `namespace` to IIFEs.
- **JSX**: `NekoJsxCompiler` lowers JSX to `__nekoJsxFactory` calls using the classic runtime.
- **ESM**: `core/module/esm/` is a complete native ESM implementation with its own lexer/parser/linker. Its states progress through NEW, LINKING, LINKED, EVALUATING, and EVALUATED. It supports hot reload through `ModuleReloadCoordinator`.
- **Node shims**: `core/node/` supplies Java+JS implementations of fs/path/buffer/crypto/process/timers/util/events/assert/os/test.
- **`java:` modules**: `NekoModuleResolver` resolves `java:package/path` into synthetic ESM modules.

<a id="wiki-section-7"></a>
### 3. Plugins and extension points (`api/`, `core/plugin/`)

NekoJS follows a **plugins-as-API** extension model:

- **`NekoJSPlugin`** (`api/NekoJSPlugin.java`): an interface with approximately 20 default hooks. Built-in features (`NekoJSCorePlugin`) and third-party plugins implement the same interface.
- **`@RegisterNekoJSPlugin`**: marks plugin classes for annotation scanning by each loader (`NeoForgePluginLoader` / `FabricPluginLoader`).
- **`NekoJSBasePluginManager`**: discovery, filtering (`clientOnly` / `requiredMods`), instantiation, and priority sorting.
- **`NekoPluginBootstrap`**: coordinates extension points such as `nekojs:bindings`, `nekojs:adapters`, `nekojs:events`, and `nekojs:recipe_schemas`; freezes registries after collection and publishes `NekoPluginRuntime`.
- **`NekoPluginExtensionPoint` / `NekoPluginExtensionProvider`**: an open SPI through which plugins register custom extension points.

See [Plugin development](plugin-development_us).

<a id="wiki-section-8"></a>
## Platform abstraction (SPI)

This uses **explicit singleton initialization**, not Java ServiceLoader:

| SPI | Location | Purpose |
|---|---|---|
| `IPlatform` + `Platform` facade | `common/.../platform/` | Environment information: `isClient` / `getMcVersion` / `capabilities` / `defaultScanPackages()`, with the last supplying probe's package filters. The platform calls `Platform.init(new NeoForgePlatform())` once at startup. |
| `NekoIdCompat` | `common/.../platform/` | Convert between `NekoId` and native platform ids (`Identifier` / `ResourceLocation`). |
| `NekoCatalogPlatformProvider` | `common/.../api/catalog/` | Supply probe with registry literals, recipe namespace handlers, and host extension declarations. Each platform has an implementation. |
| `@RegisterNekoJSPlugin` discovery | Each platform loader | Scan annotated classes and pass them to common's plugin manager. |

`PlatformCapability` expresses platform capabilities as 10 feature flags: `TAGS`, `RESOURCE_PACKS`, `CLIENT_SCREENS`, `CLIENT_KEYBINDS`, `CLIENT_RENDERERS`, `RECIPE_HOT_RELOAD`, `RECIPE_SCHEMA_AWARE`, `NETWORK_CUSTOM_CHANNEL`, `NBT_BINARY_IO`, and `RECIPE_VIEWER`.

Its role matters: **this is an information interface for script feature detection**. Scripts read `Platform.get().capabilities()` to decide whether a feature is available; platform implementations must report capabilities accurately. Internal engine feature selection does not use these flags; it uses `IPlatform` default methods, which return empty results or null when a platform has not implemented them.

Spec platform scope is a separate mechanism, declared through `@PlatformAvailability(Scope)`: `ALL` / `NF_ONLY` (26.x and 1.21.1) / `NF26_ONLY` (26.x only) / `CR_ONLY`. `SpecCoverageProcessor` enforces it at compile time according to `-Anekojs.platform=nf26|nf121|cr`.

<a id="wiki-section-9"></a>
## Bytecode injection

The mechanism used to add behavior to vanilla classes differs by version:

| Platform | Mechanism |
|---|---|
| **NeoForge 26.x** | `interfaceInjectionData` (`nekojs.interface_injection.json`) + an empty `Mixin implements *Extension`. This is the cleanest form. |
| **NeoForge 1.21.1** | Mixin `implements` only; interface injection is unavailable on this version. |

<a id="wiki-section-10"></a>
## Event system (`api/event/` + `eventbus/`)

- **`EventGroup`**: a named collection of event buses, with factories such as `group.server("name", EventClass)`.
- **`EventBus` / `CancellableEventBus` / `DispatchEventBus`**: bus interfaces; dispatch buses route by key, for example a block/item id.
- **`EventBusJS`**: a GraalJS-facing `ProxyExecutable`, allowing calls such as `ServerEvents.recipes(cb)`.
- **`EventBusForgeBridge`**: bridges loader events such as NeoForge `IEventBus` into neutral buses.
- **`api/event/` holds interfaces; `eventbus/` holds implementations** (`EventBusImpl` / `CancellableEventBusImpl` / `dispatch/*`).

<a id="wiki-section-11"></a>
## Recipe system (`api/recipe/` + platform `wrapper/`, `bindings/recipe/`)

- **`RecipeTypeDefinition`**: schema definitions (namespace/type/fields/constructors/unique).
- **`RecipeTypeDefinitionStorage`**: merges three priority layers: auto-discovered < plugin override < data-driven JSON.
- **`RecipeSchemaAutoDiscovery` + version-specific `MinecraftRecipeSchemaScanner`**: reflect Minecraft `RecipeSerializer.codec()` to generate schemas automatically.
- **`RecipeNamespaceProxy` / `SchemaRecipeBuilder` / `FallbackNamespaceProxy`**: the proxy layer for script calls `event.recipes.<ns>.<type>(...)`. Resolution tries handler methods, then schemas, then raw JSON as a fallback.
- **`RecipeManagerMixin`**: caches original recipe JSON and applies scripted recipes.

<a id="wiki-section-12"></a>
## Probe (`probe/`)

`ProbeCoordinator` performs shared BFS class collection with configuration caching and dispatches the result to registered `ProbeBackend` implementations (`ProbeBackendRegistry` and `ProbeConfig` / `ProbeConfigLoader` for `probe.toml`). Each backend reflects Java classes and renders declarations. All disk output passes through `ProbeOutputCommitter` for in-place, per-file synchronization. TypeScript and Python backends are built in. See [Probe type generation](probe-type-generation_us).

<a id="wiki-section-13"></a>
## Design principles

1. **JSON first**: use JSON for recipes and data where possible; scripts should contain logic.
2. **Bindings as vanilla classes**: expose Minecraft classes directly when practical and avoid unnecessary wrappers.
3. **Avoid leaking Graal `Value`**: prefer concrete Java types at API boundaries and keep `Value` in the interop layer.
4. **No Minecraft dependency in the engine**: Minecraft code stays in the version tree, not `common`; build-time lint enforces this.
5. **Plugins as API**: built-in features and third-party plugins share the same extension interface.

<a id="wiki-section-14"></a>
## Unfinished work

- Documentation annotations: `@Doc` / `@Param` / `@Return` / `@Overload` / `@DeprecatedNekojs` are implemented and appear in probe-generated `.d.ts`. Overloads also have an adapter-driven automatic path; see [Annotations](annotations_us). `@Example` / `@TypeOverride` / `@Since` remain proposals, with `@Since` depending on an API version boundary that does not yet exist.
- Compiler readability debt: the TS type eraser (`Eraser` inside `NekoTypeScriptCompiler`, approximately 1.9k lines) and `PythonEmitter` (approximately 2.5k lines, the directory's largest file) are handwritten source-to-source rewriters. Splitting them would improve readability without changing their API.
- The public API has no frozen SemVer boundary yet.

<a id="wiki-section-15"></a>
## Next steps

- [Plugin development](plugin-development_us): write a NekoJS plugin mod.
- [Build system](build-system_us): compilation and releases.
- [Type adapters](type-adapters_us) / [Event extensions](event-extensions_us) / [Probe type generation](probe-type-generation_us) / [Annotations](annotations_us).

<!-- wiki-nav -->

---

[Previous: Annotations](annotations_us) · [Contents](Home) · [Next: Probe type generation](probe-type-generation_us)
