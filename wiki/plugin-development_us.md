<!-- wiki-page: plugin-development; locale: us -->

> **English** · [中文](plugin-development_cn)

<a id="wiki-section-1"></a>
# Plugin development

A NekoJS plugin is an ordinary Minecraft mod that adds features to scripts from Java: new global bindings, event groups, type adapters, recipe namespaces, editor declarations and more.

NekoJS's own built-in features use the same plugin interface, so a plugin can do anything the built-ins do.

<a id="wiki-section-2"></a>
## Your first plugin

This plugin adds a global binding `MyHelper` to every script.

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

@RegisterNekoJSPlugin
public class MyAddonPlugin implements NekoJSPlugin {
    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register("MyHelper", new MyHelperJS());
    }
}
```

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.annotation.Doc;

public class MyHelperJS {
    @Doc("Adds two integers.")
    public int add(int a, int b) {
        return a + b;
    }
}
```

Use it in a script:

```javascript
// server_scripts/test.js
console.info(MyHelper.add(1, 2)) // 3
```

To check it works:

1. Put your plugin mod, NekoJS and Graal in `mods/` and start the game.
2. Join a world and check that `logs/nekojs/server.log` prints `3`.
3. Run `/nekojs probe`. In VS Code, typing `MyHelper.` should show `add` with its description.

<a id="wiki-section-3"></a>
## Letting NekoJS find your plugin

The plugin class must implement `NekoJSPlugin`, be a concrete class (not an interface or abstract class), and have a public no-argument constructor. Discovery differs by loader:

| Loader | How to register |
|---|---|
| NeoForge | Annotate the class with `@RegisterNekoJSPlugin`; NekoJS scans all mods for it |
| Fabric | Declare a `nekojs` entrypoint in `fabric.mod.json`. The annotation alone is not discovered |

The Fabric entrypoint snippet (merge it into your `fabric.mod.json`):

```json
{
  "entrypoints": {
    "nekojs": ["com.example.myaddon.MyAddonPlugin"]
  }
}
```

To support both loaders, add both the annotation and the entrypoint. On Fabric, if the class also has the annotation, its `priority`, `requiredMods` and `clientOnly` are used; without it the priority is 1000.

There are no rules for package names, class names or how many plugin classes one mod has.

<a id="wiki-section-4"></a>
### @RegisterNekoJSPlugin parameters

```java
@RegisterNekoJSPlugin(
    priority = 1000,              // higher runs first; default 1000
    clientOnly = false,           // true: not loaded on a dedicated server
    requiredMods = { "create" }   // loaded only when all listed mods are present
)
```

| Parameter | Default | Effect |
|---|---|---|
| `priority` | `1000` | Plugin order; higher runs first. The built-in core plugin uses `NekoJSPlugin.CORE_PRIORITY` (`Integer.MAX_VALUE`) and always runs first |
| `clientOnly` | `false` | Load only on the client; skipped on a dedicated server |
| `requiredMods` | `{}` | Load only when every listed mod is present; useful for integration plugins |

Plugins with the same priority are ordered by fully qualified class name. The order is stable, but renaming the class changes it, so do not rely on it.

A skipped plugin (missing required mod or `clientOnly`) only logs at debug level. A plugin class that cannot be instantiated logs the error `Failed to instantiate plugin ...`.

<a id="wiki-section-5"></a>
## When hooks run

Every `NekoJSPlugin` method has an empty default, so override only what you need. There are two kinds of hooks:

- **Registration hooks** (`registerXxx`) are called once each while NekoJS initializes plugins at game startup, to record your contributions. Write to the registry you are given only while the hook runs. Do not keep the registry for later: later writes have no effect, and some registries throw `IllegalStateException`.
- **Callback hooks** (`init`, `afterScriptsLoaded` and so on) are called at the matching moment. Overriding them is enough.

A normal `/nekojs reload` does not call registration hooks again. Restart the game after changing a plugin.

<a id="wiki-section-6"></a>
## Hook reference

<a id="wiki-section-7"></a>
### Bindings, types and modules

| Hook | Purpose |
|---|---|
| `registerBinding(BindingRegistry)` | Register global bindings; see below |
| `registerAdapters(JSTypeAdapterRegistry)` | Register type adapters so Java parameters accept strings, objects and other JS inputs. See [Type adapters](type-adapters_us) |
| `registerTypeDocs(TypeDocsRegister)` | Add or replace editor declarations; see below |
| `registerNodeModules(NodeModuleRegister)` | Register JS modules loadable with `require('id')` |
| `registerNodeTypeDocs(TypeDocsRegister)` | Provide `declare module` declarations for those modules |

<a id="wiki-section-8"></a>
### Events

| Hook | Purpose |
|---|---|
| `registerEvents(EventGroupRegistry)` | Register event groups (server and common) |
| `registerClientEvents(EventGroupRegistry)` | Register event groups that exist only on the client |

See [Event extensions](event-extensions_us) for how to write them.

<a id="wiki-section-9"></a>
### Recipes

| Hook | Purpose |
|---|---|
| `registerRecipeNamespaces(RecipeNamespaceRegister)` | Register the Java implementation behind `event.recipes.<namespace>.<method>(...)` |
| `registerRecipeSchemas(RecipeSchemaRegister)` | Register or correct the field definitions of a recipe type |
| `registerRecipeLifecycleHooks(RecipeLifecycleRegister)` | Register callbacks around recipe loading; the default wires up `beforeRecipeLoading` and `afterRecipes` |

Recipe schema priority, highest first: `event.registerSchema` in scripts > data-driven > plugins > automatic scanning.

<a id="wiki-section-10"></a>
### Script languages, properties and workspace

| Hook | Purpose |
|---|---|
| `registerScriptCompilers(ScriptCompilerRegistry)` | Register a new script language or file extension |
| `registerScriptProperty(ScriptPropertyRegistry)` | Register a new script property (`// key: value`); see [Script properties](script-properties_us) |
| `registerProbeBackends(ProbeBackendRegistry)` | Register a new declaration generator; see [Probe type generation](probe-type-generation_us) |
| `modifyWorkspaceConfig(JSConfigModel, String)` | Edit each script folder's `jsconfig.json` before it is written |
| `registerApiSurface(ApiContributionRegistry)` | Provide API surface metadata |

<a id="wiki-section-11"></a>
### Lifecycle callbacks

| Method | When it is called |
|---|---|
| `init()` | After plugins are initialized, before startup scripts load |
| `initStartup()` | After startup scripts load |
| `afterInit()` | After all mods finish initializing (`FMLLoadCompleteEvent` on NeoForge) |
| `beforeScriptsLoaded(ScriptType)` | Before a full load or full reload of one script type; single-file reloads do not trigger it |
| `afterScriptsLoaded(ScriptType)` | After a full load or full reload of one script type; single-file reloads do not trigger it |
| `beforeRecipeLoading(RecipeLifecycleContext)` | Before recipes load; you can edit the raw recipe JSON |
| `afterRecipes(RecipeLifecycleContext)` | After all recipes have loaded |

If you override `registerLifecycleHooks` or `registerRecipeLifecycleHooks` without calling the default implementation, the matching convenience callbacks are no longer called.

<a id="wiki-section-12"></a>
### Data and resource generation

| Method | Purpose |
|---|---|
| `attachServerData` / `attachLevelData` / `attachPlayerData` | Attach in-memory custom data to the server, a level or a player; it is not saved |
| `generateData(DataGeneratorJS)` | Generate data pack content. Runs before the script `ServerEvents.generateData` and shares its generator |
| `generateAssets(DataGeneratorJS)` | Generate resource pack content. Runs before the script `ClientEvents.generateAssets` |
| `generateLang(LangGeneratorJS)` | Generate language file entries |
| `generatedLangs()` | Declare which languages `generateLang` is called for; default `en_us` |

`generateLang` is called once for **every** language in the current round, including languages requested by other plugins and scripts. If you only generate one language, check `generator.getLang()` first:

```java
@Override
public void generateLang(LangGeneratorJS generator) {
    if (!"zh_cn".equals(generator.getLang())) return;
    // add zh_cn entries here
}
```

<a id="wiki-section-13"></a>
## Registering global bindings

`registerBinding` is called once per script type, and each call gets the registry for that type.

```java
@Override
public void registerBinding(BindingRegistry registry) {
    // available to every script type
    registry.register("MyHelper", new MyHelperJS());

    // only available to client scripts
    registry.register(ScriptType.CLIENT, "MyClientHelper", new MyClientHelperJS());

    // bind a Java class: scripts can use its static members and call new
    registry.register("MyMath", MyMath.class);
}
```

- The value can be an instance (scripts call its public methods) or a `Class` (scripts use its static members).
- **The first binding with a name wins.** Later ones are ignored, `register` returns `false`, and the log shows a `NEKO-4001` warning. Built-in plugins have the highest priority, so you cannot replace built-in bindings such as `Item` or `Ingredient` by reusing the name.
- Names have no required format, but they are global variables in scripts. Include something specific to your mod to avoid clashes.
- `CLIENT` bindings are not collected on a dedicated server.

<a id="wiki-section-14"></a>
### Exporting as an API module

Instead of a global variable, a binding can be exported only as a module that scripts `import`:

```java
registry.register(Binding.of("MyHelper", new MyHelperJS()), exposure -> exposure.versioned());
```

```javascript
import { MyHelper } from 'nekojs/api/version/myaddon'
```

- `myaddon` in the module path is the mod ID that owns the plugin, as reported by the loader.
- `versioned()` exports to `nekojs/api/version/<modid>`; `stable()` exports to `nekojs/api/stable/<modid>`. Choose exactly one.
- `stable()` rejects public types that expose Minecraft or loader classes. A class that must expose native types needs `@ScriptNativeBridge` to pass.
- This form does **not** also create a global variable.
- A missing mod owner, an invalid export name or a duplicate export fails with `NEKO-4057`.

For how scripts use modules, see [Module system](module-system_us#wiki-api-modules).

<a id="wiki-section-15"></a>
### The binding's value type

`Binding.of(name, value)` infers the type from the value. When the value is a proxy (such as `ProxyObject`), Java reflection cannot see its members, so the pre-load member check and declaration generation get it wrong. Use the three-argument form to name the class that really has those members:

```java
registry.register(Binding.of("MyBlock", new DelegatingBinding(
        new MyBlockHelperJS(),                          // provides of / empty
        net.minecraft.world.level.block.Block.class,    // other static members go here
        Set.of("of", "empty")
), MyBlockHelperJS.class));
```

`DelegatingBinding` looks up the listed members on the helper first and sends everything else to the target class's static members. The built-in `Item` binding works this way. It is only a proxy: the native class from `Java.type(...)` does not have these extra methods.

`Binding.close(ScriptType)` is called when that script type's runtime is reset or shut down, not on every reload.

<a id="wiki-section-16"></a>
## Completion and documentation

There are two ways to give your bindings hints and descriptions in the editor:

1. **Annotations**: put `@Doc`, `@Param`, `@Return` and others on classes and methods. See [Annotations](annotations_us).
2. **`registerTypeDocs`**: register them in code, for cases annotations cannot express.

Describe a global binding, or set the type it has in declarations:

```java
@Override
public void registerTypeDocs(TypeDocsRegister docs) {
    docs.register(TypeDocCatalogEntry.binding(
            "MyHelper",            // binding name
            null,                  // type override; null uses the reflected type
            "Math helpers for my addon.",
            List.of("MyHelper.add(1, 2)")));
}
```

`TypeDocsRegister` has some more advanced methods:

| Method | Use |
|---|---|
| `register(TypeDocCatalogEntry)` | Description, examples and type override of a global binding |
| `registerManualDeclaration(ManualDeclarationCatalogEntry)` | A hand-written global TypeScript declaration |
| `registerClassDeclaration(ClassDeclarationCatalogEntry)` | Replace the TypeScript declaration generated for a Java class |
| `registerBeanAliasExclusion(...)` / `registerBeanMethodProjection(...)` | Adjust how Bean properties appear in declarations |

When a hand-written declaration uses Java types, name them with `withReferences`:

```java
docs.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
        "myaddon:stack", "type MyStack = StackValue;", "A script-visible item stack.", List.of())
        .withReferences(Map.of("StackValue",
                ManualDeclarationCatalogEntry.TypeReference.host(ItemStack.class))));
```

`TypeReference.host(...)` refers to the Java type itself; `TypeReference.input(...)` refers to its input alias (every input form its adapters accept).

To replace a class declaration, you can write generic relationships that Java reflection cannot express:

```java
docs.registerClassDeclaration(ClassDeclarationCatalogEntry.of(
        RpcBuilder.class,
        """
        export class $RpcBuilder<T extends Record<string, 'string' | 'int'> = {}> {
            schema<const S extends Record<string, 'string' | 'int'>>(value: S): $RpcBuilder<S>;
            fn(callback: (args: { [K in keyof T]: T[K] extends 'string' ? string : number }) => void): void;
        }
        """));
```

When writing a class declaration:

- Write only the class body, without `declare module`. Keep the generator's class name format `$JavaName` (nested classes are `$Outer$Inner`).
- Do not redefine input aliases such as `$JavaName_`; the generator provides them.
- If the declaration uses other Java types, list them with the `of` overload that takes a `Set<Class<?>>`. A hidden or ungeneratable dependency fails with `NEKO-4033`.
- When one class has several replacements, the highest priority wins. Different definitions at the same priority fail with `NEKO-4032`.
- Replacements only change TypeScript declarations. The declaration must match the real Java behavior; NekoJS does not check this.

The built-in hand-written declarations live in `NekoCommonManualDeclarations` and make good references.

<a id="wiki-section-17"></a>
## Custom extension points

If your plugin should let **other plugins** register things with it (for example, you provide a machine framework and other mods register machines), define your own extension point. It uses the same mechanism as NekoJS's built-in hooks.

```java
public final class MachinesPoint {
    public static final String ID = "myaddon:machines";

    // other plugins implement this interface to contribute
    public interface Contributor extends NekoJSPlugin {
        default void registerMachines(MachineCollector collector) {}
    }

    public static final NekoPluginExtensionPoint<Contributor, MachineCollector, List<Machine>> POINT =
            NekoPluginExtensionPoint.<Contributor, MachineCollector, List<Machine>>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(ctx -> new MachineCollector())
                    .collector(Contributor::registerMachines)
                    .finish(MachineCollector::snapshot)
                    .build();
}
```

The plugin that defines the point also implements `NekoPluginExtensionProvider` and registers it in `registerPluginExtensionPoints`:

```java
@RegisterNekoJSPlugin
public class MyAddonPlugin implements NekoPluginExtensionProvider {
    private static NekoPluginExtensionHandle<List<Machine>> machines;

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        machines = registry.register(MachinesPoint.POINT);
    }

    public static List<Machine> machines() {
        return machines.result();   // available after plugin bootstrap
    }
}
```

Rules:

- `initializer`, `collector`, `merge` and `finish` are all required; if one is missing, `build()` throws `IllegalStateException`.
- `merge` has no default. Choose a conflict policy: `append()`, `firstWin()`, `overrideWarn()` or `failFast()`.
- The ID must be globally unique, including against the built-in `nekojs:*` IDs; a duplicate throws `IllegalArgumentException`. Use `<your modid>:<name>`.
- Extension points can only be registered inside `registerPluginExtensionPoints`; registering later throws `IllegalStateException`.
- If you need another point's result, declare it with `dependsOn(...)`.
- `MachineCollector` and `Machine` are your own types, not NekoJS API.

Each built-in hook corresponds to an `XxxPoint` in the `com.tkisor.nekojs.core.plugin` package. Overriding the `NekoJSPlugin` hook and implementing the matching `XxxPoint.Contributor` interface have exactly the same effect.

<a id="wiki-section-18"></a>
## Cross-platform plugins

- Prefer NekoJS's platform-neutral types, for example `NekoId` instead of Minecraft's `Identifier` / `ResourceLocation`.
- Check runtime feature differences with `Platform.capabilities()` instead of hard-coding loader names.
- Use `requiredMods` or `clientOnly` to control loading of plugins that only apply to some platforms.
- To implement the same set of methods in several platforms' Extension interfaces, you can use the Spec interface pattern; see [Annotations](annotations_us).

<a id="wiki-section-19"></a>
## Gradle dependencies

NekoJS does not publish Maven artifacts yet, so plugins depend on the matching NekoJS release jar directly. Both loaders require GraalMC 25.5.0 or later. Use Modrinth Maven for NeoForge; Fabric can use CurseMaven:

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter { includeGroup("maven.modrinth") }
    }
    // Used only by the Fabric alternative below.
    maven {
        url = "https://cursemaven.com"
        content { includeGroup("curse.maven") }
    }
}

dependencies {
    // NeoForge: GraalMC 25.5.0.
    compileOnly "maven.modrinth:bjcrgPcA:O8qtoV6d"
    // Fabric: replace the NeoForge dependency with this CurseForge file.
    // compileOnly "curse.maven:graal-1504336:9555615"
    // The NekoJS release jar for your Minecraft version and loader.
    compileOnly files("libs/nekojs-<version>.jar")
}
```

NekoJS's public API has no semantic versioning guarantee yet. Recompile and test your plugin after upgrading NekoJS.

<a id="wiki-section-20"></a>
## Reference: built-in plugins

The built-in features are spread over several plugins that make good references:

- `common/.../core/plugin/NekoCommonBuiltinPlugin.java`: platform-neutral bindings, compilers, script properties and type docs.
- `src/main/java/.../core/NekoJSCorePlugin.java`: Minecraft-facing bindings, adapters and events.
- `NekoBuiltinPointsPlugin`: registers all built-in extension points.
- Fabric also has `FabricCorePlugin`.

Next steps:

- [Type adapters](type-adapters_us): let Java methods accept several JS input forms.
- [Event extensions](event-extensions_us): register custom event groups.
- [Annotations](annotations_us): control member names, visibility and editor descriptions.
- [Probe type generation](probe-type-generation_us): how bindings end up in `.d.ts`.

<!-- wiki-nav -->

---

[Previous: Node.js compatibility](nodejs-compatibility_us) · [Contents](Home) · [Next: Type adapters](type-adapters_us)
