# Minimal external NekoJS addon (ticket 08 example)

A complete, runnable external plugin addon: one plugin class, one custom
extension point, one script-visible binding, and the two loader metadata files
that make the jar discoverable on both loaders.

## Layout

```
src/main/java/com/example/demo/DemoAddonPlugin.java   the addon (public API only)
src/main/resources/META-INF/neoforge.mods.toml        NeoForge discovery metadata
src/main/resources/fabric.mod.json                    Fabric discovery metadata (nekojs entrypoint)
```

## Build (public instructions)

The addon compiles against the published NekoJS fat jar only — no test seams,
no engine internals:

```bash
javac -cp nekojs-neoforge-<mc>-<version>.jar \
      -d build/classes $(find src/main/java -name '*.java')
jar --create --file demomod-1.0.0.jar -C build/classes . -C src/main/resources .
```

Inside this repository the same dependency surface is proven continuously by
the test-only fixture twin (`common/src/addonFixture`, compiled against
`:common` main output only — a strict subset of the fat jar) and by
`verifyExternalAddonIsolation`, which asserts none of the five production jars
contains fixture/example content.

## Install

- **NeoForge**: drop `demomod-1.0.0.jar` into the `mods/` directory. FML
  discovers the jar through `META-INF/neoforge.mods.toml` and scans it for
  `@RegisterNekoJSPlugin`.
- **Fabric**: drop the same jar into `mods/`. FabricLoader reads
  `fabric.mod.json`, resolves the `nekojs` entrypoint
  (`com.example.demo.DemoAddonPlugin`) and hands it to the NekoJS bootstrap.

## What the addon does

1. `registerBinding` contributes the `DemoAddon` global binding (hook
   projection of the built-in `nekojs:bindings` point) — scripts call
   `DemoAddon.marker()` and read `"demomod-frozen-product"`.
2. `registerPluginExtensionPoints` registers the custom point
   `demomod:greetings` (`dependsOnId("nekojs:bindings")`); its initializer
   reads the frozen bindings product through `context.result(...)}`, its
   collector gathers every `Greeter` plugin, and its `HANDLE` returns the
   frozen product after finish.
3. The binding value and the point product are frozen once at bootstrap; an
   ordinary `/nekojs reload` never re-discovers, re-bootstraps or re-freezes
   the plugin runtime — every script generation reads the same instance.

A server script can observe the chain:

```js
// server_scripts/demo_probe.js
console.log(DemoAddon.marker())   // demomod-frozen-product, same instance every reload
```
