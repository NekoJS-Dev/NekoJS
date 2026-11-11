# NekuJS and original ProbeJS: generated output and log audit

## Scope and provenance

The recipe-specific source comparison below was inspected on 2026-11-10 without game launch, regeneration, Gradle/build, TypeScript execution, production edits or generated-file edits. The lead appendix adds separately executed strict TypeScript checks and log review. Installed metadata does not by itself establish declaration-dump freshness. Runtime acceptance is not inferred from signatures.

Original evidence is [the original recipe declaration snapshot](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L80-L138>); native evidence is [the native recipe declaration snapshot](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L22-L68>). **These are not the same runtime baseline.** Actual original installed archive metadata (`META-INF/neoforge.mods.toml`, decoded directly):

- [KubeJS](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/kubejs-neoforge-2101.7.2-build.381.jar>): lines 7–8, `kubejs / 2101.7.2-build.381`.
- [Rhino](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/rhino-2101.2.8-build.91.jar>): lines 7–8, `rhino / 2101.2.8-build.91`.
- [ProbeJS](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/ProbeJS-8.0.3.jar>): lines 7–8, `probejs / 8.0.3`.
- Native [provider identities:12–22](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/src/main/resources/META-INF/neoforge.mods.toml#L12-L22>) declare KubeJS `2101.7.2-build.309` and Rhino `2101.2.7-build.81`, explicitly bounded native providers, not upstream runtimes.

Six recipe-fixture JAR pairs were SHA-256 compared in memory between original and native client mods directories; every pair was byte-identical. Primary original metadata:

| Artifact | Installed metadata version | Archive metadata exact lines |
|---|---|---|
| [Create](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/create-1.21.1-6.0.10.jar>) | 6.0.10 | `META-INF/neoforge.mods.toml`:8–9 |
| [Create extension](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/kubejs-create-neoforge-2101.3.1-build.18.jar>) | 2101.3.1-build.18 | same entry:7–8 |
| [Mekanism](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/Mekanism-1.21.1-10.7.19.85.jar>) | 10.7.19; filename suffix .85 | same entry:7–8 |
| [Mekanism extension](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/kubejs-mekanism-neoforge-2101.1.7-build.18.jar>) | 2101.1.7-build.18 | same entry:7–8 |
| [EnderIO](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/enderio-8.2.12-beta.jar>) | 8.2.12-beta | same entry:7–8 |
| [EnderIO extension](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/kubejs_enderio-neoforge-1.21.1-0.13.0.jar>) | 1.21.1-0.13.0 | same entry:10–11 |

Archive metadata was read with Python zipfile; classes with javap, without extraction. Bytecode references below name archive entries/methods, not fictitious Java source lines.

## Top three confirmed native recipe declaration defects

All three are **P1 declaration-generation defects**, not evidence of failed runtime recipe codecs. Fix shared producers and regenerate; do not patch generated imports.

1. **Create heat enum import has no matching export.** [Native import:4](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L4>) imports `$HeatCondition_`. The target [native module:10–29](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/simibubi/create/content/processing/recipe/index.d.ts#L10-L29>) exports only `$ProcessingOutput`. [Heat input:22](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L22>) and [builder:224–232](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L224-L232>) use that symbol. Original [enum alias:59](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/com/simibubi/create/content/processing/recipe/index.d.ts#L59>) defines the three heat literals. This is missing declaration closure/export, not a changed enum contract.
2. **TickDuration imports an ungenerated package.** [Native import:6](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L6>) references `java:dev/latvian/mods/kubejs/util`; a path glob confirms no corresponding generated package declaration. [Create time:24](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L24>) and [Mekanism time:59](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L59>) use that class with number/Duration. Original [Create constructors:123–125](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L123-L125>) use their original TickDuration input alias. This finding concerns a missing declaration, not class loading.
3. **EnderIO dimensions emits a raw generic host.** [Native dimensions:33](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L33>) contains bare `$ResourceKey`, but [native ResourceKey:126–129](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/net/minecraft/resources/index.d.ts#L126-L129>) declares `$ResourceKey<T>` without a default. Original [dimensions:203–206](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L203-L206>) retains `ResourceKey<LevelStem>`. This is an invalid generic application and loss of domain information.

### Shared producer classification

Recipe imports derive from whole-field input shapes in [recipe import collection:434–446](<D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/RecipeEventDeclarationGenerator.java#L434-L446>). [HostValue rendering:216–225](<D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/AdapterAliasGenerator.java#L216-L225>) uses a raw Class, chooses an input alias when available, otherwise emits a bare host reference; it supplies no generic arguments. [programmatic host shapes:122–139](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/src/main/java/com/tkisor/nekujs/recipe/schema/ProgrammaticRecipeSchemas.java#L122-L139>) reduces ResourceKey/Holder/TagKey metadata to raw hosts. These are concrete owners of the dimensions typing loss.

Primary collection **already** queues recipe HostValue classes: [recipe seeds:75–80](<D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/probe/ProbeClassCollector.java#L75-L80>) and [shape traversal:173–183](<D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/probe/ProbeClassCollector.java#L173-L183>). However, the separate emitted-signature closure explicitly adds binding/event references at [closure roots:160–176](<D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/TypeScriptProbeBackend.java#L160-L176>), not recipe-generator imports. Missing recipe-only forced closure is a strong source-level explanation to test, not proof that primary seeding is absent. Preserve exclusion/unavailability policy and test referenced symbols, not just package existence.

## Semantic comparisons and intentional differences

### Create

Original [crushing/mixing:123–125](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L123-L125>) distinguishes ProcessingOutput-or-fluid results from ingredient-or-sized-fluid inputs. [Original Crushing builder:168–175](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L168-L175>) has processingTime, heatRequirement, heated and superheated. [Native input domains:23–25](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L23-L25>) retain those distinctions and add explicit structural inputs; [native builders:279–290](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L279-L290>) retain typed fields and heat functions. Extra snake_case aliases and named options are intentional native API choices, not missing overload parity.

Native results explicitly model processing-output `{id,count?,chance?}`. Original [output helper:5–8](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/dev/latvian/mods/kubejs/create/wrapper/index.d.ts#L5-L8>) supplies `CreateItem.of(stack,chance)` returning ProcessingOutput; [ProcessingOutput:14–36](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/com/simibubi/create/content/processing/recipe/index.d.ts#L14-L36>) owns the chance-bearing class. Do not conflate an ordinary ItemStack input with a typed chance-bearing output.

Original mechanical_crafting and sequenced_assembly are declared at [advanced profiles:121–131](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L121-L131>); native marks both `never` at [rejected profiles:675–676](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L675-L676>). This is a real explicit capability boundary, not a formatting defect or equivalent support. Nested recipe/postprocessor closure must be implemented before advertising parity.

### Mekanism

Both snapshots distinguish item output from counted ingredient input for crushing: [original:115](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L115>), [native domains:49–54](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L49-L54>), [native named options:87](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L87>). Named options and aliases do not erase those domains.

Original [sawing/reaction/rotary:110–116](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L110-L116>) gives input-first optional output parameters for sawing/reaction. Native [reaction variants:781–785](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L781-L785>) also exposes output-first and input-only variants. Primary [schema archive](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/mods/kubejs-mekanism-neoforge-2101.1.7-build.18.jar>), entries `data/mekanism/kubejs/recipe_schema/reaction.json`:45–57 and `sawing.json`:32–41, explicitly defines output-first constructors. Therefore the generated-signature difference alone is not a native regression: the original dump does not exhaust its installed schema constructors. The sawing entry:21–28 additionally bounds secondary_chance to 0.00001–1; TypeScript number does not prove codec validation.

Native [rejected profiles:732–735](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L732-L735>) explicitly rejects bin_insert, clear_configuration, mek_data and bin_extract. These are intentional bounded implementation gaps, not upstream-equivalent support.

### EnderIO

Original [namespace:80–89](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L80-L89>) declares zero-argument tank/fire_crafting and omits required item/fluid/result fields. Native [constructors:826–844](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L826-L844>) retains schema-required values and optional mode/energy fields. The EnderIO archive `TankRecipeSchema.class` initializer applies noFunctions to output/input/fluid/mode; mode remains optional FILL + alwaysWrite. `FireCraftingRecipeSchema.class` marks base lists defaultOptional + excluded + noFunctions. These flags suppress autogenerated setters, not constructor requirements; the original dump is not runtime truth for zero-argument construction.

Native [custom fluents:582–605](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/server/events/recipes/index.d.ts#L582-L605>) exposes block, blockTag, emptying and smelting while preserving suppressed tank setters. Original [custom-base import:6](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L6>) imports custom bases from a recipe package absent in that generated snapshot; [FireCrafting subclass:203–206](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L203-L206>) and [Tank subclass:266–267](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/server/events/recipes/index.d.ts#L266-L267>) therefore cannot establish absence of those runtime methods. Primary EnderIO archive entries `com/almostreliable/kubeio/recipe/TankKubeRecipe.class`, `FireCraftingKubeRecipe.class` and `AlloySmelterKubeRecipe.class` confirm emptying(), block(Block), blockTag(TagKey<Block>) and smelting(). FireCrafting.serialize() rejects zero base blocks/tags. Missing original imported bases are an original declaration-closure defect; native completion is an improvement here.

## Conclusion and verification boundary

Fix the three recipe declaration producer defects before claiming complete typed recipe usability. Preserve explicit unsupported profiles and original codec/subtype validation. Native builder ownership/naming are intentionally different; raw method/overload counts are not parity metrics. Snapshot/source/bytecode inspection establishes the findings above, not a new runtime acceptance pass. Strict TypeScript/log/surface execution evidence is lead-owned and may be appended after this recipe section freezes.

## Lead appendix: real output, logs and strict declarations

The recipe section above is a read-only source audit. This appendix adds independently executed checks;
it does not change the earlier runtime acceptance boundary. No game launch, dump regeneration, production
edit, generated-file edit or user-world modification was performed in this audit.

### Comparable inputs

All **14** native client mod JARs are byte-identical by SHA-256 to their counterparts in the original
instance. The original has four additional JARs: KubeJS, Rhino, ProbeJS and Better Advanced Tooltips.
See the [comparison manifest](<D:/mcmodDemo/NekoJS-mult/build/reports/probe-comparison/comparison-summary.json>).
Installed mod equality does not establish dump freshness or make the two runtime APIs identical.

### Strict TypeScript checks

TypeScript 5.8.3 loaded each actual stage configuration, selected declaration roots only, and set
skipLibCheck=false, noEmit=true. Production configs were not edited. Results are declaration diagnostics,
not counts of independent bugs or a cross-project quality score; the two projects have different
compiler flags, runtime surfaces and generation policies. Both normally enable skipLibCheck.

| Output | Declaration roots | Error diagnostics |
|---|---:|---:|
| [Native startup](<D:/mcmodDemo/NekoJS-mult/build/reports/probe-comparison/nekujs-startup-strict.json>) | 789 | 101 |
| [Native server](<D:/mcmodDemo/NekoJS-mult/build/reports/probe-comparison/nekujs-server-strict.json>) | 790 | 124 |
| [Native client](<D:/mcmodDemo/NekoJS-mult/build/reports/probe-comparison/nekujs-client-strict.json>) | 789 | 101 |
| [Original server](<D:/mcmodDemo/NekoJS-mult/build/reports/probe-comparison/probejs-server-strict.json>) | 1129 | 5894 |

The [reproduction helper](<D:/mcmodDemo/NekoJS-mult/build/reports/probe-comparison/check-declarations.cjs>)
uses the actual jsconfig, not a permissive replacement. Its final native-server rerun exited 1 as expected;
the earlier server diagnostic extraction had collected errors without failing the process. The later
startup/client/original batch also exited 1 as expected. These checks did not run user scripts.

Besides the three recipe defects, there are 28 unresolved generic-variable diagnostics. For example,
[NightConfig:147-154](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/electronwill/nightconfig/core/index.d.ts#L147-L154>)
uses T in accessors without a corresponding type-parameter scope. There are also genuine inheritance
projection conflicts, not just formatting differences: [LootJS ItemLootEntry:298-340](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/almostreliable/lootjs/core/entry/index.d.ts#L298-L340>)
projects item as Item while its projected ancestors expose item as boolean. Several fluent-return
errors cascade from this mismatch. Fix generic binding/substitution and Bean/heritage projection while
preserving actual runtime access; do not blanket-convert types to any or turn off Bean support.

### Latest log and dependency report

The [latest native log:300-306](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/logs/latest.log#L300-L306>)
records 4955 classes, 812 generated files and 8363ms of generation. A server overload warning immediately
follows: 13565ms / 271 ticks behind. This is a real performance symptom, though that single log does
not isolate all stall time to rendering. Capture/snapshot and render/write phases should be measured
separately before moving only thread-safe work off the server thread.

The [dependency report](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@special/dependencies.json>)
has zero unavailable classes but complete=false with one incomplete public-method set:
AbstractComputerBehaviour cannot enumerate methods because ComputerCraft IPeripheral is absent.
The original [partial class](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/com/simibubi/create/compat/computercraft/index.d.ts#L5-L9>)
also exports no methods, although it retains the parent and blockEntity field; the native
[partial class](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/simibubi/create/compat/computercraft/index.d.ts#L5-L8>)
is more conservative. Do not install fake ComputerCraft classes or mark the dump complete to hide this.
Zero unavailable-class entries do not prove that generated recipe imports are closed and valid.

The same log retains eight explicit unsupported Create/Mekanism profiles at
[64-71](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/logs/latest.log#L64-L71>),
a missing legacy JsonIO Mixin target at [42](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/logs/latest.log#L42>),
and EnderIO model/resource errors starting at [93](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/logs/latest.log#L93>).
These are different categories: bounded compatibility, a remaining helper integration gap, and external
asset loading respectively. No fatal startup failure was found in this snapshot. Do not interpret
successful startup as equivalent to complete addon semantics.

### Addon events and implicit calling conventions

- **P1: unsupported targeted events look callable.** Native
  [KeyBindJS events:100-106](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@side-only/client/events/index.d.ts#L100-L106>)
  expose handler-only KubeEvent callbacks; the original [events:234-245](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@side-only/client/events/index.d.ts#L234-L245>)
  have a target string and concrete KeyBindJSKeyEvent/ProxyEventJS payloads. The native
  [legacy handler:53-63](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/src/main/java/dev/latvian/mods/kubejs/event/EventHandler.java#L53-L63>)
  deliberately rejects these subscriptions with NEKO-4217. Adapt legacy targeting to native dispatch,
  or explicitly exclude/mark unsupported declarations until implemented. Adding an overload alone is wrong.
- **P1: implicit Rhino Context leaks into the script signature.** Native
  [conduit methods:32-36](<D:/mcmodDemo/NekoJS-mult/addons/nekujs/build/run/testClient/.neko_probe/typescript/@package/com/almostreliable/kubeio/event/index.d.ts#L32-L36>)
  require Context; the original [methods:58-62](<D:/Minecraft/.minecraft/versions/1.21.1-kubejs-test/.probe/@package/com/almostreliable/kubeio/event/index.d.ts#L58-L62>)
  omit it. Source inspection traced raw payload posting, direct Graal listener execution and HostAccess
  type mappings without argument insertion. This is source-verified missing call adaptation, not a live
  conduit-call reproduction. Use an addon-owned interop projection/injection seam; do not hardcode
  Rhino types into common or merely hide the required argument in declarations.
- Startup listing some client/server APIs is **not** itself a phase bug: native canApplyOn guards and
  declaration selection share that contract. Non-targeted selected LootJS, JEI, CommonAdded and EnderIO
  event names/payload class identities match the corresponding original surfaces.
- The final legacy ItemPredicate alias correctly preserves nested Ingredient identity. Its helper and
  native advancement ItemPredicate binding are different types. Missing literal completion for the
  supported '-' selector and the unverified GLFWInput binding difference are lower-priority follow-ups,
  not established gameplay failures.

## Recommended implementation order

1. Close recipe-emitted type references and preserve generic host arguments; add export/import checks.
2. Reproduce generic-scope and inherited Bean conflicts with small common fixtures, then fix shared producers.
3. Make targeted availability truthful and adapt implicit legacy arguments at the addon interop boundary.
4. Add opt-in real-mod declaration checks beyond canonical synthetic gates, then profile generation stalls.
5. Investigate JsonIO helper integration and EnderIO assets separately; retain truthful optional-dependency warnings.

This audit changed only this research note and disposable comparison reports/helpers. No fixes are claimed.
