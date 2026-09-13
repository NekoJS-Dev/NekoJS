# Ticket 08 — minimal runnable external NekoJS addon

This is the *documented* minimal shape of an external addon: a separate mod jar that NekoJS
discovers through the loader and whose contributions reach scripts. It is the same shape as the
ticket 08 test fixture (`bench/plugin-addon/fixture`), reduced to the smallest thing that works.

Everything here uses only already-gated public entry points:

| used | where it is defined | public? |
|---|---|---|
| `NekoJSPlugin` | `com.tkisor.nekojs.api.NekoJSPlugin` | yes — 作者面唯一入口 (ADR-0010) |
| `@RegisterNekoJSPlugin` | `com.tkisor.nekojs.api.annotation` | yes |
| `BindingRegistry` | `com.tkisor.nekojs.api.data` | yes |
| `NodeModuleRegister` | `com.tkisor.nekojs.core.module` | yes (the only non-`api.*` type a plugin needs) |

No extension point is created, no internal test seam is referenced, and no `:common` test class is
on the classpath — this is the same dependency face the ticket 08 smoke exercises.

## 1. The plugin class

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.module.NodeModuleRegister;

@RegisterNekoJSPlugin(priority = 777)
public final class MyAddonPlugin implements NekoJSPlugin {

    @Override
    public void registerBinding(BindingRegistry registry) {
        // projection of the pre-existing nekojs:bindings extension point
        registry.register("MyHelper", new MyHelperJS());
    }

    @Override
    public void registerNodeModules(NodeModuleRegister registry) {
        // projection of the pre-existing nekojs:node_modules extension point
        registry.register("myaddon:hello", """
                module.exports = { greet: function (name) { return 'hello ' + name; } };
                """);
    }
}
```

```java
package com.example.myaddon;

public final class MyHelperJS {
    public int add(int a, int b) { return a + b; }
}
```

## 2. Loader metadata

### NeoForge — `src/main/resources/META-INF/neoforge.mods.toml`

```toml
modLoader="javafml"
loaderVersion="[4,)"
license="LGPL-3.0"

[[mods]]
modId="myaddon"
version="1.0.0"
displayName="My NekoJS Addon"

[[dependencies."myaddon"]]
modId="neoforge"
type="required"
versionRange="[26,)"
ordering="NONE"
side="BOTH"
[[dependencies."myaddon"]]
modId="nekojs"
type="required"
versionRange="[1.0.0,)"
ordering="NONE"
side="BOTH"
```

NekoJS discovers the class through FML's annotation/mod-metadata scan
(`NeoForgePluginLoader` -> `ReflectionUtils.findAnnotationClasses` ->
`NekoJSBasePluginManager.registerClass`). The `nekojs` dependency is what makes the loader refuse
to start the addon without NekoJS present.

### Fabric — `src/main/resources/fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "myaddon",
  "version": "1.0.0",
  "name": "My NekoJS Addon",
  "environment": "*",
  "entrypoints": {
    "nekojs": ["com.example.myaddon.MyAddonPlugin"]
  },
  "depends": { "fabricloader": ">=0.19.3", "nekojs": ">=1.0.0", "minecraft": "~26.1.2" }
}
```

`nekojs` is a **custom entrypoint key**. `FabricPluginLoader` reads
`FabricLoader.getInstance().getEntrypointContainers("nekojs", NekoJSPlugin.class)`, so the declared
class must implement `NekoJSPlugin` and be loaded by the loader's own entrypoint machinery. On
Fabric you do **not** need `@RegisterNekoJSPlugin` (Fabric has no FML annotation scan); keeping the
annotation is harmless and makes one source tree work on both loaders.

## 3. Build

The addon compiles against the NekoJS **platform fat jar** — there is no separate SPI artifact
(see `wiki/插件开发.md` §Gradle 依赖). A plain `javac` + `jar` invocation is enough;
`bench/plugin-addon/build-addon.ps1` is a working reference that produces both loader flavours from
one set of classes:

```
javac -encoding UTF-8 -parameters --release 25 \
      -cp "nekojs-neoforge-26.1.2-<version>.jar;slf4j-api.jar" \
      -d build/classes $(find src/main/java -name '*.java')

jar --create --file build/myaddon-1.0.0-neoforge.jar -C build/classes . \
    -C src/main/resources-neoforge .
jar --create --file build/myaddon-1.0.0-fabric.jar   -C build/classes . \
    -C src/main/resources-fabric .
```

## 4. Install and verify

1. Copy the matching jar into `<game dir>/mods/`.
2. Start the game / server and search the log for `MyAddonPlugin`. The plugin class is instantiated
   exactly once by NekoJS's `NekoJSBasePluginManager` (the loader may construct one extra throwaway
   instance of its own during entrypoint probing on Fabric — that one is never discovered).
3. From a script, call the binding and the node module:

```js
// server_scripts/main.js
console.info('add=' + MyHelper.add(1, 2));
const hello = require('myaddon:hello');
console.info(hello.greet('world'));
```

4. Run `/nekojs reload server`. The Plugin Runtime is **not** re-bootstrapped: bindings and node
   modules keep working after the reload, but the reloaded scripts get a fresh script environment.
5. Run `/nekojs probe` to confirm the binding shows up in the generated `.d.ts`.

## 5. Failure modes you will actually hit

| symptom | cause |
|---|---|
| binding missing, no crash | class does not implement `NekoJSPlugin`, is abstract/interface, has no no-arg constructor, or is missing `@RegisterNekoJSPlugin` on NeoForge. NekoJS logs one `error` line and carries on. |
| binding missing on Fabric only | the entrypoint key is not exactly `nekojs`, or the class does not implement `NekoJSPlugin`. |
| `IllegalStateException` at startup | the plugin registered into an extension point after its registration window closed (`Sealable` channels: type docs / lifecycle / node modules / recipe namespaces / recipe lifecycle / recipe schemas, plus `ScriptPropertyRegistry` / `ProbeBackendRegistry` / `ScriptCompilerRegistry`). |
| `IllegalArgumentException` at startup | the plugin registered two extension points with the same id, or used a `nekojs:*` id. |
| `IllegalStateException: ... dependsOn unregistered point ...` | an extension point declares `dependsOnId("typo")`. |
| silent `false` from `registry.register(name, value)` | another plugin already owns that global name — `BindingRegistry` is first-wins and only warns. |

## 6. Migration notes for existing addons

The public entry points above did not change in this refactor. The migration-relevant changes are:

- **`NekoPluginRuntime.bootstrap(List<NekoJSPlugin>, ScriptPropertyRegistry)` was removed** (ticket 08
  AC15). It had no production caller — every platform entry uses `bootstrapOwned`. If you called it
  from an embedding scenario, call `NekoPluginRuntime.bootstrapOwned(ownedPlugins, scriptProperties)`
  instead; it additionally performs the managed API bootstrap.
- **Node module ids are now registry keys, not paths**. `require('mymod:thing')` used to be routed
  through the filesystem resolver and failed with `Illegal char <:>` on Windows when the id was not
  found in `node_modules`. Namespaced ids are now classified as built-in registry keys, so a missing
  one reports a normal module-resolution error. No addon needs to change; ids that previously happened
  to collide with a file name under `nekojs/node_modules/<mymod>:<thing>` will no longer resolve as files.
- **The plugin class is instantiated once per process.** A normal `/nekojs reload` does not
  re-instantiate plugins and does not re-run `registerBinding` / `registerNodeModules`; do not cache
  per-reload state in plugin fields — use the lifecycle hooks if you need per-generation work.
