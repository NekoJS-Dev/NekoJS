# VillagerTrades 与 PostEffects 事件化改造

状态：ready-for-agent（本地 spec，2026-09-07）

## Problem Statement

整合包作者现在通过 VillagerTrades 和 PostEffects 两个全局绑定同时表达声明和触发平台副作用。VillagerTrades.add 在 SERVER 脚本加载期间直接依赖当前服务器和交易注册表；PostEffects 同时负责 effect 定义、资源 effect 激活、运行时 shader 注册、状态查询和 renderer 缓存。

这种形态让脚本作者需要知道脚本加载时机、资源 reload、registry epoch、冻结注册表和 client renderer 状态。它也让 Fabric 端口变成逐个补全 NeoForge 全局绑定：交易依赖 26.x 的 VillagerTrade / TradeSet registry 重写，后处理依赖 renderer pipeline 的 shader source 接入。两者都缺少一个可重放、可验证且能够在 reload 后清理旧产物的 Script API 形态。

## Solution

把两类“脚本声明一组期望、平台在安全时机应用”的能力改为声明式事件。脚本只向事件载荷写入贡献；生命周期 module 在一次完整脚本 reload 成功提交后封存不可变产物；平台 adapter 在 registry 或 renderer 就绪时把完整产物 reconcile 到原生状态。

VillagerTrades 改为 SERVER 事件 VillagerTradesEvents.modify。Post-effects 改为 CLIENT 事件 ClientEvents.postEffects。两者都不允许脚本直接取得服务器、RegistryAccess、MappedRegistry、GameRenderer、PostChain 或 GPU 资源。

Post-effects 的定义事件与运行时状态命令分开：ClientEvents.postEffects 声明完整 effect catalog；PostEffectController 是小的状态 interface，负责 activate、clear、toggle、current 和 capability 查询，并把 renderer 细节隐藏在 PostEffectRendererAdapter 后面。它不是第二个定义入口。

## User Stories

1. As a server script author, I want to declare villager trades in VillagerTradesEvents.modify, so that my script describes desired offers without depending on a loader-specific registry lifecycle.
2. As a pack author, I want one canonical trade-set identifier format for villagers and wandering traders, so that the same declaration can target the supported 26.x loaders and the legacy NeoForge adapter.
3. As a pack author, I want a trade declaration to accept cost, optional second cost, result, uses, XP, and price multiplier, so that the existing offer shape remains available after the API migration.
4. As a server operator, I want an empty successful trade event result to remove prior NekoJS trade contributions, so that deleting a declaration cannot leave stale offers behind.
5. As a server operator, I want full SERVER reload, datapack reload, and the NekoJS server reload command to use one reconcile protocol, so that offers do not duplicate or remain attached to an old registry epoch.
6. As a pack author, I want invalid trade declarations to fail at declaration validation and registry mutation failures to be reported at application time, so that configuration errors and platform failures are distinguishable.
7. As a pack author, I want vanilla offers to remain present when I add custom offers, so that NekoJS augments a trade set instead of replacing its baseline unintentionally.
8. As a pack author, I want repeated complete reloads to produce the same effective offers, so that reload is idempotent.
9. As a Fabric pack author, I want the same VillagerTradesEvents.modify Script API as NeoForge 26.x, so that platform code owns the different registry surgery rather than my scripts.
10. As a legacy NeoForge 1.21.1 pack author, I want the event form to map through the legacy static-trade adapter, so that the Script API has one migration destination without requiring Fabric 1.21.1 support.
11. As a client script author, I want to declare resource-backed post effects through ClientEvents.postEffects, so that effect definitions are rebuilt predictably whenever CLIENT scripts are fully reloaded.
12. As a client script author, I want to declare a desired initial active effect separately from its definition, so that one catalog can contain several effects while client state has one explicit active choice.
13. As a client script author, I want PostEffectController to expose state commands and capability results, so that activation remains possible without exposing renderer internals or creating a second definition surface.
14. As a Fabric pack author, I want resource-backed post effects to work once the Fabric renderer adapter is available, so that standard post_effect assets do not depend on runtime GLSL support.
15. As a client script author, I want runtime shader definitions to return an honest unsupported result when the current platform lacks a runtime renderer adapter, so that unsupported Fabric rendering never degrades silently.
16. As a client script author, I want replacing or deleting post-effect declarations to invalidate stale cached GPU resources, so that a full CLIENT reload cannot reuse chains from an old script revision.
17. As a plugin author, I want one plugin contribution path for registering the event groups and type documentation, so that Fabric explicit plugin discovery and NeoForge annotation discovery expose the same Script API.
18. As a maintainer, I want pure contribution collectors and fake adapters to test lifecycle ordering, so that most trade and renderer behavior is verified without starting Minecraft.
19. As a maintainer, I want thin native contract tests for every loader and version adapter, so that mapped registry and renderer target drift is caught before release.
20. As a release maintainer, I want runtime smoke tests to prove observable offers and effects rather than only class loading, so that an adapter cannot pass CI while silently failing to apply a contribution.

## Implementation Decisions

1. Introduce a shared committed-contribution lifecycle module. Its interface starts a candidate collection before a full script reload, publishes the declaration event only after candidate script contexts are committed, seals an immutable product, and asks a platform adapter to reconcile that product. A failed candidate reload leaves the prior applied product in place. Binding close callbacks and pre-commit afterScriptsLoaded notifications are not valid publication seams.

2. Introduce VillagerTradesEvents as a dedicated SERVER event group. It exposes one non-dispatch event named modify, whose payload is VillagerTradeEventJS. The payload accepts only declarative add operations and validates canonical trade-set identifiers and offer fields. It does not expose MinecraftServer, RegistryAccess, native trade objects, frozen registry operations, pending counts, or an apply method.

3. A sealed VillagerTradePlan preserves deterministic listener order and every valid declaration in the current full SERVER revision. Reconcile receives the entire plan, including an empty plan. Identical declarations in one revision remain separate declared offers; duplicate prevention applies across previous and current applied revisions, not by silently removing author-declared offers.

4. VillagerTradeLifecycle owns begin, seal, registry-ready, reconcile-once-per-epoch, and stop-reset behavior. It waits until both a sealed plan and a platform-published registry epoch are available. Startup, datapack reload, and NekoJS server reload all notify this same module. A later registry epoch causes baseline recapture and exactly one reapplication. Server stop clears all plan, snapshot, and applied-key state.

5. Define VillagerTradeRegistryAdapter as the platform seam. The legacy NeoForge adapter owns static-array mutation. The registry-backed NeoForge 26.x and Fabric 26.x adapters own VillagerTrade / TradeSet creation, snapshot restoration, previous injected keys, frozen registry handling, and native registry mutation. The adapter interface consumes a plan and epoch; no script-facing module may access registry internals.

6. Remove the VillagerTrades global binding in the same breaking Script API release that introduces VillagerTradesEvents.modify. Do not retain a deprecated alias or a facade that calls the event collector imperatively. Update type docs and wiki with a direct migration table from add calls to event.add declarations.

7. Extend the existing ClientEvents group with a CLIENT event named postEffects on every supported client loader. Its PostEffectEventJS payload collects an immutable PostEffectCatalog. A definition has a stable identifier, a resource-backed or runtime form, validated chain data, optional shader sources, and a declared desired active state. Conflicting definitions for one identifier and conflicting desired active states are contribution errors; validation must happen before the definition enters the catalog.

8. Replace the definition methods on the PostEffects global with ClientEvents.postEffects declarations. Retain runtime state commands only through a narrow PostEffectController interface. The controller has activate, clear, toggle, current, isActive, and capability inspection operations; it cannot define effects, receive raw shaders, access a renderer, or mutate the catalog.

9. Define PostEffectRendererAdapter as the client platform seam. It reconciles a full catalog, activates resource effects, optionally activates runtime effects, reports capability-specific outcomes, clears current state, and invalidates cached runtime resources. Reconcile replaces the full catalog rather than incrementally appending definitions. Removing a definition or reconciling an empty catalog invalidates its cached chains before clearing state.

10. Add explicit platform capabilities for villager trade reconciliation, resource-backed post effects, and runtime post-effect pipelines. Fabric may expose resource-backed post effects before it exposes runtime pipelines. A runtime definition on an adapter without a runtime pipeline must produce a deterministic unsupported result, never a no-op success or a fallback to another renderer path.

11. The Fabric post-effect implementation is staged. Resource-backed activation requires a Fabric renderer access strategy and client registration. Runtime definitions require a proven PostChain or GpuDevice shader-source adapter; copying the NeoForge ShaderManager hook is prohibited because modern Fabric PostChain compilation bypasses its ShaderSource callback. The catalog event can ship before Fabric runtime-pipeline capability exists.

12. Plugin registration follows the existing extension-point model. Event groups and type documentation are registered through the normal event and type-document contribution paths. Fabric built-ins must be listed by its explicit plugin loader. A new Java plugin extension point is not introduced unless third-party Java contribution demonstrates a second real adapter; if introduced later it must follow the ADR-0010 point, facade, manifest, and pairing protocol.

13. All definition collection uses CLIENT or SERVER full-script reload only. Single-file hot reload does not reconcile villager trades or post-effect catalogs. This keeps collector product ownership, native state cleanup, and client GPU lifetimes deterministic.

## Testing Decisions

Good tests observe a sealed contribution product and its externally visible applied state, not private map fields, reflection helpers, mixin locals, or renderer implementation details.

1. The highest new seam is the committed-contribution lifecycle module used with fake VillagerTradeRegistryAdapter and fake PostEffectRendererAdapter implementations. Tests cover candidate reload success and failure, event order, validation, empty-product cleanup, repeated registry-ready notification, epoch replacement, stop reset, catalog replacement, and renderer cache invalidation.

2. Villager trade collector tests cover trade-set parsing, item/count validation, offer ordering, valid duplicate declarations, invalid field diagnostics, and an empty successful revision. Adapter tests cover baseline retention, prior contribution removal, two contributions to one trade set, registry epoch replacement, and cleanup after every declaration is removed.

3. Post-effect collector tests cover identifier conflict policy, resource versus runtime definitions, one desired active effect, empty catalog teardown, and client reload replacement. Controller tests use a fake renderer adapter to cover supported resource activation, unsupported runtime activation, toggle, clear, current state, and cache invalidation after unregister or reload.

4. Existing prior art is preferred: RecipeEventJS for baseline-to-event-to-final-parse behavior, TagLoaderMixin for raw source-map mutation followed by a reload-visible assertion, Block and Item modification snapshots for restore/reapply semantics, GoalEvents for one-shot declaration events, RenderRegistrationBusJS for reload-owned client registrations, and PostEffectChainJsonTest for pure post-chain validation.

5. Native contract tests remain thin and version-specific. They verify that each loader adapter reaches its actual lifecycle barrier, that Fabric explicit plugin discovery exposes the event group, and that all required mixin/access targets resolve. They do not duplicate the pure lifecycle test matrix.

6. Runtime acceptance is required before capability exposure. For registry-backed adapters, an actual villager restock must expose a declared offer after startup, datapack reload, and NekoJS server reload. For post effects, a client smoke must visibly activate a resource effect; runtime shader capability requires a separate client smoke proving a registered runtime definition reaches the GPU pipeline and is discarded on reload.

7. Probe/type tests must assert the removal of the old global definition surfaces, the availability of VillagerTradesEvents.modify and ClientEvents.postEffects, controller command documentation, platform capability declarations, and the migration table references.

## Out of Scope

- Fabric 1.21.1 support.
- Reusing the legacy Fabric TradeOfferHelper or static TradeOffers model for 26.x.
- A generic registry-mutation framework for unrelated features.
- Runtime Fabric GLSL activation before a concrete PostChain or GpuDevice shader-source adapter and client smoke prove it.
- Moving arbitrary per-tick post-effect state logic into client tick or render listeners.
- GUI, JEI, FluidBuilder, capability, datagen, and unrelated renderer ports.
- External release, GitHub Actions release execution, CurseForge upload, GitHub Release publication, or git push.

## Further Notes

This is a breaking Script API migration. The old VillagerTrades and PostEffects definition surfaces are removed in one release, with a wiki migration table rather than compatibility shims. PostEffectController remains only for runtime state commands because commands are not declarative contributions.

Implementation task graph:

1. Build the committed-contribution lifecycle module and fake adapter test harness. This blocks both feature migrations.
2. Build VillagerTradesEvents.modify, the neutral plan, documentation, and the legacy NeoForge adapter. This depends on task 1.
3. Build registry-backed NeoForge 26.x and Fabric 26.x trade adapters plus startup, resource reload, script reload, and stop lifecycle publications. This depends on task 2 and requires native registry evidence per Fabric node.
4. Build ClientEvents.postEffects, PostEffectCatalog, PostEffectController, and fake renderer adapter tests. This depends on task 1 and can run in parallel with tasks 2 and 3.
5. Build the NeoForge renderer adapter and migrate the current global definition behavior. This depends on task 4.
6. Build Fabric resource-backed renderer activation, then a Fabric runtime pipeline adapter only after its renderer contract is proven. Both depend on task 4; runtime pipeline support additionally depends on the resource-backed client smoke.
7. Migrate probe output, wiki, and remove old bindings after their corresponding adapters pass full reload and runtime acceptance.

ADR-0011 constrains the VillagerTrades registry adapter. The existing post-effect renderer audit constrains the Fabric runtime pipeline adapter. The version tree remains the owner of loader differences; common code keeps loader-neutral contribution products and lifecycle logic.
