# NekuJS: bounded KubeJS compatibility

NekuJS is an independently packaged NekoJS addon for Minecraft 1.21.1, NeoForge 21.1 and Java 21. It is developed here but is not bundled into NekoJS.

**This is not a complete KubeJS replacement.** One shipping JAR now supplies three identities: `nekujs` 0.1.0, `kubejs` 2101.7.2-build.309 and `rhino` 2101.2.7-build.81. The latter identities represent a bounded native API provider, not upstream KubeJS or a Rhino evaluator. Do not install original KubeJS/Rhino alongside it: their IDs and packages conflict.

## Runtime contract

- Real `kubejs.plugins.txt` discovery uses loader-owned mod roots, dependency/side gates before class loading, and duplicate validation before construction. Original plugin instances and definitions belong to the existing NekoJS Plugin Runtime.
- Legacy plugin hooks contribute native event groups, bindings, per-phase type wrappers, component codecs and builder definitions through the existing NekoJS plugin facade. No second runtime, evaluator or guest-context owner is created.
- Registry-dependent data-component descriptions are collected on that same bridge through NekoJS `afterInit` (load-complete work), before the legacy `afterInit` callbacks. They must not read deferred values in the mod constructor. Event/builder definitions, bindings and adapters keep their early phases. The descriptions are retained metadata; full upstream data-component conversion is not enabled.
- Supported untargeted events publish the actual addon payload class into NekoJS EventBusJS, callback validation and Probe. Phase filtering happens before payload resolution. Subscriptions and generation cleanup stay native; unused posts return PASS. Unsupported targeted channels keep optional wrappers unresolved and reject subscriptions; immediate legacy exits/cancellation also reject explicitly.
- The native Rhino-named facade implements the TypeInfo, Context conversion, Wrapper and annotation contracts required by the inspected fixtures. Reflective wildcard metadata selects a lower bound before an upper bound; unbounded `?` retains a distinct `NONE` sentinel, not an explicitly declared `Object`. Nested concrete payload types remain intact. Type variables and generic-array metadata remain explicitly unsupported. JavaScript still executes in NekoJS/GraalJS. There is no Rhino ScriptRuntime.
- Create/Mekanism component codecs feed bounded native recipe schema translation, including inherited fields, constructors and declarative functions. Builder definitions remain metadata; registry-object insertion is not enabled. Only explicitly supported schema/value semantics are callable.
- Unsupported dynamic registry lookup, contextual source positions and data-map generation without a borrowed sink fail explicitly. Only the implemented paths are supported; unrelated upstream API classes remain absent.

The Stonecutter active node and source roots are unchanged. Native recipe dispatch distinguishes argument-conversion mismatch from execution failure: only mismatches try another overload; a selected method failure propagates without schema/raw fallback. Native adapter acceptance/conversion failures are preserved at registration before Graal can normalize ClassCastException into a mismatch signal; custom scalar/reference/null conversions still use the existing adapters. Null primitive arguments are converted through the native boxed target before rejecting an unconverted null, so accepted custom null mappings are not bypassed. Production code is under `src/main`; contract tests are under `src/test`. The independent ASM auditor (`src/audit`) and historical evaluator spike (`src/compatProof`) are excluded from the shipping JAR. The latter deliberately has a narrower test-only ABI and is not production integration evidence.

Probe public-method discovery filters through the JVM before resolving private implementation signatures. This preserves the supplied Create station declaration without ComputerCraft; no optional-mod stub or extra catch was added. See the [measured failure and scope](<../../docs/research/2026-11-09-probe-private-signatures.md>). Restart the client after rebuilding, then run `/nekojs probe` to replace old TypeScript output. The [public-contract followup](<../../docs/research/2026-11-09-probe-public-contracts.md>) replaces villager implementation dependencies with output-only public interfaces and filters framework-owned constructors from declarations without changing Java/Graal construction. If a public optional signature itself is absent, independent class members remain declared, but the whole method set and heritage are omitted with an explicit incomplete report. Dependency-resolved reflection remains unchanged.

## Supplied fixture profile

| Fixture | Current evidence / limitation |
|---|---|
| Architectury 13.0.11 | Loaded by the real test client |
| Create 6.0.10 | Initialized with nested Flywheel/Ponder/Registrate dependencies |
| Mekanism 10.7.19.85 | Initialized in the real test client |
| KubeJS Create 2101.3.1-build.18 | Original plugin loaded; 21 schema documents compile to 16 callable profiles, four bases and one explicit rejection |
| KubeJS Mekanism 2101.1.7-build.18 | Original plugin loaded; 41 schema documents compile to 32 callable profiles and nine non-callable bases |
| KubeJS Additions 6.0.0 | Original plugin and event definitions loaded; actual player respawn/gameplay still unverified |

The loader-backed client smoke passed dependency validation, instantiated all three unchanged extension plugins, collected four legacy event groups, nine component codecs and one default builder definition, and completed client resource loading with all six supplied JARs. NekoJS startup/client scripts ran. This is initialization evidence, **not** a full gameplay, dedicated-server or script-reload compatibility pass. A session-server authentication timeout was nonfatal to this development startup. Human acceptance is not recorded.

The static report remains separate: it inventories exact KubeJS/Rhino references, hashes, metadata, plugin lines and schema paths **without executing classes**. It is not a full linker; nested JARs, reflection and mixins are not exhaustively checked. Its `runtimeCompatibility=NOT_VERIFIED` and `addonClassesExecuted=false` describe that audit, not the separate loader smoke.

## Native recipe schema translation

Installed legacy plugins own their schema resources. The [bridge](<src/main/java/com/tkisor/nekujs/bridge/LegacyRuntimeBridge.java>) reads their JAR or loader directory roots, compiles inheritance into NekoJS recipe definitions, and publishes through the existing `registerRecipeSchemas` hook. Script execution and Probe consume those same definitions; there is no second recipe engine or declaration-only patch.

The supplied graph contains 62 documents: 13 inheritance bases, 48 callable schema profiles and one unsupported feature profile. Callable means the graph and value semantics are implemented; it does not mean all 48 serializers have passed an actual game reload. The compiler preserves field order, constructor variants, field aliases, declarative no-argument functions, omitted optional defaults and explicit always-written defaults. Whole-field codecs use native ingredient/item/fluid conversion and the original registered component codecs. Unsupported profiles are registered as explicit rejections, so an automatic generic schema or raw handler cannot silently impersonate them.

Native builders retain NekoJS generated-ID policy; upstream KubeJS automatic-ID parity is not promised. Use `.id(...)` for stable IDs. Final recipe decoding remains in the existing NekoJS recipe-manager pipeline after all callback mutations; codecs do not make invalid cross-field combinations valid.

Canonical JSON keys remain snake_case where the serializer requires them; fluent aliases use the source-defined names, such as `.processingTime(25).heated().heatRequirement("heated")`. Declarative functions replace colliding setter aliases according to the source contract. Required argument conversion completes before a recipe is published, so a rejected constructor cannot leave a half-created entry.

Recipe declarations use the shared NekoJS Probe input renderer and the original ProbeJS component-TypeInfo model: nominal types, parameterized lists, unions, and canonical input aliases. The same rules apply to every namespace, not just Create. Whole-field/custom recipe-domain wrappers and addon-name-based type hints are not generated. An opaque registered converter without declared input forms retains its nominal class type; Probe does not guess DTOs from the addon name. Native class input aliases come from the existing adapter metadata channel, and builtin collection aliases share one canonical name table with their declaration emitter. Converter-owned component metadata can recursively project input arguments, while erased Java adapters preserve host generic arguments; genuine List/Map guest conversion is distinguished from host collection identity. List-only metadata remains list-only even when runtime wrapping accepts a scalar; can_write_self explicitly adds the scalar alternative. Actual constructor profiles, safe optional tails, named options and codec bounds remain authoritative. Runtime compatibility converters are separate from Probe: native fluid/ingredient conversion, the unchanged registered Create wrapper, and original TickDuration unit strings retain their bounded support. Heat/Mekanism codecs keep their existing bounded paths; no registry service or Rhino evaluator is added. Rebuild/restart and run `/nekojs probe` after updating; `FixtureReadableRecipeDeclarationTest` generates an unchanged multi-addon preview under build reports.

Structured JSON values deliberately accept only the documented narrow shapes. Actual typed host values retain their original codec semantics; existing native item string DSL remains available. Numeric script inputs must be exact safe integers where integral values are required. Source JSON constants and typed hosts may retain the full supported integral range, but fractional, overflowing or out-of-range counts/amounts are rejected before a permissive codec can truncate them.

The unchanged-codec fixture executes the following constructor through the real native event/builder path:

~~~js
ServerEvents.recipes(event => {
  event.recipes.mekanism.crushing(
    { id: "minecraft:gravel", count: 2 },
    { item: "minecraft:cobblestone", count: 3 }
  ).id("nekujs:gravel_from_cobblestone");
});
~~~

The former `config/nekujs/schema-sources/kubejs-mekanism.jar` opt-in crushing prototype is no longer auto-published. Install the unchanged extension as a mod; its owned schema graph is now the canonical source. The old resource-only importer remains a historical contract fixture, not a second runtime registration path. Resource changes require restarting the Plugin Runtime, not merely script reload.

The shared native `minecraft:shaped` and `minecraft:special` dependencies restore seven profiles: `create:mechanical_crafting`, `mekanism:mek_data`, `mekanism:clear_configuration`, `mekanism:bin_insert`, `mekanism:bin_extract`, `mekanismtools:paxel` and `mekanismtools:banner_shield`. Child serializer IDs are preserved; inherited `hidden` metadata does not forbid runtime calls. Native shaped cleanup validates encoded fields before allocation and runs again in the final preparation lifecycle. Non-default KubeJS mirror/shrink controls are not supplied or advertised. `create:sequenced_assembly` remains explicitly unsupported because nested recipe construction is not implemented; the thirteen inheritance bases remain non-callable. No fabricated generic fallback is exposed.

## Expanded local fixture profile

The development NeoForge dependency is 21.1.238 in both the core 1.21.1 node and this addon. The additional KeyBindJS 2101-1.1.0 requires at least 21.1.233 and JEI 19.70.0.469 requires at least 21.1.238. The real test client now satisfies those loader ranges. The original six-JAR compatibility evidence above is not evidence for every additional local JAR.

The current expanded directory also contains ModifyJS, KeyBindJS, JavaScriptJS, KubeJS EnderIO, EnderIO, LootJS and JEI KubeJS. The full supplied 14-JAR profile has completed development-client bootstrap and CLIENT reload (ten owned plugins); this is not full gameplay acceptance. The bounded API provider now declares build .309 after implementing and testing original EnderIO schema/factory hooks; the packaged metadata is checked against its unchanged dependency range. This baseline is not a claim of complete upstream compatibility. Its original programmatic hooks now compile nine EnderIO schemas and three subtype factories into native NekoJS definitions in isolated regression tests. KubeRecipe is only the binary superclass facade required by unchanged subclasses: it borrows native JSON and final preparation, with no independent recipe map or engine. Tank emptying and FireCrafting mutation/validation are tested; these tests are separate from the live expanded startup smoke. LootJS registry-aware wrappers/descriptions and broader helper semantics still require implementation, not a metadata-only version increase. Forced programmatic constructor overrides remain explicitly unsupported. The supplied EnderIO includes its required EnderCore/modded-conduits classes in nested JARs; they must not be diagnosed as missing from an outer-JAR-only scan.

Validator-backed wrappers use the existing managed-phase native adapter predicate. A false validator result declines without running the converter; callback failures remain visible. Original two-argument registrations remain supported. This fixes a concrete KeyBindJS initialization/conversion contract, not full targeting, key-input or gameplay behavior.

`expandedFixtureTest` is opt-in and requires the added local fixture binaries; it does not run under ordinary `build` or the baseline `fixtureTest`. Passing its current dependency/contract assertions does not establish full expanded-profile startup.

## Development and verification

Run from the repository root (`gradlew.bat` in Windows cmd/PowerShell):

~~~sh
./gradlew --no-daemon guardLint :addons:nekujs:build
./gradlew --no-daemon :addons:nekujs:auditCompatibility :addons:nekujs:fixtureTest
./gradlew --no-daemon :addons:nekujs:compatProofTest
./gradlew :addons:nekujs:runTestClient
~~~

`build` runs ordinary contracts and six MC-independent auditor regressions without requiring external fixture JARs. Fixture/proof/run tasks use local `addons/nekujs/1211nekujs` inputs; `-PnekujsFixturesDir=/path/to/fixtures` overrides them (relative overrides resolve from the repository root). `fixtureTest` selects and asserts the original exact six-JAR profile even when additional local fixtures are present; `expandedFixtureTest` separately checks the added profile contracts. No third-party fixture, engine, original KubeJS or Rhino evaluator is bundled.

~~~text
addons/nekujs/build/libs/nekujs-neoforge-1.21.1-0.1.0.jar
addons/nekujs/build/reports/compatibility/fixtures.json
addons/nekujs/build/reports/tests/{test,auditTest,fixtureTest,compatProofTest}/
~~~

Ordinary tests use independent JVM forks because their public platform/Minecraft bootstrap seams initialize process-singleton services. Packaging contracts require the production provider/API classes while excluding engine, auditor, historical spike and loader-test helper classes.

### IDEA and fixture client

Choose **NekuJS Client (1.21.1)** using the [normal run configuration](<../../.run/NekuJS_Client_1_21_1.run.xml>), or **NekuJS Test Client (1.21.1)** using the [fixture run configuration](<../../.run/NekuJS_Test_Client_1_21_1.run.xml>). Use fully qualified tasks `:addons:nekujs:runClient` / `:addons:nekujs:runTestClient`: unqualified root `runClient` selects multiple subprojects. Compiling the NekoJS dependency does not launch a second game.

Before test-client launch, `syncTestClientMods` copies every top-level fixture JAR into `addons/nekujs/build/run/testClient/mods`, where NeoForge discovers actual mods. This is a managed Sync destination: for a nonempty source, destination-only files are removed; an empty source follows Gradle NO-SOURCE/previous-output rules and does not guarantee arbitrary-file cleanup. Keep manual fixture changes in the source directory. Worlds/configuration are outside this sync destination. Normal client/server use separate `build/run/client` / `build/run/server` directories and do not stage fixtures.

The real loader groups multiple IDs from this one provider file in parts of its mod-list output; resource loading reports `mod/nekujs,kubejs,rhino`. Dependency validation and original plugin execution, not a UI count alone, establish provider discovery.

## Remaining gates

- Dedicated-server startup with the full fixture profile; actual world/respawn and reload cleanup through the original game hook.
- Actual game recipe reload with supported profiles, unsupported nested recipes and non-default KubeJS mirror/shrink controls, and complete upstream coercion/automatic-ID parity.
- Targeted events, full cancellation/result semantics, dynamic registry/data-generation contexts and optional integration behavior.
- Previously observed unrelated generated TypeScript generic/inheritance errors need separate fixes. Repository fixture gates do not establish that an old live Probe tree passes strict compilation.
- Packaged distribution smoke and maintainer acceptance. No claim of complete upstream binary compatibility.

Keep explicit unsupported-operation failures. Reconsider a real Rhino dependency only after documenting a concrete evaluator semantic requirement that cannot reasonably use the native facade.
