# Transparent generated-carrier loading for MixinJS/NekoJS

Date: 2026-11-07
Status: research only; no production implementation, no runtime verification
Scope: NeoForge FML11 (MC 26.1.2 / 26.2.0), NeoForge FML4 (MC 1.21.1), Fabric Loader 0.19.3

> Historical snapshot, superseded in part by later work on the same date. The categorical legacy LIBRARY visibility rejection below is too strong: the [legacy NeoForge follow-up](2026-11-07-mixinjs-legacy-neoforge-no-carrier.md) traces Mixin class-byte lookup through PLUGIN parent package loaders. The [Fabric follow-up](2026-11-07-mixinjs-fabric-no-carrier.md) evaluates a NekoJS-owned config plugin and internal Knot classpath admission instead of relying only on preLaunch. These are source-backed candidates, not runtime acceptance. Modern managed GAMELIBRARY implementation and its limited native verification are recorded in the [implementation log](../implementation/2026-11-07-mixinjs.md). The original findings below are retained as historical evidence, not the current recommendation.

## Question

Can NekoJS generate its MixinJS carrier JAR and have the loader discover it without the carrier appearing as a normal, user-visible mod? The current design writes a standard carrier with `META-INF/neoforge.mods.toml` or `fabric.mod.json` into the instance `mods/` directory. On NeoForge that carrier is a `MOD` file and therefore appears in the mod list. This document asks:

1. Which loader phases can admit a generated JAR, and from where?
2. Can an ordinary mod, or a nested (Jar-in-Jar) plugin, provide the discovery service that admits it?
3. Can admission happen in the same launch that generated the JAR?
4. Which routes are public API, which are internal/version-specific, and which are only code-level feasibility?

The answer is based on loader/Mixin source code and shipped artifacts, and builds on the earlier survey in `docs/research/2026-11-06-mixinjs-primary-sources.md`. It is not a runtime proof; no build or game launch was performed.

## Bottom line

- **The current standard carrier is visible on every target.** A NeoForge `MOD` file appears in `LoadingModList.getModFiles()` and therefore in the mod list. The same is true for the 1.21.1 FML4 `MOD`/`GAMELIBRARY` path and for a Fabric mod with `fabric.mod.json`.
- **FML11 (26.1.2 / 26.2.0) has a genuinely invisible, auto-managed route: a plain `GAMELIBRARY` JAR in `mods/`.** Put `FMLModType: GAMELIBRARY` and `MixinConfigs: <config>.json` in the manifest, omit `neoforge.mods.toml`, and the built-in `ModsFolderLocator` admits it as a game library. Its classes/resources are placed in the game module layer and are visible to the transforming class loader; its manifest `MixinConfigs` are registered through `FMLModFileContainerHandle`; it does not appear in the mod list. MixinExtras NeoForge 0.5.4 is a real artifact with this exact manifest shape (normally nested or declared by NeoForge rather than dropped into `mods/` by hand), which is strong evidence that the route is intentional rather than accidental.
- **The engine still cannot generate and admit that JAR in the same launch by itself.** On FML11, discovery runs before any ordinary mod is constructed. A `GAMELIBRARY` JAR must already exist at discovery time (or be created by a discovery-time service). The practical flow is two-launch: NekoJS writes the carrier at the end of launch N, and the loader admits it at launch N+1.
- **A custom early-service locator can admit a carrier from outside `mods/`, and can generate it during the same discovery pass, but it is a second installed artifact.** Such a locator is loaded in the `FML Early Services` class loader, before mod discovery. It can call `IDiscoveryPipeline.addPath`/`addModFile` and admit a cached carrier. It cannot call NekoJS engine classes unless it bundles or class-loads them itself, because the ordinary mod class loader does not exist yet. This is a public SPI route, but the locator is still an installed JAR and the carrier metadata convention is version-specific.
- **FML4 (1.21.1) has no invisible standard-JAR route.** A `GAMELIBRARY` is treated as a candidate mod, validated, sorted into the mod list, and requires mod metadata. A `LIBRARY` plugin is invisible, but it is placed in the plugin layer, not the game/transforming class loader, and FML4 has no per-file manifest `MixinConfigs` scan. Its `DeferredMixinConfigRegistration` is an internal service-layer hook, not a standard carrier admission API.
- **Fabric Loader 0.19.3 has no supported route.** `preLaunch` runs after discovery, `freeze()`, access-widener loading, Mixin initialization and `finishMixinBootstrapping()`. `FabricLoader` has no add-mod/add-classpath/add-mixin API. Only the internal `FabricLauncher.addToClassPath` plus Mixin's public `Mixins.addConfiguration` is technically reachable from `preLaunch`, and even that has post-`DEFAULT` config-selection and access-widener limitations.

### Recommendation matrix

| Target | Current standard carrier | Strongest supported recommendation | Invisible? | Evidence class |
|---|---|---|---|---|
| NeoForge 26.1.2 / 26.2.0 (FML11) | visible `MOD` | Auto-managed plain `GAMELIBRARY` JAR in `mods/`, generated for the next launch; optional early-service locator only if the cache must live outside `mods/` | Yes, not in mod list | public SPI + loader source + shipped MixinExtras artifact; no runtime proof |
| NeoForge 1.21.1 (FML4) | visible `MOD` | Visible standard `MOD` carrier; defer invisibility | No | loader source; no standard route |
| Fabric 0.19.3 | visible mod | Visible standard `fabric.mod.json` carrier; defer invisibility | No | loader source; no supported route |

## Terms and evidence standard

- **Public API**: a class/interface in a documented SPI package, loaded through `ServiceLoader`, or a public factory intended for third parties. An example is `net.neoforged.neoforgespi.locating.IModFileCandidateLocator`.
- **Internal / version-specific route**: an implementation class or manifest convention that is not a stable third-party contract, even if public in Java. An example is `FMLModType: GAMELIBRARY`.
- **Feasibility**: the code path exists, but there is no runtime test and no guarantee about edge cases, obfuscation, mapping, or future versions.
- **Visible**: the artifact is reported as a mod (mod list / mod metadata). **Invisible**: the artifact is not a mod; it may still be a file on disk.

All line numbers refer to the extracted source jars under `build/research-loader-transparent/` and the public repositories at the cited tags. The key FML11 files are byte-identical between loader 11.0.13 and 11.0.16 (SHA-256 compared), so the behavior below is stable across the two pinned NeoForge lines.

## NeoForge FML11: the `GAMELIBRARY` carrier

### Versions and source basis

The repository pins NeoForge `26.1.2.71` and `26.2.0.57` in `versions/26.1.2/gradle.properties` and `versions/26.2.0/gradle.properties`. The published POMs resolve those to FancyModLoader 11.0.13 and 11.0.16 respectively, Mixin `0.17.3+mixin.0.8.7`, and MixinExtras NeoForge 0.5.4. The local source extraction is FancyModLoader 11.0.16; 11.0.13 was fetched and compared.

Primary sources:

- `https://github.com/neoforged/FancyModLoader/blob/11.0/loader/src/main/java/net/neoforged/fml/loading/EarlyServiceDiscovery.java`
- `https://github.com/neoforged/FancyModLoader/blob/11.0/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModDiscoverer.java`
- `https://github.com/neoforged/FancyModLoader/blob/11.0/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/locators/ModsFolderLocator.java`
- `https://github.com/neoforged/FancyModLoader/blob/11.0/loader/src/main/java/net/neoforged/fml/loading/mixin/MixinFacade.java`
- `https://github.com/neoforged/FancyModLoader/blob/11.0/loader/src/main/java/net/neoforged/fml/loading/mixin/FMLModFileContainerHandle.java`
- `https://github.com/neoforged/FancyModLoader/blob/11.0/loader/src/main/java/net/neoforged/fml/loading/mixin/FMLMixinService.java`

The FancyModLoader repository tags only `major.minor` (for example `11.0`), not patch versions; the exact patch source is the corresponding source jar from Maven.

### Discovery happens before any ordinary mod runs

`FMLLoader.create()` performs early-service loading and mod discovery before it builds the transforming class loader, loads mod classes, or runs mod constructors:

- `net/neoforged/fml/loading/FMLLoader.java:615-621`: `loadEarlyServices(startupArgs)` scans for early-service JARs and appends them as the `"FML Early Services"` class loader.
- `FMLLoader.java:630-660`: creates a `URLClassLoader` over the early-service content roots, sets it as the thread context class loader, and at line 652 adds every content root to `locatedPaths`.
- `FMLLoader.java:662-673`: runs discovery with the built-in locators `GameLocator`, `InDevFolderLocator`, `InDevJarLocator`, and `ModsFolderLocator`, plus any service-provided locators.
- `FMLLoader.java:368-407`: only after discovery does it construct `MixinFacade`, build the game module layer, create the transforming class loader, set it as TCCL, and call `mixinFacade.finishInitialization(...)`.

The built-in locators (`GameLocator`, `InDevFolderLocator`, `InDevJarLocator`, `ModsFolderLocator`) are constructed and passed explicitly by `FMLLoader`; they are not discovered as services. Only an early-service JAR, or a JAR already on the unclaimed launch classpath, can add another locator/dependency locator before discovery.

Consequently, no NekoJS engine code that runs as an ordinary mod can influence discovery of its own launch. A carrier generated while the game is running is necessarily for a later launch.

### How a plain JAR becomes a game library

`ModsFolderLocator` is a built-in locator, not an early service. It lists the top level of `mods/` and calls `pipeline.addPath(...)` for every non-empty `.jar`:

- `net/neoforged/fml/loading/moddiscovery/locators/ModsFolderLocator.java:50-71`: `Files.list(modFolder)`, `*.jar`, `pipeline.addPath(file, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ALWAYS)`.
- `net/neoforged/fml/loading/moddiscovery/ModDiscoverer.java:190-213`: `addPath` first calls `launchContext.addLocated(primaryPath)`; an already-located path is skipped. A new path is read as `JarContents` and passed to `addJarContent`.
- `ModDiscoverer.java:228-288`: `addJarContent` runs every `IModFileReader`; `addModFile` accepts the resulting `ModFile`.
- `net/neoforged/fml/loading/moddiscovery/readers/JarModsDotTomlModFileReader.java:39-55`: if `META-INF/neoforge.mods.toml` exists, the file is parsed as a normal mod file; otherwise, if the manifest has `FMLModType`, it is parsed by `manifestParser` with that type; otherwise `createModFile` returns `null` and the JAR is not a mod file.
- `JarModsDotTomlModFileReader.java:72-88`: `manifestParser` returns a `DefaultModFileInfo` with no mods and no `[[mixins]]` list. The manifest is still the source of `FMLModType` and of Mixin's `MixinConfigs` attribute.

`ModFile` then sets its type from the manifest or defaults to `MOD`:

- `net/neoforged/fml/loading/moddiscovery/ModFile.java:63`: `TYPE = Attributes.Name("FMLModType")`.
- `ModFile.java:221-224`: `parseType` returns `Type.valueOf(manifest FMLModType)`, else `Type.MOD`.
- `ModFile.java:73-110`: the constructor resolves `modFileInfo`, the module name, and `mixinConfigs`. For a manifest-only file, `modFileInfo` is a `DefaultModFileInfo` and `getMixinConfigs` is not useful; the manifest `MixinConfigs` is handled separately by Mixin, as described below.

`ModSorter` places the file:

- `net/neoforged/fml/loading/ModSorter.java:57-71`: `LIBRARY` goes to plugin content, `GAMELIBRARY` to game-library content, everything else to game content.
- `net/neoforged/fml/loading/LoadingModList.java:47-68`: `plugins` and `gameLibraries` are separate lists; `modFiles` is the list cast to `ModFileInfo`. A game library is therefore not in the mod list and does not need a `ModFileInfo` cast.
- `net/neoforged/fml/loading/FMLLoader.java:140-166`: `DiscoveryResult.allContent` is plugin + game + game-library; `allGameContent` is game + game-library.
- `FMLLoader.java:380-397`: the game module layer is built from `allGameContent`, and the transforming class loader is created over it. A `GAMELIBRARY` is therefore in the same module layer as ordinary mods and Minecraft, and its classes/resources are reachable through the transforming class loader (TCL).

### Mixin registration for a game library

The ordinary mod path is not the only Mixin registration path. For game libraries and plugins, `MixinFacade` exposes a container handle to Mixin:

- `net/neoforged/fml/loading/mixin/MixinFacade.java:173-179`: after registering mod `[[mixins]]`, for every plugin and every game library it calls `service.addMixinContainer(new FMLModFileContainerHandle(file))`.
- `net/neoforged/fml/loading/mixin/FMLModFileContainerHandle.java:13-28`: the class comment says the handle exists "purely to allow it scanning for manifest attributes so game libraries/plugins can also contribute Mixins. One example for this is mixinextras." `getAttribute(name)` returns `modFile.getContents().getManifest().getMainAttributes().getValue(name)`.
- `net/neoforged/fml/loading/mixin/MixinFacade.java:44-74`: the constructor calls `MixinBootstrap.init()` at line 52, which prepares the Mixin platform before the TCL exists. `finishInitialization` later asserts TCCL is the `TransformingClassLoader`, adds mod configs, advances the environment through `INIT` and `DEFAULT`, and calls `getPlatform().inject()`.
- Mixin 0.17.3 `org/spongepowered/asm/launch/platform/MixinPlatformAgentDefault.java:36-61`: `prepare()` reads the manifest attributes `Compatibility`, `MixinConfigs`, `TokenProviders`, and `MixinConnector`. Each comma-separated `MixinConfigs` entry ending in `.json` becomes `manager.addConfig(config, handle)`, which reaches `Mixins.addConfiguration(config, source)`.
- Mixin 0.17.3 `org/spongepowered/asm/launch/platform/MixinPlatformManager.java:129-148`: if the platform is already prepared, `addContainer` prepares the container immediately. `MixinFacade`'s constructor prepared the platform, so the game-library handles added at lines 173-179 are prepared during `inject()`, with the TCL in place.
- `net/neoforged/fml/loading/mixin/FMLMixinService.java:211-225`: Mixin config resource lookup checks the in-memory `mixinConfigContents` map first, then `Thread.currentThread().getContextClassLoader().getResourceAsStream(name)`. A game-library JAR is in the game layer, so its config JSON is found through the TCL.

The result is a coherent, no-Mixin-internals path: the loader supplies a handle; Mixin's own platform agent reads the standard `MixinConfigs` manifest attribute; the config and mixin classes are loaded from the game layer. The only non-Java contract is the manifest keys.

### The MixinExtras proof of artifact shape

The shipped `mixinextras-neoforge-0.5.4.jar` (cached at `C:/Users/11515/.gradle/caches/modules-2/files-2.1/io.github.llamalad7/mixinextras-neoforge/0.5.4/3cb0fec08c25e18fddf797fbdc1b9db83dc4e92f/mixinextras-neoforge-0.5.4.jar`) has this manifest:

    Manifest-Version: 1.0
    MixinConfigs: mixinextras.init.mixins.json
    FMLModType: GAMELIBRARY
    Automatic-Module-Name: mixinextras.neoforge

Its entries include `META-INF/MANIFEST.MF`, `mixinextras.init.mixins.json`, and `META-INF/services/javax.annotation.processing.Processor`; there is no `META-INF/neoforge.mods.toml`. This is a real-world artifact using `FMLModType: GAMELIBRARY`, a manifest `MixinConfigs`, and no mod metadata. It is the same convention recommended here. The artifact demonstrates that the ecosystem ships this shape and that the convention is intentional; the loader code above establishes the acceptance path. It does not prove a NekoJS-generated JAR will work in every environment.

The artifact is published at `https://maven.neoforged.net/releases/io/github/llamalad7/mixinextras-neoforge/0.5.4/mixinextras-neoforge-0.5.4.jar`. Its presence in a build cache does not prove where the launcher places it (`mods/` versus Jar-in-Jar); the `mods/` placement is established by the `ModsFolderLocator` code above, not by the artifact alone.

### Same-launch analysis

There are three distinct meanings of "same launch":

1. **The engine generates and admits its own carrier during the current launch.** Not possible through the supported discovery API. Discovery finishes before ordinary mod code is loaded. The engine's own class loader and mod instance do not exist at `findCandidates`/`scanMods` time.
2. **A discovery-time service generates/admits the carrier during the current discovery pass.** Possible with a custom `IModFileCandidateLocator` or `IDependencyLocator` in an early-service JAR. The service runs before the game layer is built, so its additions are included in the same launch. This is the only "same launch" route that does not require a previous launch.
3. **A carrier is present before launch.** The built-in `ModsFolderLocator` admits it during discovery; no custom service is needed. This is the two-launch flow.

For case 2, the early-service JAR itself must be discoverable:

- `net/neoforged/fml/loading/EarlyServiceDiscovery.java:38-43`: the early services are `IModFileCandidateLocator`, `IModFileReader`, `IDependencyLocator`, `GraphicsBootstrapper`, and `ImmediateWindowProvider`.
- `EarlyServiceDiscovery.java:60-68`: it walks `mods/` at depth 1 and considers regular `.jar` files of non-zero size.
- `EarlyServiceDiscovery.java:73`: it also considers unclaimed launch-classpath entries.
- `EarlyServiceDiscovery.java:99-130`: it only checks that the JAR contains `META-INF/services/<service>`; it does not load the code at this point. It creates a manifest-parser `ModFile` for the JAR.
- `FMLLoader.java:630-660`: the early-service JAR is loaded into a `URLClassLoader` whose parent is the current launch loader, and its path is marked located.
- `net/neoforged/fml/util/ServiceLoaderUtil.java:44-61`: when the service is instantiated, the provider's code source is added to `locatedPaths`; the comment says this prevents it from being located again as a mod file or library.
- `ModDiscoverer.java:190-213`: a path already marked located is skipped by `addPath`.

Therefore the discovery service cannot be supplied by an ordinary mod:

- If an ordinary mod JAR in `mods/` contains `META-INF/services/IModFileCandidateLocator`, `EarlyServiceDiscovery` intercepts it, marks it located, and it is no longer a mod. Its classes are on the early-services loader, not the game layer.
- If it does not contain the service file, its services are never loaded by the early-service scan.
- A nested Jar-in-Jar JAR is not scanned at all: `EarlyServiceDiscovery` only walks `mods/` depth 1 and unclaimed launch classpath entries, not the contents of other JARs.

A custom locator can still admit a `MOD` file in the same launch, but that file is visible. It can admit a `GAMELIBRARY` file invisibly. The locator itself is a separate installed JAR (or an unclaimed launch-classpath entry); it is not eliminated by this route.

### Constructing a game library programmatically

If a locator wants to avoid the manifest-only `DefaultModFileInfo` path and its error log, it can construct the `ModFile` explicitly. The relevant public points are:

- `net/neoforged/neoforgespi/locating/IModFile.java:29`: `@ApiStatus.NonExtendable` (do not implement `IModFile`; use the factories).
- `IModFile.java:38-90`: public `create(...)` factories, including `create(JarContents, ModFileInfoParser, IModFile.Type, ModFileDiscoveryAttributes)`.
- `IModFile.java:171-185`: `Type` docs: `MOD` is the game layer and holds mod code; `LIBRARY` is the plugin layer for language providers/non-mod code; `GAMELIBRARY` can reference Minecraft code and is in the game layer.
- `IModFile.java:131-139`: if `getModInfos()` is non-empty, the type must be `MOD`; otherwise the file is not loaded in the proper module layer. A game-library carrier must therefore have an empty mod list.
- `net/neoforged/neoforgespi/language/IModFileInfo.java:14-35`: public interface with the metadata methods; `ModFileInfoParser` is public. A custom parser can return an empty `getMods()` and an empty `getConfigList(...)` (not `null`).
- `ModDiscoverer.java:291-307`: `addModFile` requires the argument to be a `ModFile`; use `IModFile.create(...)` to obtain one, then `pipeline.addModFile(modFile)`.

This programmatic path avoids the manifest-only null-`getConfigList` bug and makes the empty mod list explicit. It is still a `GAMELIBRARY` and still not a mod in the list. It requires a locator; the built-in `ModsFolderLocator` cannot supply a custom parser.

### Known rough edges

- **Null `getConfigList` error log.** `JarModsDotTomlModFileReader.manifestParser` returns a `DefaultModFileInfo` whose `getConfigList` returns `null` (`JarModsDotTomlModFileReader.java:95-105`). `ModFileParser.getMixinConfigs` iterates that value, catches the resulting `NullPointerException`, logs `"Failed to load mixin configs from mod file"`, and returns an empty list (`ModFileParser.java:63-84`). A manifest-only `GAMELIBRARY` therefore works via its manifest `MixinConfigs` but is likely to emit an error log every launch. This is a loader bug/edge case, not a Mixin failure. A custom parser or an upstream fix avoids it. Do not build user-facing error handling on the assumption that this log means the carrier failed.
- **No `[[mixins]]` registration for a game library.** `MixinFacade.addMixins` iterates only `modList.getModFiles()` (`MixinFacade.java:109-171`). Put the config name in the manifest `MixinConfigs`; a `[[mixins]]` entry in a `neoforge.mods.toml` on a `GAMELIBRARY` is not read by that loop. The game-library handle only reads manifest attributes.
- **Config name uniqueness.** Mixin's `Mixins.addConfiguration` registers by config name; duplicate names are an error. A stale carrier plus a new carrier with the same config name can collide. Use a versioned config name or remove the old carrier before installing the new one.
- **Module name collisions.** A game library is a named module in the game layer. Set a stable, unique `Automatic-Module-Name` (MixinExtras uses `mixinextras.neoforge`); otherwise the derived name can collide with another JAR or with a future NekoJS artifact.
- **Class/package collisions.** Mixin classes, dispatchers, and generated support classes live in the game layer. Keep a stable NekoJS package namespace and do not duplicate classes across the visible mod, the game library, and the early-service locator.
- **Access transformers.** The FML11 `ModFileParser` reads access transformers from the mod metadata (`accessTransformers` entries). The manifest-only game-library path has no such metadata; do not assume ATs from a generated game library are applied. Use Mixin accessors or an access widener only if the specific loader path supports it.
- **Mixin config phase.** `MixinPlatformAgentDefault` rejects `config.json@PHASE` in the manifest with `"Setting config phase via manifest is no longer supported"` (Mixin 0.17.3, `MixinPlatformAgentDefault.java:36-61`). Do not put a phase suffix in `MixinConfigs`.
- **File placement and cleanup.** `ModsFolderLocator` uses `Files.list`, not a recursive walk: the carrier must be directly in `mods/`, not `mods/nekojs/`. Write atomically, validate the JAR before moving it into place, and remove the previous carrier by exact path. On Windows, a JAR that the previous process still has open can block replacement; write a new versioned file and delete the old one on the next start.
- **First launch.** A carrier generated during launch N cannot affect launch N. The first launch after installation has no generated mixins until restart. If the product must have mixins on the very first launch, it needs a preinstalled bootstrap carrier (which is an installed artifact) or a different launch mechanism, not this route.

### Public API, internal route, or feasibility?

- **Public SPI:** `IModFileCandidateLocator`, `IDependencyLocator`, `IDiscoveryPipeline`, `IModFile.create(...)`, `IModFile.Type`, `IModFileInfo`, and `ModFileInfoParser`. These are in `net.neoforged.neoforgespi` and are loaded as services or used as public factories.
- **Loader/Mixin manifest convention (version-specific, not a Java SPI):** `FMLModType: GAMELIBRARY` and `MixinConfigs: <file>.json`. MixinExtras 0.5.4 proves the convention is used in production; it is not a formal compatibility promise for arbitrary generated JARs.
- **Internal implementation:** `EarlyServiceDiscovery`, `LaunchContextAdapter`, `ModsFolderLocator`, `ModSorter`, `MixinFacade`, and `FMLModFileContainerHandle` are loader internals. They explain the behavior; they are not an API to call.
- **Feasibility, not runtime proof:** the whole `GAMELIBRARY` recommendation. The code path is clear and the artifact shape is real, but no NekoJS generated carrier has been run.

## NeoForge FML11: visible fallback

A standard carrier with `META-INF/neoforge.mods.toml` containing `[[mods]]` and `[[mixins]]` is a `MOD` file. It is sorted into game content, its classes are in the game layer, and `MixinFacade.addMixins` reads its `[[mixins]]` entries directly. It appears in `LoadingModList.getModFiles()` and therefore in the mod list.

This is the current design and the conservative fallback. A discovery-time locator can add such a JAR in the same launch, but the result is still visible. Without a locator, the JAR must be present before launch. There is no supported way for the running mod to add itself or a sibling `MOD` file to the current launch's mod list.

## NeoForge FML4 (MC 1.21.1): no invisible standard route

FML4 4.0.42 has a different discovery architecture:

- **Service layer.** `net/neoforged/fml/loading/TransformerDiscovererConstants.java:39-45` lists `ITransformationService`, `IModFileCandidateLocator`, `IModFileReader`, `IDependencyLocator`, `GraphicsBootstrapper`, and `ImmediateWindowProvider` as services that should be loaded in the service layer. `shouldLoadInServiceLayer` checks `META-INF/services` providers (`TransformerDiscovererConstants.java:64-69`).
- **Discovery.** `net/neoforged/fml/loading/ModDirTransformerDiscoverer.java:57-93` scans `mods/` depth 1 for regular `.jar` files of non-zero size that should be in the service layer. A service JAR is therefore loaded early and marked located; `ModsFolderLocator` later skips it. This is the same "an ordinary mod cannot be the discovery service" rule as FML11, but implemented through ModLauncher's service layer.
- **No game-library split.** `net/neoforged/fml/loading/moddiscovery/ModValidator.java:37-39`: candidate mods are `Type.MOD` and `Type.GAMELIBRARY`; candidate plugins are `Type.LIBRARY`. `ModValidator.java:47-63` validates candidate mods; `ModValidator.java:65-76` puts plugins in the `PLUGIN` layer and mods plus all game libraries in the `GAME` layer. `ModSorter.java:57-70` has no separate game-library list. `LoadingModList.java:42-56` has only plugins and mod files, and casts mod files to `ModFileInfo`.
- **Consequence.** A `GAMELIBRARY` is treated as a mod: it is validated, sorted, listed, and needs mod metadata. It is visible. A `LIBRARY` plugin is invisible, but its classes are in the `PLUGIN` layer, not the game layer.
- **Mixin lookup.** Mixin 0.15.2 `org/spongepowered/asm/service/modlauncher/MixinServiceModLauncher.java:302-303` resolves configs with `Thread.currentThread().getContextClassLoader().getResourceAsStream(name)`; `ModLauncherClassProvider.java:66-77` loads classes with `Class.forName(name, initialize, TCCL)`. A plugin-layer config/class is not on that TCL. FML4's `FMLMixinContainerHandle.getAttribute` returns `null` (`FMLMixinContainerHandle.java:12-35`), so there is no manifest `MixinConfigs` scan comparable to FML11.
- **Deferred registration is internal.** `net/neoforged/fml/loading/mixin/DeferredMixinConfigRegistration.java:20-35` is a public class, but it is an FML-internal hook: its static block installs `FMLMixinPlatformAgent` and the FML container; `addMixinConfig` throws `IllegalStateException("Too late to add mixin configs!")` after `registerConfigs()` (`DeferredMixinConfigRegistration.java:37-47`), and `registerConfigs` registers the names (`DeferredMixinConfigRegistration.java:49-65`). It is intended for FML's own mixin config collection, not for third-party generated carriers. A service-layer `ITransformationService` could call it before registration and provide transformers for generated classes, but that is an internal, version-specific route with module/readability risk and no standard JAR admission.

**FML4 conclusion:** there is no standard, public, invisible carrier route on 1.21.1. The choices are a visible `MOD` (the current design) or an unsupported internal service-layer/coremod route. Recommendation: keep the visible standard carrier for 1.21.1 and treat invisibility as not available on that platform.

## Fabric Loader 0.19.3: no supported route

Fabric's launch order in `net/fabricmc/loader/impl/launch/knot/Knot.java:139-161` is:

1. `provider.initialize(this)` (line 141)
2. `loader.load()` (line 142): discovery, mod resolution, remapping, `addMod`
3. `loader.freeze()` (line 143): sets `frozen = true` and `finishModLoading()`
4. `INSTANCE.loadClassTweakers()` (line 145): reads access-widener/class-tweaker metadata from the discovered mods
5. `FabricMixinBootstrap.init(...)` (line 147): Mixin initialization and config collection
6. `FabricLauncherBase.finishMixinBootstrapping()` (line 148): advances Mixin to `DEFAULT`
7. `classLoader.initializeTransformers()` (line 150)
8. `provider.unlockClassPath(this)` and `unlocked = true` (lines 152-153)
9. `loader.invokeEntrypoints("preLaunch", PreLaunchEntrypoint.class, ...)` (line 156)

Relevant bodies:

- `net/fabricmc/loader/impl/FabricLoaderImpl.java:111-118`: `freeze()` sets `frozen = true`, then `finishModLoading()`.
- `FabricLoaderImpl.java:366-379`: `finishModLoading()` adds each discovered non-builtin mod's code source through `FabricLauncherBase.getLauncher().addToClassPath(path)`, then sets up language adapters and entrypoints.
- `FabricLoaderImpl.java:524-541`: `loadClassTweakers()` reads each mod's `classTweaker` metadata and loads it. There is no public add-AW method.
- `FabricMixinBootstrap.java:51-113`: sets the Mixin service properties, calls `MixinBootstrap.init()`, then iterates `loader.getModsInternal()`, reads each mod's `getMetadata().getMixinConfigs(side)`, and calls `Mixins.addConfiguration(config)`. A duplicate config name throws `RuntimeException("Non-unique Mixin config name ...")`.
- `FabricLauncherBase.java:136-151`: `finishMixinBootstrapping()` reflectively calls `MixinEnvironment.gotoPhase(INIT)` then `DEFAULT`; by `preLaunch` the Mixin environment is already in `DEFAULT`.
- `PreLaunchEntrypoint.java:19-31`: the public interface is described only as "invoked just before launching the game" and "several seconds before the main/client/server entrypoints". It promises nothing about classpath, mixins, or access wideners.

Fabric's public `FabricLoader` API has no method to add a mod, add a classpath entry, or add a Mixin config. The only public-looking API in this area is Mixin's own `Mixins.addConfiguration`, which is not a Fabric loader API. `FabricLauncher.addToClassPath(Path, String...)` is an implementation interface (`net/fabricmc/loader/impl/launch/FabricLauncher.java:31`), and `Knot.addToClassPath` calls `KnotClassDelegate.addCodeSource` (`Knot.java:258-264`, `KnotClassDelegate.java:144-166`). It has no `frozen` check, and `unlocked` is already true at `preLaunch`, so the internal call is technically possible. However:

- The generated JAR cannot become a Fabric mod after `freeze()`; it will not appear in `getAllMods()`, and `FabricLoader.addMod` is private.
- Its access widener is not loaded: `loadClassTweakers` already ran and only reads mod metadata.
- `Mixins.addConfiguration` after `DEFAULT` is not guaranteed to be selected. Mixin 0.17.3 `Mixins.getUnvisitedCount()` documents that unvisited configs may still be visited if the transformer entered a phase but no mixins have been applied (`Mixins.java:162-170`), and `MixinProcessor.checkSelect` can select a config when `Mixins.getUnvisitedCount() > 0` and `transformedCount == 0` (`MixinProcessor.java:483-530`). That is a race/ordering-dependent condition, not a contract.
- The generated JAR's config must be resolvable through Knot's class loader after `addToClassPath`; the config is read when `Config.create` is called, so the classpath addition must precede `Mixins.addConfiguration`.

**Fabric conclusion:** there is no supported public route to admit a generated JAR as an invisible or visible mod at `preLaunch`. The internal `addToClassPath` + `Mixins.addConfiguration` combination is feasibility only, version-specific, has no access-widener support, and depends on Mixin's post-`DEFAULT` selection behavior. Recommendation: use a visible `fabric.mod.json` carrier and accept the mod list entry, or explicitly defer Fabric invisibility.

## Recommendations

### NeoForge 26.1.2 / 26.2.0 (FML11)

**Primary recommendation: auto-managed `GAMELIBRARY` carrier in `mods/`.**

- Generate the carrier at the end of a launch (or on demand), write it atomically to a versioned path directly under `mods/`, and remove the previous carrier. It is invisible in the mod list; it is still a file under `mods/`, managed by NekoJS, not by the user.
- Manifest: `FMLModType: GAMELIBRARY`, `MixinConfigs: <unique-name>.mixins.json`, and a stable `Automatic-Module-Name`. Do not add `META-INF/neoforge.mods.toml` with `[[mods]]`; a game library with a non-empty mod list violates the `IModFile` type contract. Do not rely on `[[mixins]]` in a game library; use the manifest attribute.
- Place the config JSON and all mixin/dispatcher classes in the same JAR. Keep the config at a path the TCL can resolve (normally the JAR root), and keep the config name unique across versions.
- Accept the first-launch limitation and the manifest-only error-log edge case. A custom locator with a custom `IModFileInfo` parser is the clean way to avoid the log if that becomes user-visible.
- This is the strongest supported recommendation: it uses the loader's own discovery, needs no Mixin internals, no classpath mutation, and no discovery service. It changes the current design assumption that the carrier is a standard mod with `neoforge.mods.toml`; that assumption is exactly what makes it visible.

**Optional: early-service locator for a cache outside `mods/` or true same-discovery generation.**

- A small early-service JAR in `mods/` (or on the unclaimed launch classpath) with `META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator` or `IDependencyLocator`.
- The locator reads a generated carrier from a cache directory and calls `pipeline.addPath(path, ...)` (path must not already be located) or `pipeline.addModFile(IModFile.create(..., Type.GAMELIBRARY, ...))`.
- This removes the carrier from `mods/` and can generate/admit it during the current discovery pass, but it adds an installed locator JAR and a second class loader. The locator cannot call the ordinary NekoJS engine; it must be self-contained or load the engine itself. The class loader and manifest conventions are version-specific. Do not choose this for the first implementation unless the cache location or same-launch requirement is a hard product requirement.
- Do not use `addJarContent` to bypass the located check for a JAR that is already an early service; that duplicates classes across the early-services loader and the game layer.

**Visible fallback:** a standard `MOD` with `neoforge.mods.toml` `[[mods]]` and `[[mixins]]`. Use it when the carrier must be a first-class mod, when mod-list visibility is acceptable or desired, or when the generated carrier needs mod metadata (for example dependencies or access transformers).

### NeoForge 1.21.1 (FML4)

Use the visible standard `MOD` carrier. There is no standard invisible route: `GAMELIBRARY` is treated as a mod and appears in the list; `LIBRARY` is invisible but its classes/config are not on the game TCL and FML4 does not scan per-file manifest `MixinConfigs`. The `DeferredMixinConfigRegistration` route is an internal FML hook, not a public carrier API, and would require a service-layer/coremod implementation with module-readability risk. Treat FML4 invisibility as a documented limitation.

### Fabric 0.19.3

Use the visible standard `fabric.mod.json` carrier. `preLaunch` is after `freeze()`, Mixin config collection, and `DEFAULT`; `FabricLoader` exposes no add-mod/classpath/mixin API; access wideners are loaded before `preLaunch` and cannot be added. The internal `FabricLauncher.addToClassPath` + `Mixins.addConfiguration` route is not a supported contract and has selection/ordering risk. Treat Fabric invisibility as a documented limitation.

## Blockers and open questions

1. **Engine-before-discovery is impossible.** NekoJS's normal mod code cannot run before FML11/FML4 discovery or Fabric `freeze()`. Every invisible FML11 route still requires an artifact to exist before the launch that uses it, or a discovery-time locator. There is no "the engine generates its own carrier and the current launch applies it" path through supported APIs.
2. **Generated classes, config, and mappings must exist before the class loader is built.** The carrier must contain final mixin classes, dispatchers, config JSON, and any refmap. Runtime generation after discovery cannot add classes to the current game layer. Mapping/refmap correctness remains a P0 blocker from the design; a game library does not remove it.
3. **The FML11 manifest-only game library has a loader error-log bug.** The path works through the manifest `MixinConfigs`, but `ModFileParser.getMixinConfigs` logs a caught NPE for the null config list. Confirm the log volume on 26.1.2/26.2.0 and either fix upstream, use a custom parser, or document it. Do not interpret the log as a failed carrier without checking the actual mixin application.
4. **Version-specific contracts.** `FMLModType`, `MixinConfigs` manifest handling, and `FMLModFileContainerHandle` are loader/Mixin implementation conventions, not a formal API promise. Pin and test against the exact loader/Mixin versions per node; the key FML11 files are stable across 11.0.13/11.0.16, but that is evidence for these versions, not a guarantee.
5. **No runtime proof.** This document establishes code paths and artifact shape. It does not prove that a generated NekoJS game library loads, that its mixins apply to obfuscated production classes, that the module descriptor resolves, or that the error log is harmless. A minimal runtime experiment is required before shipping.
6. **No supported invisible route on FML4 or Fabric.** If invisibility is a hard requirement on all three targets, the product needs either a visible carrier on those two platforms or a separate launch mechanism outside the loader's supported API. The latter is out of scope here and should be treated as unsupported.

## Evidence appendix

### Source trees and versions

| Component | Version | Local source / artifact |
|---|---|---|
| FancyModLoader (FML11) | 11.0.16 | `build/research-loader-transparent/fml11-11.0.16`; `loader-11.0.16-sources.jar` |
| FancyModLoader (FML11) | 11.0.13 | compared by SHA-256; key files identical |
| FancyModLoader (FML4) | 4.0.42 | `build/research-loader-transparent/fml4-4.0.42`; `loader-4.0.42-sources.jar` |
| Fabric Loader | 0.19.3 | `build/research-loader-transparent/fabric-0.19.3`; `fabric-loader-0.19.3-sources.jar` |
| Mixin | 0.17.3+mixin.0.8.7 | `build/research-loader-transparent/mixin-0.17.3` |
| Mixin (FML4) | 0.15.2+mixin.0.8.7 | `build/research-loader-transparent/mixin-0.15.2` |
| MixinExtras NeoForge | 0.5.4 | cached JAR inspected; manifest quoted above |
| NeoForge | 26.1.2.71 | POM resolves loader 11.0.13, Mixin 0.17.3, MixinExtras 0.5.4 |
| NeoForge | 26.2.0.57 | POM resolves loader 11.0.16, Mixin 0.17.3, MixinExtras 0.5.4 |
| NeoForge | 21.1.227 | POM resolves loader 4.0.42, Mixin 0.15.2, MixinExtras 0.5.3 |
| Fabric Loader | 0.19.3 | `gradle.properties`: ASM 9.10.1, Mixin 0.17.3, MixinExtras 0.5.4 |

### Primary URLs

- NeoForge Maven POMs: `https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.71/neoforge-26.1.2.71.pom`, `.../26.2.0.57/neoforge-26.2.0.57.pom`, `.../21.1.227/neoforge-21.1.227.pom`
- FancyModLoader 11.0: `https://github.com/neoforged/FancyModLoader/tree/11.0/loader/src/main/java/net/neoforged/fml`
- FancyModLoader 4.0: `https://github.com/neoforged/FancyModLoader/tree/4.0/loader/src/main/java/net/neoforged/fml`
- Fabric Loader 0.19.3: `https://github.com/FabricMC/fabric-loader/tree/0.19.3/src/main/java/net/fabricmc/loader`
- Fabric Loader 0.19.3 `gradle.properties`: `https://github.com/FabricMC/fabric-loader/blob/0.19.3/gradle.properties`
- MixinExtras NeoForge 0.5.4: `https://maven.neoforged.net/releases/io/github/llamalad7/mixinextras-neoforge/0.5.4/mixinextras-neoforge-0.5.4.jar`

### What was and was not verified

- Verified: loader/Mixin source code paths, exact line-level behavior, POM version mapping, MixinExtras manifest and entries, SHA-256 equality of key FML11 files across 11.0.13/11.0.16.
- Not verified: an actual NekoJS generated carrier launch, mixin application to production classes, obfuscation/refmap behavior, module resolution in a real instance, the frequency/impact of the manifest-only error log, and behavior on future loader/Mixin versions.

This document is research input, not an implementation approval. The implementation should remain with the lead, and any runtime test should be treated as a separate validation step.