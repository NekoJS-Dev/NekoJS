# Legacy NeoForge: a MixinJS path without an independent carrier MOD

Date: 2026-11-07. Research only; no implementation, build, launch, or executable probe was performed.

## Recommendation

**Do not conclude that NeoForge 1.21.1 requires a second visible MOD.** On the exact stack below, a passive manifest-marked **LIBRARY**, plus an early Mixin config plugin owned by the already-installed NekoJS JAR, is a credible source-backed candidate. The decisive distinction is that GAME cannot generally find PLUGIN resources through ordinary resource lookup, **but GAME can obtain class bytes from PLUGIN parent package loaders**. Mixin's actual bytecode-provider route uses the latter.

The recommended first experiment is therefore **NekoJS-owned existing config → NekoJS-owned plugin → plugin-returned mixin names → pre-admitted passive LIBRARY class bytes**, not another MOD, a late mod constructor, a new transformation service, or late mutation of a completed module layer.

This is **pinned-version internal feasibility, not runtime proof or a supported production contract**. It does not establish a zero-extra-JAR solution. It also does not mean that an early plugin can admit an arbitrary cache file after module construction.

| Requirement | Assessment for this candidate |
| --- | --- |
| No extra mod-list entry | Source-backed plausible: LIBRARY has no mod identity and is routed to PLUGIN, outside sorted mod entries. Must verify UI and loading diagnostics separately. |
| No separate generated JAR | Not established. The simplest candidate still uses a generated passive LIBRARY JAR. |
| No additional user-installed bootstrap | Plausible: NekoJS owns the plugin and emits the passive artifact; no separately installed early service is needed. This is a proposal, not current behavior. |
| Next launch acceptable | Required for newly generated packages/artifacts in this design; the artifact must exist before discovery. |
| Admit only the selected hash | Not delivered by the plugin alone: PLUGIN is already built when it runs. See immutable-selection options below. |
| Activate only the selected hash | Plausible through plugin-returned hash-qualified mixin names, even if multiple passive artifacts were admitted. |
| Stable public loader extension | No: public Mixin plugin methods and loader library admission exist, but their useful cross-layer combination depends on exact ModLauncher/SecureJarHandler internals. |
| Production supported today | No. Visibility, first-target timing, remapping, isolation, packaging, and Windows behavior need the experiment below. |

## Scope, status, and provenance

Only Minecraft 1.21.1 / NeoForge 21.1.227 / FML 4.0.42 is considered here. Effective launch dependencies are **ModLauncher 11.0.5, SecureJarHandler 3.0.8, and Fabric's Sponge Mixin 0.15.2+mixin.0.8.7**. The [NeoForge moddev configuration](<https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.227/neoforge-21.1.227-moddev-config.json>), lines 20–40, explicitly selects those versions. FML's own POM transitives are not sufficient to identify the effective stack.

Primary, version-pinned source archives used for the line citations below:

- **[F]** [FML 4.0.42 sources](<https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/4.0.42/loader-4.0.42-sources.jar>).
- **[M]** [ModLauncher 11.0.5 sources](<https://maven.neoforged.net/releases/cpw/mods/modlauncher/11.0.5/modlauncher-11.0.5-sources.jar>).
- **[J]** [SecureJarHandler 3.0.8 sources](<https://maven.neoforged.net/releases/cpw/mods/securejarhandler/3.0.8/securejarhandler-3.0.8-sources.jar>).
- **[X]** [Sponge Mixin 0.15.2+mixin.0.8.7 sources](<https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.15.2%2Bmixin.0.8.7/sponge-mixin-0.15.2%2Bmixin.0.8.7-sources.jar>).

Line numbers refer to the named member **inside the pinned source archive**, not a moving Git branch. FML/Mixin members were read from the existing extracted sources; ModLauncher/SecureJarHandler members were read directly from cached source archives without extraction. Guessed FML GitHub version tags were unavailable, and the ModLauncher moving branch differed from the pinned archive; neither is substituted as evidence.

The [implementation status](<D:/mcmodDemo/NekoJS-mult/docs/implementation/2026-11-07-mixinjs.md#L35-L49>) already records modern NeoForge's invisible GAMELIBRARY, immutable hash selection, restart requirement, and Windows loaded-JAR restrictions. That does **not** prove this older loader behaves identically. The [historical transparent-loading report](<D:/mcmodDemo/NekoJS-mult/docs/research/2026-11-07-mixinjs-transparent-loading.md#L187-L198>) is evidence to correct, not the conclusion adopted here.

The existing [legacy NekoJS mixin config](<D:/mcmodDemo/NekoJS-mult/src/main/resources-legacy/nekojs.mixins.json#L1-L34>) contains ordinary mixins under `com.tkisor.nekojs.mixin` and has **no plugin key**. The [mod metadata template](<D:/mcmodDemo/NekoJS-mult/src/main/templates/META-INF/neoforge.mods.toml#L17-L18>) already registers that config. No existing NekoJS IMixinConfigPlugin/connector implementation was found in the inspected source trees. “Existing config with a future owned plugin” is therefore accurate; “already implemented plugin” is not.

## Evidence matrix

| Question | Primary source, method, exact lines | Finding and limit |
| --- | --- | --- |
| Loose JAR without mod TOML? | [F], `net/neoforged/fml/loading/moddiscovery/readers/JarModsDotTomlModFileReader.java`, `createModFile` 39–54; `getModType` 58–65 | A manifest `FMLModType: LIBRARY` is enough. A completely unmarked loose JAR returns null. “No mods.toml” does not mean “no admission marker.” |
| Independent mod identity? | Same member, `manifestParser` 68–84, `DefaultModFileInfo.getMods` 103–106; [F] `net/neoforged/fml/loading/moddiscovery/ModValidator.java` constructor 35–39, `stage1Validation` 47–66 | Manifest-only LIBRARY has an empty mod list; candidatePlugins are separate from MOD/GAMELIBRARY candidates and are not subjected to normal mod validation. |
| Where does LIBRARY go? | [F] `ModValidator.java` 65–75 | Libraries become PLUGIN resources; ordinary mods become GAME resources. This is not itself a visibility prohibition. |
| Is PLUGIN a GAME parent? | [M] `cpw/mods/modlauncher/api/IModuleLayerManager.java` enum 26–30; `cpw/mods/modlauncher/ModuleLayerHandler.java` `buildLayer` 74–95 | `GAME(PLUGIN, SERVICE)` is explicit. Resolution uses parent configurations and constructs the loader with parent layer objects. |
| Does the real GAME loader use these parents? | [M] `cpw/mods/modlauncher/TransformationServicesHandler.java` `buildTransformingClassLoader` 53–56; `cpw/mods/modlauncher/TransformingClassLoader.java` 43–52 | Yes: the production construction path supplies the constructed GAME configuration and parents. Do not mistake the alternate constructor at lines 37–39 for the actual launch route. |
| Can classes reach PLUGIN? | [J] `cpw/mods/cl/ModuleClassLoader.java` constructor 102–137, `loadClass` 205–223 | Readable parent automatic-module packages populate `parentLoaders`; missing local packages delegate there. Automatic modules and unique packages matter. |
| Can Mixin obtain those class bytes? | [J] same member, `getMaybeTransformedClassBytes` 315–338; [M] `TransformingClassLoader.java` 64–66; `LaunchPluginHandler.java` 105–106; [X] `org/spongepowered/asm/launch/MixinLaunchPluginLegacy.java` `getClassNode` 216–244; `org/spongepowered/asm/mixin/transformer/MixinInfo.java` `loadMixinClass` 1300–1312 | Parent package loaders are asked for the class resource. Mixin first uses `transformerLoader.buildTransformedClassNodeFor`, which reaches this route. That is the missing counterexample to the historical categorical rejection. |
| Can ordinary GAME resource lookup read library JSON? | [J] `ModuleClassLoader.java` `getResource` 245–257, `findResourceList` 273–287; [X] `org/spongepowered/asm/service/modlauncher/MixinServiceModLauncher.java` 302–303 | Generally no: own roots first, then fallback; GAME fallback is BOOT, not a recursive PLUGIN resource search. Class reachability and arbitrary resource reachability are different. |
| Plugin before target application? | [X] `org/spongepowered/asm/mixin/transformer/MixinConfig.java` `onSelect` 788–811, `prepare` 852–878; `org/spongepowered/asm/mixin/transformer/MixinProcessor.java` `select` 448–464 and `prepareConfigs` 527–577 | Config selection invokes plugin onLoad; preparation then invokes plugin getMixins before normal application. This is a real earlier hook than the mod constructor, but not before module-layer construction or all possible service-stage classes. |
| Too late to register with FML? | [F] `net/neoforged/fml/loading/mixin/DeferredMixinConfigRegistration.java` 41–51; `FMLMixinPlatformAgent.java` 21–25 | FML seals its registration queue during platform prepare. Late adds throw `Too late to add mixin configs!`. This queue is unnecessary when augmenting an already registered NekoJS-owned config. |
| Can arbitrary cached paths be added then? | [M] `ModuleLayerHandler.java` `addToLayer` 64–71; `Launcher.java` 81–103 | A completed layer rejects additions with `Layer already populated`. By plugin selection time PLUGIN and GAME already exist. A plain URLClassLoader is not a registration mechanism for these package maps. |

## Concrete candidate: passive library, owned config, early plugin

1. NekoJS generates bytecode during the current running instance, but activation waits for a fresh launch. Do not compile at the first target transformation, load Minecraft classes from the plugin, or initialize Graal/runtime ownership merely to enumerate mixins.
2. Before that launch's discovery, place a generated library in the normal direct mods directory with `FMLModType: LIBRARY` and an explicit unique `Automatic-Module-Name`. It must contain **no mod TOML, no MixinConfigs/MixinConnector attributes, no service descriptors, and no executable bootstrap**. It supplies bytes, not registration authority.
3. Keep config registration in NekoJS. A lightweight NekoJS-owned config plugin, outside the reserved mixin package, reads an immutable plan/index and selected hash, validates compatibility and artifact membership, and returns relative class names from `getMixins()`. No dynamic library JSON lookup is needed.
4. Generate classes in hash-qualified subpackages of the config's package, e.g. relative `generated.habc123.ExampleMixin` under `com.tkisor.nekojs.mixin`. Do not put generated classes in the exact same package as existing NekoJS classes. Every admitted revision needs a unique module name **and every exact Java package must avoid collisions**, including helpers. [J] `cpw/mods/jarhandling/JarMetadata.java` 48–70 honors `Automatic-Module-Name` for non-modular libraries; filename derivation alone is insufficient.
5. Let Mixin fetch bytes through its normal ModLauncher bytecode provider. Do not directly `Class.forName` a mixin to “make it visible”; Mixin consumes ASM and marks non-loadable mixins invalid ([X] `MixinInfo.java` 855–874). Runtime dispatcher/helper ownership should remain explicit; bytecode visibility does not establish all linkage behavior.

The fixed config package is important: [X] `MixinConfig.prepareMixins` 910–934 prepends the config package to every returned name. Returning arbitrary fully-qualified names is not an independent-package escape hatch. Also, plugin-returned mixins pass `ignorePlugin=true` at lines 875–878: do not rely on `shouldApplyMixin` as the sole safety filter for this list. Filter the returned list itself, including client/server distinction, selected revision, disabled state, and compatibility.

If library metadata or a refmap must be read explicitly, a NekoJS-owned reader can locate the PLUGIN layer via [M] `Launcher.findLayerManager` 118–120 and `IModuleLayerManager.getLayer` 23–24, then use the actual library module loader or module reader. This is materially different from assuming TCCL will see root JSON. A plugin-returned refmap **filename** alone still goes through Mixin's resource service and does not solve that blind spot. Prefer an experiment with runtime-namespace bytecode and no refmap first; test production remapping separately.

ModLauncher also sets PLUGIN's fallback loader to GAME after GAME construction ([M] `TransformationServicesHandler.java` 53–56). Thus “PLUGIN can never reach GAME” is another unsafe absolute statement. It does not establish that arbitrary library-owned helpers with Minecraft dependencies are safe, particularly before GAME construction or through module-aware lookup.

## Timing: what the early hook does and does not buy

The source-backed order is:

- FML discovers library/mod candidates and emits PLUGIN resources.
- ModLauncher constructs PLUGIN, completes scanning, gathers GAME contents, offers **GAME** contents to launch plugins, then constructs GAME and sets TCCL ([M] `Launcher.run` 81–103).
- FML's platform agent registers its queued configs during Mixin platform prepare ([F] `FMLMixinPlatformAgent.prepare` 21–25).
- Mixin selects configs and invokes onLoad; prepares static mixins; invokes getMixins and validates the additional mixins; then applies the selected configuration to eligible transformations ([X] `MixinConfig.java` 788–878; `MixinProcessor.java` 448–464, 527–577).

Consequently, the plugin is early enough in the source control flow to supply extra mixin names before ordinary application from that config. It is **not** a retroactive transform facility. Classes loaded by early services, plugin dependencies, remapping/plan discovery, or another initialization path may already be defined. Reentrant target loading during plugin setup is particularly dangerous. The exact first targeted Minecraft class must be measured in the proposed experiment, not inferred from “earlier than constructor.”

Adding a new config from onLoad is a weaker proposal than returning names: FML's queue is sealed, while Mixin's pending-config selection is phase/state sensitive. [X] `MixinProcessor.checkSelect` 436–445 only reselects unvisited configs under its stated state conditions. This report does not treat arbitrary late `Mixins.addConfiguration` as equivalent to augmenting the existing config.

### Mixin connector inside the ordinary NekoJS JAR

A connector is also a real early candidate, not categorically excluded by FML's synthetic container. [M] `Launcher.java` 93–102 offers GAME SecureJars to the launch plugins. [X] `org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncherEx.java` 39–46, 82–88 wraps SecureJars; `org/spongepowered/asm/launch/platform/MixinPlatformAgentDefault.java` 57–60 reads the connector manifest attribute; `MixinPlatformManager.java` 177–197 injects connectors. NekoJS is an ordinary GAME mod, so this is a source-backed way for its manifest to participate. FML's own `FMLMixinContainerHandle.getAttribute` returning null does **not** prove all ordinary-mod manifests are ignored.

However, a connector is not earlier than discovery/layer population either, and its startup ordering and dependency isolation need runtime verification. Prefer the already-registered config/plugin for the first experiment: no extra config-discovery dependency, no FML-queue mutation, and clear ownership. Do not put a connector in the passive generated library; PLUGIN contents are not the GAME scan-results list offered by `Launcher.run`.

## Early services, nesting, and self-location

**Root service descriptor is not a harmless addition to an ordinary mod.** [F] `net/neoforged/fml/loading/ModDirTransformerDiscoverer.java` 58–66, 69–92 scans direct mods JARs. `TransformerDiscovererConstants.java` 35–68 recognizes exactly transformation services, candidate locators, mod-file readers, dependency locators, graphics bootstrappers, and immediate-window providers. It promotes the entire matching outer artifact to early SERVICE discovery. Saying “any service” would be incorrect: later language-loader/coremod services are a different case.

[F] `LaunchContext.java` 45–65, 97–104 indexes existing layer locations. `moddiscovery/locators/ModsFolderLocator.java` 50–71 uses pipeline `addPath`; `moddiscovery/ModDiscoverer.java` 178–200 rejects an already located primary path. Merely giving the service-promoted NekoJS JAR a self-locator that calls addPath does **not** restore ordinary mod admission.

But this is not proof of universal impossibility: [F] `net/neoforged/neoforgespi/locating/IDiscoveryPipeline.java` 46–73 exposes `addJarContent`, `readModFile`, and `addModFile`. Their implementation in `ModDiscoverer.java` 203–239, 267–282 does not use the same located-path check. An early custom locator could explicitly resubmit its own contents. The reader does not reject ordinary TOML simply because a service descriptor exists. This is an **admission-level counterexample**, not a working single-JAR bootstrap: duplicate module/package resolution, ancestor SERVICE identity versus GAME identity, entrypoint lookup, and premature dependencies remain unresolved. [F] `ModJarMetadata.computeDescriptor` 38–46 includes all packages/providers; `net/neoforged/fml/javafmlmod/FMLModContainer.java` 42–63 finds the named game module and loads its entrypoint. A one-JAR self-locator must prove those downstream properties, not just bypass one guard.

**Nesting an early service does not fix automatic discovery.** The early scanner is depth one, not recursive. [F] `ModDiscoverer.java` 50–57 snapshots the candidate-locator/reader/dependency-locator services before normal discovery and JarJar at 101–113. `moddiscovery/locators/JarInJarDependencyLocator.java` 38–66 selects embedded contents and calls `readModFile` with a parent. `readers/NestedLibraryModReader.java` 25–44 admits an otherwise unmarked nested library as LIBRARY, hence PLUGIN—not SERVICE. A descriptor inside that library does not rerun the earlier service scan or refresh the captured locator lists.

Do not overgeneralize: [F] `LaunchContext.loadServices` 67–95 visits currently completed layers. Later `LanguageProviderLoader.java` 41–53 and `FMLServiceProvider.java` 124–148 can discover language/coremod services after PLUGIN exists. The [FML 4.0.42 changelog](<https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/4.0.42/loader-4.0.42-changelog.txt>) explicitly describes Java coremods in separate LIBRARY JARs, including JarJar. That is not evidence that nested transformation services are early-discovered.

## Immutable cache, Windows, selection, and NekoJS absence

There are two different selection contracts:

**A. Activate one hash, allow multiple inert libraries to be admitted.** NekoJS can emit each revision as a different immutable passive library in the direct mods directory. All revisions use unique module names and exact packages. On the next launch the owned plugin selects exactly one compatible hash and returns only its mixins. No loaded artifact is replaced. A small plan/pointer is not a loaded JAR and can be published separately. Stale libraries are still admitted and indexed, even though inactive: do not describe this as immutable *single-artifact admission*. It trades extra modules/startup work/disk for avoiding another bootstrap. Cold-process cleanup is necessary to bound growth; deleting already-open libraries from the next plugin is not a Windows-safe cleanup strategy.

**B. Admit only one selected cache artifact.** A NekoJS config plugin alone is too late. Cache files outside the scanned mods directory are not magically discoverable, and all matching passive libraries already in that directory can be admitted before plugin selection. This requires cold external staging, a genuinely earlier selector/locator, or a separately proven same-JAR early bootstrap. A hidden automatically generated bootstrap LIBRARY/SERVICE is still a bootstrap artifact, even if not a MOD and not manually installed. Do not silently claim the stronger contract while implementing A.

Option A is the smallest source-backed no-extra-MOD/no-additional-manual-bootstrap experiment. Option B may be operationally preferable but adds a real early-selection problem. Neither requires replacing the NekoJS JAR or an open generated JAR. No Windows runtime test was performed here; existing implementation notes establish the reason to avoid loaded-JAR replacement, not this design's complete behavior.

For **NekoJS absence**, a passive artifact has no config/connector/service/entrypoint to activate its mixins. Source logic therefore predicts inert bytes rather than an independent executable mod. This must be tested. Library module resolution still happens even without NekoJS; malformed module names, duplicated packages, wrong Java class versions, or foreign service metadata can fail startup before any plugin safeguard. Absence safety is conditional on validating exactly what is emitted, not on adding a mod dependency that a metadata-less library cannot declare. For disabled/corrupt/stale plans, return no generated mixins and report a bounded diagnostic; do not accidentally break the required static NekoJS config.

## Remapping, compatibility cost, and production limits

- This works, if proven, because ModLauncher 11.0.5 passes both parent layers into the real transforming loader and SecureJarHandler 3.0.8 exposes their package resources through its bytecode route. Treat that chain as a version-specific compatibility adapter and rerun the probe for every supported loader update. Public plugin interfaces do not guarantee those internals.
- Bytecode and plan identity must include Minecraft/NeoForge/FML/Mixin versions, runtime namespace/mapping identity, side, NekoJS ABI/dispatcher contract, generator version, and content hash. A source-compatible Java name in a development environment is not evidence of production selector/descriptors being correct.
- A library is not an ordinary GAME mod and does not obtain all mod scanning, config registration, dependency, remapping, access-transformer, or runtime ownership semantics automatically. Test helpers/accessors, referenced descriptors, interfaces, and Java access separately from the trivial injection case.
- Library root JSON/refmaps are not generally visible through GAME TCCL. `getRefMapperConfig()` supplies a name, not a new resource provider. A verified bytecode namespace or explicitly designed refmap bridge is required; no production mapping support is proven here.
- Keep plugin dependencies to startup-safe Java/Mixin/loader interfaces. Calling the normal NekoJS runtime, touching a target class, or evaluating scripts during plugin selection can trigger premature loading/reentrancy. No arbitrary hot reload of applied mixins is promised.
- Immutable filenames are not enough: repeated automatic module names and split packages can make coexistence fail during resolution. Prefix hash package/module segments with a legal identifier character; validate plan-to-artifact hash and module metadata before publication, not after loading.

## What the earlier report got wrong or overstated

1. **LIBRARY class visibility was rejected too broadly.** Its resource warning is substantially right, but class bytes use the parent package route. GAME's explicit PLUGIN parent, actual production loader constructor, and `getMaybeTransformedClassBytes` are decisive omitted evidence.
2. **No stable GAME-admission API is not proof that a visible MOD is the only path.** The generated mixins need bytecode reachability, not necessarily GAME ownership of their source archive. A passive PLUGIN library plus NekoJS-owned config is a materially different proposal.
3. **FML's synthetic container is not all Mixin containers.** GAME SecureJars are offered to Mixin and can expose connector manifests. A null attribute on FML's synthetic handle cannot disprove the NekoJS-owned connector option.
4. **The deferred queue is a boundary, not a universal ban on early plugin augmentation.** Returning classes from an already-registered config avoids that queue. Conversely, the plugin cannot reopen completed module layers or fix targets already defined.
5. **SERVICE self-location and nested services need narrower claims.** Default addPath admission is blocked, but explicit addJarContent/addModFile is an admission-level escape requiring downstream proof. Nested early services are too late for the early lists; later language/coremod service consumers are not.
6. **Modern GAMELIBRARY success does not transfer automatically.** FML4's metadata-less GAMELIBRARY path still enters normal mod-list/config assumptions; LIBRARY is the relevant counterexample here. Do not relabel a legacy library as the already-proven modern implementation.

## Falsifiable minimal experiment — proposed, not executed

Use an isolated fixture/profile, never the working production mods directory. Do not modify or replace existing research/build outputs. Keep each step separately observable.

1. Pin exactly the versions above and an ordinary NekoJS-equivalent fixture MOD with one registered config and a startup-safe plugin in its own JAR. Start with a harmless fixture target which is not touched by bootstrap code. Record class/module/loader identity and timestamps at plugin construction, onLoad, getMixins, first target transform, target definition, and mod construction.
2. Add one passive `FMLModType: LIBRARY` JAR without TOML/services/connector/config attributes. Give it a unique automatic module name and a unique hash subpackage under the owned config package. Put one trivial mixin there; it references no library-owned runtime helper and adds an observable constant result to the fixture target.
3. Before returning that relative name from getMixins, record: GAME/PLUGIN module membership; GAME TCCL ordinary JSON/resource lookup; PLUGIN loader JSON/class lookup; Mixin bytecode-provider class-node lookup. **Expected:** ordinary GAME JSON lookup fails, PLUGIN lookup succeeds, and Mixin obtains the library mixin ClassNode through the transformer route. Do not define the mixin class to test lookup.
4. Assert the target's first relevant transform sees the returned mixin and its behavior changes before first target definition. Assert the normal mod list has only the fixture mod identity, with the library absent as a MOD. Assert the library is visibly a PLUGIN module in the separate diagnostics. Failure in any assertion falsifies the candidate at that stage.
5. Restart with the owner mod removed but the passive library retained. Assert startup and target baseline behavior. Repeat with a disabled plan and a missing/stale/wrong-version hash; no generated mixin may activate. Validate that absence/corruption cannot introduce a config on its own.
6. Add a second passive hash with a different module and package. Select A, then B, in separate fresh processes. Assert only selected behavior and record that both libraries are admitted. Add an intentional duplicate package/module negative control and verify that the test detects resolution failure rather than silently claiming safe coexistence.
7. Only after this passes, target the earliest required real Minecraft class on both client and dedicated server. Repeat in a normal packaged production launch, not just development mappings. Add one realistic member selector, descriptor, accessor/helper reference, and remapping case. Measure whether any target was loaded before plugin setup.
8. On Windows, emit a new immutable artifact while the old launch is alive, atomically publish the plan after the artifact is complete, and restart without replacing/moving the open artifact. Verify hash selection, incomplete-write rejection, rollback, stale-file behavior, and cold cleanup. Run a bounded accumulation case to measure admission cost.

A bounded isolated Java layer/Mixin-provider probe could establish steps 2–3 without a game, but it would not prove the actual FML launch order, UI absence, remapping, or first real target timing. It is proposed only; no probe was implemented this turn.

### Exact remaining gaps

- No runtime proof that this precise library/provider/plugin combination prepares and applies a generated mixin successfully.
- No measured first-target ordering for the intended Minecraft targets, including reentrant loads and third-party early services.
- No production remapping/refmap bridge, helper linkage, accessor, client/server, or dedicated-server compatibility proof.
- No verified actual mod-list UI/diagnostic distinction and no owner-absent launch proof.
- No Windows execution, bounded stale-library cleanup implementation, or module-accumulation measurements.
- No public supported path for arbitrary in-memory classes or late package admission; no zero-extra-JAR solution established.
- No verified single-JAR SERVICE+self-locator+ordinary-mod coexistence; admission APIs alone do not establish that stronger bootstrap design.
- No claim that the existing legacy implementation now supports this. Only this report was authored; existing dirty production work was preserved.

## Verification performed

Read repository context/conventions and the existing implementation/historical research; inspected pinned extracted FML/Mixin source and cached ModLauncher/SecureJarHandler source archives; checked version selection against the NeoForge configuration; obtained an independent read-only FML service/admission trace. No game launch, Gradle build, source/test modification, or executable experiment was run. The research agent authored only this report; the lead separately added a supersession note to the historical report.

[F]: https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/4.0.42/loader-4.0.42-sources.jar
[M]: https://maven.neoforged.net/releases/cpw/mods/modlauncher/11.0.5/modlauncher-11.0.5-sources.jar
[J]: https://maven.neoforged.net/releases/cpw/mods/securejarhandler/3.0.8/securejarhandler-3.0.8-sources.jar
[X]: https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.15.2%2Bmixin.0.8.7/sponge-mixin-0.15.2%2Bmixin.0.8.7-sources.jar
