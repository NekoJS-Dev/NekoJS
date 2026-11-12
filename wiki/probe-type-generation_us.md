<!-- wiki-page: probe-type-generation; locale: us -->

> **English** · [中文](probe-type-generation_cn)

<a id="wiki-section-1"></a>
# Probe type generation

Probe is NekoJS's built-in type declaration generator, the equivalent of ProbeJS. It reads the Java classes, events, bindings and registries loaded in your current game. From them it generates TypeScript `.d.ts` files and Python `.pyi` stubs, and sets up your editor. VS Code can then complete event names, methods and item IDs.

The first half of this page is for script authors: commands, configuration, editor setup and common problems. The second half is for plugin authors and contributors: the pipeline, data sources and custom backends.

<a id="wiki-section-2"></a>
## Quick start

1. Enter a world and run `/nekojs probe`.
2. Open a script folder in VS Code, for example `nekojs/server_scripts`.
3. Open a `.js` or `.ts` file and type `ServerEvents.`. A completion list should appear.

The generated files are in `.neko_probe/` in the game directory, next to `nekojs/`. Do not edit them; the next run overwrites them.

After adding mods or registering new items, run `/nekojs probe` again. Declarations reflect the game at the moment you ran the command.

For Python scripts, use `/nekojs probe python` instead. See "Python" below.

<a id="wiki-section-3"></a>
## Commands

All probe commands need operator permission (in single player, enable cheats). Language and backend names complete with TAB.

| Command | What it does |
|---|---|
| `/nekojs probe` | Generates TypeScript declarations. This is the one you normally need |
| `/nekojs probe python` | Generates Python declarations |
| `/nekojs probe all` | Runs every registered backend, including ones from plugins |
| `/nekojs probe <language>` | Runs the default backend for that language |
| `/nekojs probe <language> <name>` | Runs one backend, for example `/nekojs probe typescript builtin` |
| `/nekojs probe list` | Lists registered backends |
| `/nekojs probe reload` | Rereads `probe.toml`; no restart needed |
| `/nekojs probe enable` / `disable` | Turns probe on or off and saves it to `probe.toml` |
| `/nekojs probe reset_config` | Restores the default editor configuration and regenerates |

To generate every time the server starts, set `runAtStartup = true` in `probe.toml`. A failure is only logged; the server still starts.

<a id="wiki-section-4"></a>
## Editor setup

<a id="wiki-section-5"></a>
### TypeScript and JavaScript

Probe maintains the `jsconfig.json` in each script folder for you. It:

- maps module names like `java:*`, `@side-only/...` and `@special` to `.neko_probe/typescript/`;
- shows each script type only what it can use, so `server_scripts` does not see client events;
- keeps your own settings. Extra `include`, `typeRoots` or other options you add are not overwritten.

Completion is most accurate when you open **one script folder** in VS Code, such as `nekojs/server_scripts`. Opening the whole game directory also works, but declarations for different script types get mixed.

Add `// @ts-check` at the top of a `.js` file to get type errors in VS Code. NekoJS does not type-check at runtime; this only affects the editor.

<a id="wiki-section-6"></a>
### Python

1. Run `/nekojs probe python`. Plain `/nekojs probe` does not generate Python declarations.
2. Put this at the top of every `.py` file:

```python
from nekojs import *
```

This line is only for the editor. NekoJS removes it when it runs the script.

3. Install the Pylance extension in VS Code.
4. Open the game directory, `nekojs/` or a script folder directly in VS Code, not a folder above them. Pylance reads configuration only from the workspace root.

Probe writes `pyrightconfig.json` and `.vscode/settings.json` in those folders to add `.neko_probe/python` to the search path. If you have not set `python.languageServer` yourself, it is set to `"Pylance"`.

Python declarations are coarser than TypeScript: generics become `Any` and complex object shapes become `dict[str, Any]`. They are good enough for everyday completion. See [Python scripts](python-scripts_us).

<a id="wiki-section-7"></a>
## Using generated types in scripts

Most of the time you import nothing: global bindings and event parameters already have types. To type your own functions, import them:

```typescript
import type { $ItemStack, $ItemStack_ } from 'java:net/minecraft/world/item'
import type { RegistryTypes } from '@special/types'

function describe(stack: $ItemStack): string {
  return stack.getHoverName().getString()
}

const id: RegistryTypes.Item = 'minecraft:diamond'
```

Naming rules:

- `$ItemStack`: the declaration of a Java class. The `$` prefix avoids clashes with JavaScript names.
- `$ItemStack_`: what the type **accepts as input**. For example, a string like `'minecraft:stone'` can be passed as an item stack. Only types NekoJS can actually convert get this alias.
- `RegistryTypes.Item`: literals for all item IDs. `RegistryTypes.ItemTag` holds item tags and `RegistryTypes.Namespace` all namespaces.
- For `java:` imports, see [Module system](module-system_us).

`import type` only matters in the editor and is removed at runtime.

<a id="wiki-section-8"></a>
## probe.toml

The configuration file is `nekojs/config/probe.toml`. It is created with comments on first use. After editing, run `/nekojs probe reload` and generate again.

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Master switch. When off, probe fails immediately |
| `runAtStartup` | `false` | Generates TypeScript declarations once after the server starts |
| `baseDir` | `".neko_probe"` | Output folder, relative to the game directory |
| `scan.mode` | `"SMART"` | `SMART`: scan only allowlisted packages. `FULL`: ignore the allowlist; only exclusions and depth apply. `NONE`: no scanning; probe fails |
| `scan.extraIncludePackages` | `[]` | **Adds** packages to the default allowlist |
| `scan.includePackages` | `[]` | When not empty, **replaces** the whole default allowlist. Usually use `extraIncludePackages` |
| `scan.excludePackages` | Built-in list | Packages never scanned, in every mode |
| `scan.forceScanMods` | `["minecraft"]` | Mod IDs or packages always scanned |
| `scan.maxDepth` | `5` | How many type references to follow from events and bindings |
| `languages.<language>.outputDir` | The language name | Subfolder of `baseDir` for that language |
| `languages.<language>.backend` | Not set | Default backend for `/nekojs probe <language>` |

For example, to get completion for a mod's classes:

```toml
[scan]
extraIncludePackages = ["com.example.mymod", "com.mojang"]
excludePackages = ["com.example.mymod.debug"]
maxDepth = 6
```

About package rules:

- The default allowlist is `java`, `com.tkisor.nekojs` and the Minecraft and loader packages of the current platform.
- Each entry is a package prefix (`com.example.mymod`) or a regex starting with `re:` that must match the full class name, such as `re:com\.example\.(mod1|mod2)\..*`. An invalid regex is warned about once and then ignored.
- `forceScanMods` understands the mod IDs `minecraft`, `neoforge`, `forge` and `java`. For anything else, write a package or a `re:` regex.
- The default exclusions include `com.tkisor.nekojs.core`, `java.awt`, `javax.swing`, `java.sql`, `org.graalvm` and other packages scripts cannot use anyway. `excludePackages = []` removes these exclusions, but scripts still cannot use those classes.

A wider scan means slower generation and more files.

<a id="wiki-section-9"></a>
## Common problems

<a id="wiki-section-10"></a>
### No completion

1. Make sure you ran `/nekojs probe` and `.neko_probe/typescript/` exists in the game directory.
2. Open a script folder in VS Code, not a folder above the game directory.
3. If you broke `jsconfig.json`, run `/nekojs probe reset_config`.
4. For Python, follow the four steps under "Python" above.

<a id="wiki-section-11"></a>
### Completion is outdated or a mod's classes are missing

Declarations do not update by themselves. After adding mods or changing registration scripts, run `/nekojs probe` again.

If classes are still missing, the mod's packages may be outside the scan. Add them to `scan.extraIncludePackages` or raise `scan.maxDepth`.

<a id="wiki-section-12"></a>
### NEKO-4048 in the log

`NEKO-4048` means some declarations refer to types that could not be generated, for example because they are excluded, hidden or unreadable. The missing ones are listed in `.neko_probe/typescript/@special/dependencies.json`. The declarations were still written; a few types may show as not found in the editor. Check whether you excluded a package you need.

<a id="wiki-section-13"></a>
### Methods written as `vector["x()"]()`

When a Java class has both a field `x` and a method `x()`, `vector.x` reads the field. Call the method with Graal's signature syntax: `vector["x()"]()`. For overloads, include the parameter types, as in `matrix["m00(double)"](0.5)`.

<a id="wiki-section-14"></a>
### A property or method is missing from the declarations

A few same-named members clash in TypeScript, so the declarations pick one. Both forms work at runtime; only the editor hints differ:

- `entity.attackable` is not declared; use `entity.isAttackable()`. The `livingEntity.attackable()` method is still available.
- `section.x()`, `y()` and `z()` on `SectionPos` are not declared; use `section.x` or `section.getX()`.

<a id="wiki-section-15"></a>
### A recipe method shows never

A recipe member typed as `never` means NekoJS explicitly rejected that recipe type. It is not a declaration that failed to generate. Check the recipe diagnostics in `/nekojs error`, and see [Recipe system](recipe-system_us).

<a id="wiki-section-16"></a>
## Adjusting declarations from scripts: ProbeEvents

When generated declarations are wrong or incomplete, fix them with `ProbeEvents` in `server_scripts` instead of editing generated files. These events fire only when `/nekojs probe` runs. Register the listeners at the top level of a script.

| Event | What it does |
|---|---|
| `ProbeEvents.modifyType` | Edits one class: rename, retype, hide, document, add methods |
| `ProbeEvents.assignType` | Replaces a Java type with another type in every declaration |
| `ProbeEvents.addGlobal` | Declares extra global variables |
| `ProbeEvents.snippets` | Adds VS Code snippets |

Changes apply to both TypeScript and Python declarations (snippets are TypeScript only).

Wherever a type is expected you can pass a string. A string containing `.` is a Java class name, such as `"net.minecraft.world.item.ItemStack"`. Anything else is a basic type, such as `"string"`, `"number"` or `"boolean"`.

<a id="wiki-section-17"></a>
### modifyType

```javascript
// server_scripts/probe_patch.js
ProbeEvents.modifyType(event => {
  const editor = event.forClass('net.minecraft.world.entity.player.Player')
  if (!editor) return

  editor
    .setDoc('A player')
    .setMethodDoc('getName', 'Returns the player name')
    .renameParam('addItem', 'arg0', 'stack')
    .changeReturnType('getInventory', 'net.minecraft.world.entity.player.Inventory')
    .changeParamType('addItem', 0, event.union('string', 'net.minecraft.world.item.ItemStack'))
})
```

- `event.forClass(name)` returns an editor for that class. It returns `null` when the class was not scanned this run, so check for it. `event.hasClass(name)` checks first.
- `event.type(...)`, `event.array(type)` and `event.union(a, b, ...)` build complex types.
- Methods that work by name affect **every overload** with that name. A missing member is silently ignored.

Editor methods:

| Method | What it does |
|---|---|
| `hide()` | Hides the whole class |
| `setDoc(doc)` | Sets the class documentation |
| `renameClass(newName)` | Renames the class |
| `changeSuper(type)` | Changes the superclass |
| `hasMethod(name)` | Whether the method exists (constructors included) |
| `renameMethod(name, newName)` | Renames a method |
| `hideMethod(name)` | Hides a method |
| `setMethodDoc(name, doc)` | Sets method documentation |
| `changeReturnType(name, type)` | Changes the return type |
| `changeParamType(name, index, type)` | Changes a parameter type by position |
| `changeParamType(name, paramName, type)` | Changes a parameter type by name |
| `renameParam(name, paramName, newName)` | Renames a parameter |
| `removeParam(name, index)` | Removes a parameter |
| `markOptional(name, paramName)` | Marks a parameter optional |
| `addParam(name, paramName, type)` | Appends a parameter |
| `addMethod(name, returnType, ...params)` | Adds an instance method. Each parameter is `"type"` or `"name:type"` |
| `addStaticMethod(name, returnType, ...params)` | Adds a static method |
| `hasField(name)` | Whether the field exists |
| `renameField(name, newName)` | Renames a field |
| `hideField(name)` | Hides a field |
| `changeFieldType(name, type)` | Changes a field type |
| `setFieldDoc(name, doc)` | Sets field documentation |

<a id="wiki-section-18"></a>
### assignType

```javascript
ProbeEvents.assignType(event => {
  // Show CompoundTag as MyNbt in every declaration
  event.assign('net.minecraft.nbt.CompoundTag', 'MyNbt')
})
```

`assignType` runs before `modifyType`. If `modifyType` sets the same slot again, `modifyType` wins.

<a id="wiki-section-19"></a>
### addGlobal and snippets

```javascript
ProbeEvents.addGlobal(event => {
  event.add('MyFlag', 'boolean')            // declare const MyFlag: boolean
  event.add('Helper', 'com.example.Helper') // refers to a Java class
})

ProbeEvents.snippets(event => {
  event.add('nekojs-listener', 'listen', 'ServerEvents.started(e => { $1 })', 'Listen for server start')
})
```

`addGlobal` only affects declarations. You still have to create the variable, for example by registering a binding from a plugin. Snippets go to `nekojs/.vscode/nekojs.code-snippets`; snippets you wrote yourself are kept.

<a id="wiki-section-20"></a>
## Pipeline

From here on, the page is for plugin authors and contributors.

<a id="wiki-section-21"></a>
### What one probe run does

`/nekojs probe` runs on the server thread, driven by `ProbeCoordinator`. Only one run at a time is allowed; a second one returns `probe already running`.

```text
1. Collect classes (ProbeClassCollector)
   Seeds: event types, binding Java types, recipe input types, declaration dependencies
   Filtered by the scan config; breadth-first along type references up to scan.maxDepth

2. Build the IR (ProbeIrBuilder, on demand)
   Built when a backend declares requiresIr() or modifyType / assignType has listeners
   TypeReflector reflects each class in parallel into List<TypeDecl>
   Applies ProbeEvents.assignType, then fires ProbeEvents.modifyType

3. Fire ProbeEvents.addGlobal / ProbeEvents.snippets (when they have listeners)

4. Write the base workspace config (WorkspaceGenerator)

5. Run each selected backend in turn
   render() produces "relative path -> text" in memory
   ProbeOutputCommitter syncs each file into the output folder:
   unchanged files are skipped, changed ones overwritten, ones no longer produced deleted

6. Each backend contributes editor config (jsconfig / pyrightconfig / settings.json / snippets)
```

Keep in mind:

- Both built-in backends require the IR, so in practice it is built on every run.
- If `render` throws, that backend's output folder stays unchanged. Workspace files from step 4 have already been written and are not rolled back.
- Writing is file by file, not an atomic folder swap. A failure halfway can leave the output partly updated. A file locked by another program is retried; if it still fails, the file is named in the error.
- After TypeScript declarations are generated, an API snapshot is published to the AI docs folder (default `.github/neko_ai`, set in `nekojs/config/ai.toml`). Changing `baseDir` does not move the AI docs. See [AI-assisted scripting](ai-support_us).

<a id="wiki-section-22"></a>
### Output layout

Output of the built-in TypeScript backend (default `.neko_probe/typescript/`):

```text
.neko_probe/typescript/
├── @package/                      # Java class declarations, one folder per package
│   ├── index.d.ts
│   └── net/minecraft/.../index.d.ts
├── @side-only/
│   ├── startup/{events,bindings}/index.d.ts
│   ├── server/{events,events/recipes,bindings}/index.d.ts
│   └── client/{events,bindings}/index.d.ts
├── @special/
│   ├── index.d.ts
│   ├── types/index.d.ts           # Registry literals (RegistryTypes)
│   └── dependencies.json          # Which declaration dependencies were filled in
├── @manual/
│   ├── index.d.ts                 # Hand-written declarations
│   ├── globals.d.ts               # ProbeEvents.addGlobal
│   └── references.d.ts            # When manual references exist
├── @nekojs/managed/<side>/index.d.ts
├── @registry-builders/index.d.ts
└── @event-builders/index.d.ts
```

Output of the built-in Python backend (default `.neko_probe/python/nekojs/`):

```text
.neko_probe/python/nekojs/
├── __init__.pyi    # Global bindings, event groups, addGlobal; target of from nekojs import *
├── py.typed        # PEP 561 marker
├── README.md
├── _java/          # Java type stubs, one folder per package
└── _events/        # Event declarations, one folder per script type
```

These folder names are conventions of the built-in backends. The probe framework does not check them; it only requires `render` to return a map from relative paths to text.

Declarations for API modules such as `nekojs/api/stable` go to `@side-only/<side>/bindings/api-modules.d.ts`. See [Module system](module-system_us#wiki-api-modules).

<a id="wiki-section-23"></a>
### Editor config merging

Each run updates only the entries probe manages and leaves everything else alone:

- `compilerOptions.paths`: `java:*`, `@side-only/<side>`, `@side-only/<side>/*`, `@special`, `@special/*`;
- `include`: entries pointing at `@package`, `@manual`, `@side-only/<side>` and `@nekojs/managed/<side>` under `.neko_probe/typescript/`, plus globs for the standard script file types;
- `typeRoots`: `<output folder>/@package` and `../node_modules/@types`;
- `typeAcquisition.enable = false`;
- JSX keys, which follow `jsxAutomaticRuntime` in `engine.toml`. See [TypeScript and JSX](typescript-and-jsx_us).

Relative paths are computed from the script folder and output folder. A `.neko_probe/jsconfig.json` is written as well.

Python `extraPaths` are written to the game directory, `nekojs/`, every script folder, and subfolders of script folders that contain `.py` files (up to 8 levels deep, skipping dependency, build and cache folders).

`reset_config` deletes the config files each backend fully owns (jsconfig, pyrightconfig, snippets) and keeps the shared `.vscode/settings.json`. It then rebuilds the default config and runs the TypeScript probe again.

<a id="wiki-section-24"></a>
### Key classes

| Class | Package | Responsibility |
|---|---|---|
| `ProbeCoordinator` | `probe` | Drives the whole run: collection, IR, events, backends, editor config |
| `ProbeClassCollector` | `probe` | Collects classes from the seeds |
| `ProbeIrBuilder` | `probe` | Builds the shared IR and applies ProbeEvents edits |
| `ProbeBackend` / `ProbeContext` | `probe` | The backend interface and the context passed to it |
| `ProbeBackendRegistry` | `probe` | Registers backends by (language, name) and detects conflicts |
| `ProbeBackendSelector` | `probe` | Parses `/nekojs probe` arguments; shared by all platforms |
| `ProbeOutputCommitter` | `probe` | Validates output paths and writes files |
| `ProbeConfig` / `ProbeConfigLoader` | `probe` | Reads and writes `probe.toml` |
| `TypeReflector` / `TypeDecl` | `probe.ir` | Reflects `Class<?>` into a language-neutral IR; handles `@Remap`, `@HideFromJS`, `@Doc` |
| `TypeScriptClassRenderer` | `probe.ir` | Renders `TypeDecl` as TypeScript |
| `TypeAliasRegistry` | `probe.types` | Builds `$Foo_` input aliases and collection input types |
| `TypeScriptProbeBackend` | `probe.backend.typescript` | Built-in TypeScript backend; calls `BindingDeclarationGenerator`, `EventDeclarationGenerator`, `RecipeEventDeclarationGenerator` and others |
| `PythonProbeBackend` | `probe.backend.python` | Built-in Python backend |
| `NekoProbeBuiltinPlugin` | `probe` | Registers the two built-in backends |

All packages are under `com.tkisor.nekojs`; the source is in `common/src/main/java/com/tkisor/nekojs/probe/`.

<a id="wiki-section-25"></a>
### Data source: the catalog snapshot

Backends never read runtime state directly. The command first calls `NekoScriptCatalog.snapshot(runtime)` to build an immutable `NekoScriptCatalogSnapshot`. Backends read only that snapshot, the collected classes and the IR. The snapshot contains:

- `bindings`: global binding names and Java types;
- `events`: event groups, names, event types and cancelability;
- `adapters`: target types and input shapes of type adapters;
- `moduleBindings` / `moduleEvents`: API modules published per script type;
- `recipeNamespaces`: recipe namespaces and method signatures;
- `registryTypes`: registry IDs, tags and namespaces;
- `hostExtensions`: extension methods injected into vanilla classes;
- `snippets`, `typeDocs`, `manualDeclarations`.

<a id="wiki-section-26"></a>
### TypeScript projection rules

Some Java constructs cannot be expressed exactly in TypeScript. The built-in backend handles them like this:

- `char` and `Character` appear as `string`. The runtime checks that the length is 1.
- Constructors cannot have their own type parameters, so type variables on constructors appear as their upper bound.
- Collection parameters distinguish Java values from script input. The short alias `$List_<T>` is used only when the Java element type and the input type are the same; otherwise it is written as `$List<$Payload> | $Payload_[]`.
- Recipe schemas with fields offer positional overloads and a full named-options object overload.
- Callable declarations wrap softly at 120 characters; a single very long type can still exceed that.

<a id="wiki-section-27"></a>
## Feeding data to probe (plugin authors)

Bindings, events and adapters your plugin registers go into the snapshot automatically. The options below make the declarations more precise.

<a id="wiki-section-28"></a>
### Doc annotations

After `registry.register("Foo", new FooJS())`, probe reflects the public methods of `FooJS`. Put `@Doc` on classes and methods, and use `@Param` and `@Return` on methods to describe parameters and return values. Probe turns them into JSDoc:

```java
@Doc("Utility helpers for my mod.")
public final class FooJS {
    @Doc("Returns the display name of an item.")
    @Param(name = "id", value = "item id, e.g. minecraft:stone")
    public String displayName(String id) {
        // ...
    }
}
```

When annotations are not an option, provide docs from code through the `registerTypeDocs` hook. Annotations like `@Remap` and `@HideFromJS` also shape the declarations. See [Annotations](annotations_us) and [Plugin development](plugin-development_us).

<a id="wiki-section-29"></a>
### Explicit input and output types

- `@ScriptInput(Provider.class)` on a parameter replaces a broad input type with the provider's `AdapterInputShape`. The provider implements `ScriptInputShapeProvider` and has a public no-argument constructor.
- `@ScriptOutput("TypeScript type expression")` on a method replaces its TypeScript return type. Write external types as `import("module").Type`. If the returned value still has Java members, keep the Java type in an intersection.

Both annotations affect TypeScript declarations only. They register no converters and do no runtime checks. Python declarations keep the reflected types. `ProbeEvents.modifyType` edits take precedence over them.

<a id="wiki-section-30"></a>
### Registry literals

`NekoCatalogPlatformProvider.registryTypes(...)` supplies registry IDs, and probe generates `@special/types` from them:

- `RegistryTypes.<Name>`: every ID in the registry, such as `RegistryTypes.Item`.
- `RegistryTypes.<Name>Tag`: the registry's current tags. Adapters use `template("#", registryTag("Item"))` so `'#minecraft:planks'` completes too. Without tags it falls back to `string`.
- `RegistryTypes.Namespace`: all mod IDs plus the namespaces of all registry entries. Adapters use `template("@", namespace())` so `'@create'` completes.

Registries used by published `SimpleRegistryBasedAdapter`s are added automatically. If one name maps to different registries, or clashes with a generated `Tag` / `Namespace` type, probe reports `NEKO-4052` and refuses to generate. Python gets `Literal[...]` for fewer than 512 entries, and `str` otherwise.

For how adapter input shapes become `$Foo_`, see [Type adapters](type-adapters_us).

<a id="wiki-section-31"></a>
### Hand-written declarations and snippets

- For things reflection cannot express, call `registry.registerManualDeclaration(...)` in the `registerTypeDocs` hook to supply a hand-written `.d.ts` fragment. It goes to `@manual/index.d.ts`. NekoJS's own hand-written declarations live in `NekoCommonManualDeclarations`.
- `NekoCatalogPlatformProvider.snippets()` supplies snippets. Scripts can add more with `ProbeEvents.snippets`.

<a id="wiki-section-32"></a>
## Writing a custom backend

The built-in backends are `typescript:builtin` and `python:builtin`. A plugin can provide an alternative for the same language under a different name, or produce something new under a new language ID, such as Lua declarations, a JSON manifest or a docs site.

<a id="wiki-section-33"></a>
### Minimal example

A backend implements only `languageId()`, `name()` and `render(ctx)`. This one lists the collected classes as JSON:

```java
public final class JsonListBackend implements ProbeBackend {

    @Override
    public String languageId() {          // Language ID: used by the command and the default output folder
        return "json";
    }

    @Override
    public String name() {                // Unique within the language
        return "list";
    }

    @Override
    public Map<String, String> render(ProbeContext ctx) {
        // Return "relative path -> text"; never write to disk yourself
        StringBuilder sb = new StringBuilder("{\"classes\":[\n");
        boolean first = true;
        for (Class<?> c : ctx.collectedClasses()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {\"fqn\": \"").append(c.getName()).append("\"}");
        }
        sb.append("\n]}\n");
        return Map.of("classes.json", sb.toString());
    }
}
```

Register it in your plugin's `registerProbeBackends` hook:

```java
@Override
public void registerProbeBackends(ProbeBackendRegistry registry) {
    registry.register(new JsonListBackend(), "my-addon");
}
```

Now `/nekojs probe json` writes `.neko_probe/json/classes.json`. `/nekojs probe all` runs it too.

<a id="wiki-section-34"></a>
### The render contract

- Keys are paths relative to the output folder, separated by `/`. Values are UTF-8 text.
- Do not touch the disk. The default `generate()` writes files, deletes stale ones, retries locked files and validates paths.
- If it throws, the backend is marked failed, the message is shown to whoever ran the command, and the output folder stays unchanged. For a missing precondition, throw `IllegalArgumentException` with a clear message.
- `ctx` offers `snapshot()`, `collectedClasses()`, `ir()`, `config()`, `paths()`, `languageDir()` and `overrides()`.

<a id="wiki-section-35"></a>
### Optional methods

| Method | Default | When to override |
|---|---|---|
| `requiresIr()` | `false` | Return `true` when you need `ctx.ir()`, the `TypeDecl` list including ProbeEvents edits |
| `priority()` | `0` | With several backends for one language, the highest is the default |
| `outputDir(paths, config)` | `<baseDir>/<language>`, overridable by the `outputDir` setting | When you need a special folder |
| `contributeEditorConfig(contributor, ctx)` | Does nothing | Let editors find your output, merging through the `contributor` merge methods |
| `resetEditorConfig(paths)` | Does nothing | Delete the config files you wrote when `reset_config` runs |
| `generate(ctx)` | render, then write | Normally do not override |

<a id="wiki-section-36"></a>
### Rules

The framework stops these cases:

- The same (language, name) registered twice: when the registry locks at startup, the game crashes and lists the conflicting registrants.
- `render` returns an illegal path (empty, absolute, or containing `..`): the backend fails and writes nothing.
- Two backends in one run share an output folder: only the first runs; the rest are skipped with a warning. Otherwise the second would delete the first one's files as stale.

Several backends for one language can coexist. `/nekojs probe <language>` uses `languages.<language>.backend` from `probe.toml` first, and otherwise the highest priority. Run the others with `/nekojs probe <language> <name>`.

<a id="wiki-section-37"></a>
### Replacing the TypeScript backend

- Keeping the built-in names (`@package`, `@side-only`, `@special`, `$Foo`, `$Foo_`) is easiest; the existing editor config keeps working.
- For a different layout, change two places: your backend's `contributeEditorConfig`, and the plugin hook `NekoJSPlugin.modifyWorkspaceConfig`. The hook controls the first `jsconfig.json`; without it, path aliases are wrong until probe runs for the first time.
- Existing scripts hard-code module names like `@side-only/...`, so a new layout is a breaking change for script authors.

<a id="wiki-section-38"></a>
## Known limitations

- Documentation is only as complete as the annotations. Classes without `@Doc` show only signatures; add docs with `registerTypeDocs` or `ProbeEvents.modifyType`.
- Third-party jars not compiled with `-parameters`, and JDK classes, show parameter names as `arg0`, `arg1`. NekoJS's own classes have real names.
- Declarations are a snapshot; regenerate after the game content changes.
- Python declarations do not keep generic relationships.
- Declaration dependencies are filled in under the current exclusion and hiding rules; not every Java class is exported.

<a id="wiki-section-39"></a>
## Tests

Tests live in `common/src/test/java/com/tkisor/nekojs/probe/`. The most useful ones:

- `ProbeBackendRegistryTest`: backend registration and conflicts.
- `ProbeConfigTest`, `ProbeScanModeTest`: `probe.toml` parsing and the three scan modes.
- `ProbeOutputCommitterTest`: writing and path validation.
- `TypeScriptNoopIrGoldenTest`, `LegacyProbeTreeTest`, `ProbeOutputCompatibilityTest`: golden comparisons of TypeScript output.
- `TypeScriptProbeBackendEditorConfigTest`, `FileEditorConfigContributorTest`: editor config merging.
- `ProbeEventsSurfaceGoldenTest`: the public ProbeEvents surface.

After changing probe, run `:common:check`.

<a id="wiki-section-40"></a>
## Related pages

- [Commands](commands_us): all `/nekojs` commands.
- [TypeScript and JSX](typescript-and-jsx_us): TypeScript scripts and the JSX settings in `jsconfig.json`.
- [Python scripts](python-scripts_us): Python scripting itself.
- [Module system](module-system_us): `java:` imports and API modules.
- [Annotations](annotations_us): `@Remap`, `@HideFromJS` and other annotations that shape declarations.
- [Type adapters](type-adapters_us): where `$Foo_` input aliases come from.
- [Plugin development](plugin-development_us): `registerProbeBackends` and other plugin hooks.

<!-- wiki-nav -->

---

[Previous: Project architecture](project-architecture_us) · [Contents](Home) · [Next: Build system](build-system_us)
