# NekuJS: run KubeJS addons on NekoJS

NekuJS is an optional NekoJS addon. It lets some mods written for KubeJS — "KubeJS addons" such as KubeJS Create or KubeJS Mekanism — load and work on top of NekoJS.

It is developed in this repository but ships as a separate JAR. It is not included in NekoJS.

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1 (21.1.238 in development) |
| Java | 21 |
| JAR | `nekujs-neoforge-1.21.1-0.1.0.jar` |

## What it is, and what it is not

**NekuJS is not a full KubeJS replacement.** It provides just enough of the KubeJS and Rhino APIs for the supported addons to load. Your scripts still run in NekoJS (GraalJS), not in Rhino.

So that addons find the APIs they depend on, the NekuJS JAR registers three mod IDs:

| Mod ID | Version | Purpose |
|---|---|---|
| `nekujs` | 0.1.0 | The addon itself |
| `kubejs` | 2101.7.2-build.309 | The subset of the KubeJS API that addons need, backed by NekoJS |
| `rhino` | 2101.2.7-build.81 | Rhino type and conversion classes only; no Rhino engine |

> **Do not install the original KubeJS or Rhino together with NekuJS.** They use the same mod IDs and packages and will conflict.

## Installing

1. Install NekoJS and Graal for NeoForge 1.21.1 as usual.
2. Put the NekuJS JAR in `mods/`.
3. Put the KubeJS addons you want in `mods/` as well, unchanged.
4. Start the game, then run `/nekojs probe` so code completion includes the addons' events and recipes.

After updating NekuJS or an addon, restart the game and run `/nekojs probe` again. A script reload is not enough.

## What works

- **Addon plugins load.** NekuJS reads each addon's `kubejs.plugins.txt`, checks its dependencies and side, and runs the plugin inside NekoJS. Addon events, bindings, type wrappers and component codecs become normal NekoJS features.
- **Addon recipe types work in scripts.** Recipe schemas shipped by an addon are turned into NekoJS recipe definitions, so they show up in code completion and can be called from `ServerEvents.recipes`:

```js
ServerEvents.recipes(event => {
  event.recipes.mekanism.crushing(
    { id: "minecraft:gravel", count: 2 },
    { item: "minecraft:cobblestone", count: 3 }
  ).id("nekujs:gravel_from_cobblestone");
});
```

- Fields, constructor variants, aliases and defaults follow the addon's own schema. Chained setters use the addon's names, for example `.processingTime(25).heated()`.
- Recipes that extend vanilla shaped or special recipes are supported. This restores `create:mechanical_crafting`, `mekanism:mek_data`, `mekanism:clear_configuration`, `mekanism:bin_insert`, `mekanism:bin_extract`, `mekanismtools:paxel` and `mekanismtools:banner_shield`.
- Generated recipe IDs follow NekoJS rules, not KubeJS rules. Use `.id(...)` when you need a stable ID.

### Input checking

Recipe arguments are checked before the recipe is created, so a rejected call never leaves a half-built recipe behind. Counts and amounts must be whole numbers in range; fractions and overflowing values are rejected instead of being silently rounded.

## What does not work yet

Anything not listed above should be treated as unsupported. NekuJS fails with a clear error rather than guessing. In particular:

- `create:sequenced_assembly` (recipes nested inside recipes).
- KubeJS mirror/shrink options for shaped recipes other than the defaults.
- Events that target a specific object (for example "this block only"), and the full KubeJS cancel/result behavior.
- Registering new registry objects through addon builders, dynamic registry lookups and data generation contexts.
- Matching KubeJS's automatic recipe IDs and every KubeJS type conversion.
- The old way of loading schemas from `config/nekujs/schema-sources/`. Install the addon JAR as a mod instead.

## Tested addons

These were tested with the development client. "Loads" means the game starts and the addon's plugin runs. It does **not** mean every feature has been played through, tested on a dedicated server or tested across script reloads. Maintainer acceptance has not been recorded yet.

| Addon | Result |
|---|---|
| Architectury 13.0.11 | Loads |
| Create 6.0.10 | Loads, including Flywheel, Ponder and Registrate |
| Mekanism 10.7.19.85 | Loads |
| KubeJS Create 2101.3.1-build.18 | Loads. 16 recipe types callable, 1 rejected on purpose |
| KubeJS Mekanism 2101.1.7-build.18 | Loads. 32 recipe types callable |
| KubeJS Additions 6.0.0 | Loads. Respawn and gameplay features not yet checked |

A larger local set of 14 JARs also starts and survives a client script reload. It adds ModifyJS, KeyBindJS, JavaScriptJS, KubeJS EnderIO, EnderIO, LootJS and JEI KubeJS. Of these, KubeJS EnderIO's 9 recipe types are covered by automated tests. LootJS and the key-input side of KeyBindJS still need work.

## Developing NekuJS

Run from the repository root (use `gradlew.bat` on Windows):

```sh
./gradlew --no-daemon guardLint :addons:nekujs:build
./gradlew --no-daemon :addons:nekujs:auditCompatibility :addons:nekujs:fixtureTest
./gradlew --no-daemon :addons:nekujs:compatProofTest
./gradlew :addons:nekujs:runTestClient
```

| Task | What it does |
|---|---|
| `build` | Builds the JAR and runs the normal tests. Needs no third-party JARs |
| `auditCompatibility` | Scans the test addons for KubeJS/Rhino references without running them |
| `fixtureTest` | Tests against the six addons in the table above |
| `expandedFixtureTest` | Tests against the larger local set. Opt-in; not part of `build` |
| `compatProofTest` | Old experiment kept for reference; not proof that production works |
| `runTestClient` | Starts a game client with the test addons installed |

The test addons are not committed. Put them in `addons/nekujs/1211nekujs`, or pass another folder with `-PnekujsFixturesDir=/path/to/fixtures`.

Outputs:

```text
addons/nekujs/build/libs/nekujs-neoforge-1.21.1-0.1.0.jar
addons/nekujs/build/reports/compatibility/fixtures.json
addons/nekujs/build/reports/tests/{test,auditTest,fixtureTest,compatProofTest}/
```

### Running from IntelliJ IDEA

Use the **NekuJS Client (1.21.1)** run configuration for a normal client, or **NekuJS Test Client (1.21.1)** for a client with the test addons. From the command line, always use the full task names `:addons:nekujs:runClient` and `:addons:nekujs:runTestClient`; a bare `runClient` matches several projects.

Before the test client starts, every JAR from the test addon folder is copied into `addons/nekujs/build/run/testClient/mods`. Files you add directly to that `mods` folder are removed on the next run, so put new test addons in the source folder instead. Worlds and configs are not touched.

## Code layout

| Folder | Contents |
|---|---|
| `src/main` | Shipping code |
| `src/test` | Tests |
| `src/audit` | The compatibility scanner (not shipped) |
| `src/compatProof` | The old experiment (not shipped) |

## Still to do before a release

- Start a dedicated server with all test addons, and test respawn and reload cleanup in a real world.
- Reload recipes in a running game with every supported addon recipe type.
- Targeted events, full cancel/result behavior, dynamic registries and data generation.
- Fix unrelated TypeScript errors seen in some generated declarations.
- Test the packaged JAR and get maintainer acceptance.

We only plan to add a real Rhino engine if a specific addon turns out to need behavior the current approach cannot provide.
