<!-- wiki-page: plugin-development; locale: us -->

> **English** · [中文](plugin-development_cn)

<a id="wiki-section-1"></a>
# Plugin development

> This page explains how to write a **NekoJS plugin mod** (addon): a separate mod that adds bindings, events, adapters, recipe schemas, and other features to NekoJS.

<a id="wiki-section-2"></a>
## What is a plugin?

NekoJS follows a **plugins-as-API** extension model: built-in features (`NekoJSCorePlugin`) and third-party plugins implement the same `NekoJSPlugin` interface and use the same hooks.

A plugin consists of:
1. A class that implements `NekoJSPlugin`.
2. The `@RegisterNekoJSPlugin` annotation.
3. Implementations of the hooks you need. All default methods are empty, so override only what you use.

During bootstrap, NekoJS automatically discovers, instantiates, and sorts your plugin classes, then calls their hooks.

**These four requirements are enforced** (`NekoJSBasePluginManager.registerClass`; there are no other constraints): the annotation must be present (platform loaders scan only classes annotated with `@RegisterNekoJSPlugin`; without it, the class is never discovered and no message is logged), the class must `implements NekoJSPlugin`, it must be concrete (interfaces and abstract classes are rejected), and it must have a no-argument constructor. If any of the last three requirements is not met, the plugin is skipped and an `error` is logged, but the game still starts. The symptom is therefore a missing binding rather than a crash; search for the plugin class name first.

There are **no constraints** on package names, class names, source directories, or the number of plugin classes in one mod. A class name does not need to end in `Plugin`, and plugins do not have to share a particular package. Layouts such as `com.example.myaddon` in this page's examples are simply conventions for readability.

<a id="wiki-section-3"></a>
## Minimal plugin

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

@RegisterNekoJSPlugin(
    priority = 500,
    requiredMods = { "mymod" }    // Enable this plugin only when mymod is loaded
)
public class MyAddonPlugin implements NekoJSPlugin {

    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register("MyHelper", new MyHelperJS());
    }
}
```

```java
package com.example.myaddon;

public class MyHelperJS {
    public int add(int a, int b) { return a + b; }
}
```

Scripts can now call `MyHelper.add(1, 2)` and receive `3`.

> To add JSDoc descriptions to methods, use annotations such as `@Doc`, `@Param`, and `@Return`, or the programmatic `registerTypeDocs` hook described below. Annotations appear in probe-generated declarations; see [Annotations](annotations_us) for planned annotations that are not implemented yet.

<a id="wiki-section-4"></a>
## @RegisterNekoJSPlugin in detail

```java
@RegisterNekoJSPlugin(
    priority = 1000,                  // Load priority: larger values load first (default 1000; built-in CORE_PRIORITY = Integer.MAX_VALUE)
    clientOnly = false,               // Load on the client only
    requiredMods = { "mymod" }        // Load only when every listed mod is present (AND)
)
```

| Element | Default | Purpose |
|---|---|---|
| `priority` | `1000` | Load priority, in descending order. `CORE_PRIORITY` ensures built-in plugins load first. |
| `clientOnly` | `false` | Load in client processes only; skip on dedicated servers. |
| `requiredMods` | `{}` | Load only when every listed mod is present. |

> **Ordering contract**: plugins are sorted by descending priority. Equal priorities are resolved by the implementation class FQN, then lexicographically by owner id (`NekoJSBasePluginManager.ORDER`). Ordering is deterministic for a given classpath, but depends on your **class name** and changes if that name changes; it is not a contract you should rely on. To override a built-in implementation deterministically, use the relevant registry's explicit API, such as `ScriptCompilerRegistry.replaceLanguage`. Compiler lookup by extension uses the last registration, so a plugin with **lower priority** than the built-in plugin can override a built-in language through ordinary `register`. Duplicate discovery of the same plugin class for the same owner, for example through duplicate classpath entries, is deduplicated automatically: the class is registered only once.

<a id="wiki-section-5"></a>
## NekoJSPlugin hooks

> **Dual-form model (ADR-0010)**: each channel's **source of truth** is a self-contained extension point file (`XxxPoint` in `core.plugin`, containing its conflict policy, product freezing, and dependencies). `NekoJSPlugin` hooks are **facade projections** of those points. Overriding the base interface hook (recommended and simplest) and implementing `XxxPoint.Contributor` (the explicit form) are collected by the same point and are **equivalent**. Either form works; this page uses the base interface hooks throughout. (`PluginHookPairingTest` is an internal repository check: every new collection method on the base interface must have a corresponding Point or CI fails. This constraint applies when contributing to NekoJS itself, not when writing an addon.)

**Registration window**: registries are meaningful only during the bootstrap collection phase, while the hook is on the call stack. **Do not save a registry in a field for later use**: anything written after collection finishes will never be read. If a decision needs to be deferred, register an object that can evaluate it later while you are still inside the hook.

The consequence of registering too late depends on the registry:

- **`IllegalStateException`**: extension points that use `Sealable` (TypeDocs / Lifecycle / NodeModules / RecipeNamespaces / RecipeLifecycle / RecipeSchemas), plus `ScriptPropertyRegistry`, `ProbeBackendRegistry`, and `ScriptCompilerRegistry`. These fail immediately.
- **Silently ignored**: `BindingRegistry`, `JSTypeAdapterRegistry`, and `EventGroupRegistry` currently do not check whether registration has closed. Late registrations enter their collections but are never read. These failures are harder to diagnose: no error is reported, but your binding is missing.

All methods have empty default implementations. Override them as needed:

<a id="wiki-section-6"></a>
### Lifecycle

| Hook | Timing |
|---|---|
| `init()` | Plugin initialization |
| `initStartup()` | Startup-phase initialization |
| `afterInit()` | After all plugins have run init |
| `beforeScriptsLoaded(ScriptType)` | Before scripts of each type load |
| `afterScriptsLoaded(ScriptType)` | After scripts of each type load |
| `registerLifecycleHooks(PluginLifecycleRegister)` | Register the five lifecycle callbacks above programmatically. The default implementation registers `this::init` and the other four; normally, override the convenience callbacks directly. |
| `beforeRecipeLoading(...)` | Before recipes load; can modify the raw JSON map |
| `afterRecipes(...)` | After recipe data is committed to RecipeManager |

<a id="wiki-section-7"></a>
### Bindings and types

| Hook | Purpose |
|---|---|
| `registerBinding(BindingRegistry)` | Register global JS bindings; see below |
| `registerAdapters(JSTypeAdapterRegistry)` | Register JS-to-Java type adapters; see [Type adapters](type-adapters_us) |
| `registerTypeDocs(TypeDocsRegister)` | Register type documentation programmatically; `@Doc` / `@Param` / `@Return` annotations are also supported |
| `registerNodeTypeDocs(...)` | Register Node module type declarations |
| `registerNodeModules(...)` | Register Node-compatible modules |

<a id="wiki-section-8"></a>
### Events

| Hook | Purpose |
|---|---|
| `registerEvents(EventGroupRegistry)` | Register event groups; see [Event extensions](event-extensions_us) |
| `registerClientEvents(EventGroupRegistry)` | Register client event groups |

<a id="wiki-section-9"></a>
### Recipes

| Hook | Purpose |
|---|---|
| `registerRecipeNamespaces(RecipeNamespaceRegister)` | Register recipe namespaces and their handler classes |
| `registerRecipeSchemas(RecipeSchemaRegister)` | Register recipe schemas on the Java side |
| `registerRecipeLifecycleHooks(RecipeLifecycleRegister)` | Register recipe lifecycle hooks |

<a id="wiki-section-10"></a>
### Probe / workspace

| Hook | Purpose |
|---|---|
| `registerProbeBackends(ProbeBackendRegistry)` | Register probe backends by the `(languageId, name)` pair. The two built-in backends (TS `.d.ts` and Python `.pyi`, both named `builtin`) are registered through **this same hook** by common's `NekoProbeBuiltinPlugin`. Third parties can add a language or provide an alternative implementation under a different `name`. A duplicate `(language, name)` causes `lock()` to throw at the end of bootstrap, aborting startup. Only `languageId()` / `name()` / `render(ctx)` lack defaults; `priority()` / `requiresIr()` / `outputDir(...)` / `contributeEditorConfig(...)` all have defaults, and the default `generate(ctx)` implementation handles atomic commitment. See [Probe type generation: adding a custom backend](probe-type-generation_us) for a complete example. |
| `modifyWorkspaceConfig(JSConfigModel, env)` | Modify automatically generated jsconfig.json |
| `registerScriptCompilers(ScriptCompilerRegistry)` | Register custom script compilers for new language frontends |
| `registerScriptProperty(ScriptPropertyRegistry)` | Register custom script properties (`// key:`) |

<a id="wiki-section-11"></a>
### Attached data / resource generation

| Hook | Purpose |
|---|---|
| `attachServerData(...)` / `attachLevelData(...)` / `attachPlayerData(...)` | Attach `AttachedData` to Server/Level/Player |
| `registerApiSurface(ApiContributionRegistry)` | Contribute API surface entries |
| `generateData(DataGeneratorJS)` | Run before `ServerEvents.generateData`, sharing the generator with scripts |
| `generateAssets(DataGeneratorJS)` | Run before `ClientEvents.generateAssets` |
| `generateLang(LangGeneratorJS)` | Language-file generation hook |

<a id="wiki-section-12"></a>
### Custom channels (advanced)

Third parties can define their own collection extension points through the same V2 builder used by built-ins. See ADR-0001/0002 for the full semantics:

```java
public final class MyChannelPoint {
    public static final String ID = "myaddon:my_channel";
    public interface Contributor extends NekoJSPlugin {
        default void registerMyChannel(MyCollector collector) {}
    }
    public static final NekoPluginExtensionPoint<Contributor, MyCollector, MyProduct> POINT =
        NekoPluginExtensionPoint.<Contributor, MyCollector, MyProduct>builder(ID, Contributor.class)
            .merge(MergePolicy.append())
            .initializer(ctx -> new MyCollector())
            .collector(Contributor::registerMyChannel)
            .finish(MyCollector::snapshot)
            .build();
}
// Implement NekoPluginExtensionProvider in your plugin and call
// registry.register(MyChannelPoint.POINT) in registerPluginExtensionPoints;
// use the returned handle to obtain the product after bootstrap finishes.
```

The required parts are: `builder(id, pluginType)` must receive a non-empty id or it throws `IllegalArgumentException`; **all four** of `initializer` / `collector` / `merge` / `finish` must be called or `build()` throws `IllegalStateException`. `merge` deliberately has no default because conflict policy is central to the extension point's semantics, so you must choose explicitly (`append` / `firstWin` / `overrideWarn` / `failFast`). Registering an extension point after bootstrap freezes throws `IllegalStateException`, and registering the same id twice throws `IllegalArgumentException`.

Everything else is a convention: using `<your modid>:<channel name>` for `ID` makes ownership clear when a conflict occurs (uniqueness is required, that format is not). Nesting `Contributor` inside `XxxPoint`, calling the field `POINT`, and making `Contributor` extend `NekoJSPlugin` simply make your code familiar to readers of the built-in implementation.

<a id="wiki-section-13"></a>
## Register bindings (registerBinding)

`BindingRegistry` registers bindings by `ScriptType`. There are two forms:

<a id="wiki-section-14"></a>
### Simple bindings: `register(name, value)`

```java
@Override
public void registerBinding(BindingRegistry registry) {
    registry.register("MyHelper", new MyHelperJS());

    // Restrict the script type
    registry.register(ScriptType.CLIENT, "ClientHelper", new ClientHelperJS());

    // Bind a Java class directly so scripts can use it as a constructor
    registry.register("MyItemHelper", MyItemHelper.class);
}
```

> **The first binding with a given name wins**: `register(...)` returns `boolean`. When a name is already taken, it returns `false` and writes a warn message to the `nekojs.bootstrap` logger (`BindingRegistry.BindingRegistryImpl`); it neither throws nor replaces the binding. Built-in bindings are registered first by the `CORE_PRIORITY` plugin, so third parties cannot claim names such as `Item` or `Ingredient` through ordinary `register`. Check the return value to confirm registration succeeded. Binding names are not format-validated and do not need a namespace prefix, but they are global identifiers in scripts; including something specific to your mod helps avoid collisions.

<a id="wiki-section-15"></a>
### Delegating bindings: `DelegatingBinding` (helper + vanilla class)

If a binding needs both helper methods and a Minecraft class's static members, use `DelegatingBinding`; the `Item` binding uses this form:

```java
import com.tkisor.nekojs.js.DelegatingBinding;

@Override
public void registerBinding(BindingRegistry registry) {
    // Look up extensions (helper methods) first; delegate remaining static members to targetClass
    registry.register(Binding.of("MyBlock", new DelegatingBinding(
        new MyBlockHelperJS(),                            // Helper methods
        net.minecraft.world.level.block.Block.class,      // Minecraft class whose static members are delegated
        Set.of("of", "empty")                             // Extension method names supplied by the helper
    )));
}
```

> `DelegatingBinding` must wrap the original object with `asValue` during GraalJS access, while a Context is active, not during construction. Otherwise, the helper receives a detached Value and returns null everywhere. It is a `ProxyObject`, not a Java Class mirror, so `Java.type('...Item').of()` cannot access the extension methods.

<a id="wiki-section-16"></a>
## Binding interface

To customize binding behavior completely, implement `Binding`; `SimpleBinding` and `TypedBinding` are the built-in records:

```java
public interface Binding {
    String name();
    Object value();
    Class<?> valueType();          // Defaults to value.getClass(); proxies must declare this explicitly
    default void close(ScriptType) {}  // Cleanup hook on reload/close
}
```

`valueType()` is important for `ProxyObject` and bindings with dynamic members: preflight validation and probe use it to discover members and avoid false errors when reflection cannot see dynamic members.

<a id="wiki-section-17"></a>
## Type documentation

There are two ways to document binding methods and fields, which probe emits as `.d.ts` JSDoc: **annotations** (`@Doc` / `@Param` / `@Return` on wrapper methods and parameters, consumed by `TypeReflector` into the IR) and the **programmatic** `registerTypeDocs` hook:

```java
@Override
public void registerTypeDocs(TypeDocsRegister docs) {
    docs.register(new TypeDocCatalogEntry(...));          // Regular type documentation
    docs.registerManualDeclaration(new ManualDeclarationCatalogEntry(...));  // Manual wrapper/helper declarations
}
```

**`TypeDocsRegister` is the single source of truth**. Manual declaration registration for built-in wrappers/helpers is centralized in `NekoCommonManualDeclarations`.

<a id="wiki-section-18"></a>
## Cross-platform plugins

For plugins that support multiple platforms:
- **Abstract Minecraft types**: prefer common abstractions such as `NekoId` over `Identifier` / `ResourceLocation`.
- **Loader-specific code**: separate it from general code. NekoJS uses a single shared `src/` tree, with whole-file `//? if neoforge` / `//? if fabric` guards for loader differences and `versions/<node>/src/` for files whose differences are too large for practical guarding. Your plugin can use the same structure or separate sourceSets. Gate runtime behavior differences with `Platform.capabilities()`.
- **`requiredMods`**: use the annotation's `requiredMods` field to control platform-dependent loading.

<a id="wiki-section-19"></a>
## Gradle dependencies

Add this to the plugin mod's `build.gradle`:

```groovy
dependencies {
    compileOnly 'curse.maven:graal-1504336:8762962'   // Graal dependency (25.1.3.7, NeoForge build; Fabric build file id is 8762963)
    compileOnly files('libs/nekojs-<version>.jar')
}
```

> The actual compile dependency is currently the **platform fat jar**. Replace `<version>` in its filename with the target Release version; it includes all `:common` classes. NekoJS does not yet have a separately consumable stable SPI artifact, and the engine is not published to Maven, so the public API currently has no semantic-versioning guarantee.

<a id="wiki-section-20"></a>
## Test your plugin

1. Place your plugin mod in `mods/` together with NekoJS + Graal.
2. Start the game and search the log for your plugin class name to confirm discovery.
3. Call your registered bindings from a script.
4. Run `/nekojs probe` and check the `.d.ts` files for your binding declarations, including JSDoc contributed by `registerTypeDocs`.

<a id="wiki-section-21"></a>
## Complete example

See the built-in `src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java`: it registers all built-in bindings/adapters/events and is the best reference implementation.

<a id="wiki-section-22"></a>
## Next steps

- [Type adapters](type-adapters_us): `JSTypeAdapter`, allowing Java methods to accept multiple JS input forms automatically.
- [Event extensions](event-extensions_us): register custom event groups.
- [Annotations](annotations_us): `@Remap` / `@RemapByPrefix` / `@HideFromJS` / `@PlatformAvailability` and more.
- [Probe type generation](probe-type-generation_us): how your bindings enter `.d.ts` files.

<!-- wiki-nav -->

---

[Previous: Node.js compatibility](nodejs-compatibility_us) · [Contents](Home) · [Next: Type adapters](type-adapters_us)
