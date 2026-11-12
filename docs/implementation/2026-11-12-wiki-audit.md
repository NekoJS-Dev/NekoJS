# Bilingual wiki audit after the engine configuration changes

## Scope

The audit uses commit 6febef8b as the production baseline. All 31 existing topic pairs were assigned source-based review, including topics where no correction was needed; a new paired Configuration reference brings the catalog to 32 topics. This is a documentation delivery, not a production change, release, or live-game acceptance. Human translation review remains pending.

## Corrections and additions

- Added a single configuration entry point covering engine.toml, probe.toml and ai.toml; exact current engine defaults, TOML table placement, restart requirements, legacy read-only precedence, protected config paths, default-off MixinJS and retired execution controls. Both examples are parsed and compared against all twelve default entries in the [canonical loader](../../common/src/main/java/com/tkisor/nekojs/core/config/SandboxConfigLoader.java#L50-L106); [legacy fallback](../../common/src/main/java/com/tkisor/nekojs/core/fs/ClassFilter.java#L137-L155) and [protected writes](../../common/src/main/java/com/tkisor/nekojs/core/fs/SandboxPolicy.java#L46-L86) remain distinct.
- Removed stale automatic execution-budget claims from remaining script/pack/command guidance without confusing request/reply waits, normal lifecycle cancellation, output caps or the separate Mixin declaration evaluator.
- Corrected architecture/build guidance for the independent NekuJS addon and both JSX lowering modes. The [settings graph](../../settings.gradle.kts#L35-L45), [addon packaging](../../addons/nekujs/build.gradle.kts#L16-L27) and [JSX compiler](../../common/src/main/java/com/tkisor/nekojs/core/compiler/NekoJsxCompiler.java#L15-L31) are the respective sources, not a claimed sixth platform node or a second runtime.
- Corrected recipe filter examples and limits, builder/handler return types, required ingredient-action arguments, native item/ingredient JSON boundaries, separate addon installation/restart requirements and fixture-scoped compatibility counts. Native constructors and codec support are not universal gameplay/serializer acceptance.
- Corrected loader-specific login payload examples: NeoForge publishes native PlayerEvent payloads, while Fabric uses a neutral player wrapper. [NeoForge binding](../../src/main/java/com/tkisor/nekojs/bindings/event/PlayerEvents.java#L22-L25) and [neutral getter](../../src/main/java/com/tkisor/nekojs/wrapper/event/player/PlayerLifecycleEventJS.java#L10-L17) do not establish a universal event.player property.
- Corrected native Brigadier command examples, ProbeEvents names/IR workflow and publication boundaries, member/type-adapter availability, entry versus callback failure wording, and current plugin/annotation contracts. [IR construction and workspace generation](../../common/src/main/java/com/tkisor/nekojs/probe/ProbeCoordinator.java#L251-L279) happen at different stages; successful per-file writes are not a whole-run atomic transaction.
- Filled practical Node shim limits, Python subset caveats, client UI lifecycle/layout constraints, I18n coverage and commit-time modification/synchronization behavior from their current owners. No new overload or future feature is presented as implemented.

## Coverage ownership

| Review group | Existing topics covered |
|---|---|
| Entry, configuration, architecture and build | home, platform-compatibility, quick-start, faq, script-api-style, project-architecture, build-system; new configuration pair |
| Native recipe and type surfaces | recipe-system, mixinjs, registering-new-content, type-adapters, probe-type-generation |
| Script authoring and lifecycle | script-basics, script-properties, global-bindings, data-and-state, python-scripts, python-feature-support, event-reference, client-rendering, jsx-client-ui, typescript-and-jsx |
| Modules, controls and extension contracts | ai-support, commands, error-reference, script-packs, module-system, nodejs-compatibility, plugin-development, event-extensions, annotations |

Existing topic headings and explicit anchor identities must remain intact; generated navigation and bilingual metadata are refreshed through scripts/wiki-docs.mjs rather than manually patched. The new page is registered in docs/wiki-pages.json.

## Verification

- Baseline strict check: 31 bilingual topics, zero problems.
- Both new TOML examples parsed and matched all twelve current engine defaults, with no retired controls.
- Focused existing recipe checks passed in 3m25s: `./gradlew --no-daemon :26.1.2:test --tests "*RecipeFilterAdapterTest" --tests "*RecipeFilterApplicationTest" --tests "*RecipeEventJSJsonContractTest" --tests "*IngredientActionRegistryTest" :1.21.1:nativeRecipeProbeTest --tests "*MinecraftRecipeSchemaScannerTest" :26.1.2:nativeRecipeProbeTest --tests "*MinecraftRecipeSchemaScannerTest" --console=plain`. These verify their actual contracts; registry-free JSON tests are not native item codec or game-world proof.
- Both final TOML samples parse and exactly match all twelve current defaults. All 36 new/changed JavaScript or TypeScript fences pass Node syntax or TypeScript transpile diagnostics (zero failures); no changed Python code fence was introduced. These are syntax checks, not game execution.
- Existing PythonToJsCompilerTest and PythonExamplesSmokeTest plus NeoForge 26.1.2 I18nBindingsTest passed in 31s.
- Final canonical sync/stamp and strict checks passed: 32 bilingual topics, zero problems. All eight wiki-tool tests passed.
- A separate baseline comparison verifies all explicit wiki-section anchor identities on the original 62 pages are unchanged. Whitespace review passed; all production source trees remain untouched.
- Existing JSX/profile/modification-focused checks passed in 26s: `./gradlew --no-daemon :common:test --tests "*NekoJsxCompilerTest" --tests "*Ticket43ProfileGoldenTest" :26.1.2:test --tests "*ModificationSyncWireTest" --tests "*Ticket39ModificationExamplesTest" --console=plain`. They verify compiler/profile/synchronization contracts, not native UI visuals or actual multiplayer acceptance.

## Known remaining limits

The initial audit identified three source-backed defects: and-wrapped arrays followed OR parsing, invalid script properties replaced prior valid values with defaults, and the shipped welcome source used the wrong NeoForge accessor. The subsequent authorized [bugfix delivery](2026-11-12-wiki-audit-bugfixes.md) corrects these implementations/resources and updates the temporary Wiki caveats. This audit alone did not deliver those fixes; their separate regression evidence and remaining acceptance boundaries are recorded there.

No live client, server-world, multiplayer, GPU visual or human translation acceptance was performed. Package main/exports resolution, legacy builder publication, broader addon semantics and exact MixinJS long transport are not added by this audit. Passing syntax checks does not resolve runtime globals, loader payload types or game side effects. Historical baselines are not rewritten to claim newer acceptance.
