# 2026-09-12 生产 static 可变生命周期状态总账（ticket 05 / Phase 1）

> 工单：`docs/architecture-refactor/implementation-tickets/05-runtime-root.md`（W1 预整理：单一 owner + 装配收口 + 旁路删除）。
> 本账是全仓静态扫描的归档快照，基线 commit `4020c130`（01/02/03 已关闭）。
> 分类词汇（同工单）：`loader-owned`（loader composition root 持有）/ `process-owned`（进程级例外，AC2）/ `root-owned`（随 NekoRuntimeRoot 生命周期）/ `generation-owned`（06/07 处理，本票不动）/ `domain adapter-owned`（W2–W7 功能域）/ `test-only seam` / `待删除 legacy bypass` / `deliberate singleton`（有意单例，保留）。

## 0. 扫描方法

- 范围：`src/main/java`（NeoForge 三节点共享树，stonecutter 守卫）、`common/src/main/java`、`versions/26.1.2-fabric/src/main/java`（Fabric 双节点 bridge 源，`deps.fabric_source_node`）、`versions/1.21.1/src/main/java`（1.21.1 override）；不含 `build/` 生成副本与 test source set。
- 方法：正则扫 `static` 字段声明（439 原始命中 → 过滤不可变常量/Logger/Pattern 等 → 213 条候选），再人工归并到"类/符号簇"粒度；每条给 owner 分类、生命周期、可替换性（本票动作 / 保留理由 / 删除条件）、测试覆盖现状。
- 本票动作词汇：`迁移`（Phase 3 注入窄 handle）、`收口`（Phase 2 装配函数承载，不改语义）、`删除`（Phase 5）、`保留`（写明理由）、`06/07`（generation-owned，本票不动）。

## A. Runtime owner / lifecycle entry（本票核心）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| A1 | `NekoJSMod.RUNTIME_ROOT` | `src/main/java/com/tkisor/nekojs/NekoJSMod.java:52`（赋值 :163） | 待删除 legacy bypass（public static root） | 进程（每 mod entry 装配一次，reload 不重建） | **删除**：Phase 3 改 private + 注入窄 handle 迁移全部读者；Phase 5 删字段 | 无（本票补独立 root/close 单测） |
| A2 | `NekoJSFabricMod.RUNTIME_ROOT` | `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java:59`（赋值 :154） | 待删除 legacy bypass（public static root） | 同上 | 同上 | 同上 |
| A3 | `NekoJS.scriptManagers`（`ScriptTypedValue<ScriptManager>` 实例字段，非 static，但与 root 内 EnumMap 平行的第二 manager 容器） | `common/src/main/java/com/tkisor/nekojs/NekoJS.java:21`；两 loader 装配时 `set(type, manager)`（`NekoJSMod.java:173`、`NekoJSFabricMod.java:164`） | 待删除 legacy bypass（重复 manager 容器） | 进程（随 mod entry） | **删除**：Phase 3 把 STARTUP load 等读点改走 root；Phase 5 删字段。生产读者仅两处 `this.scriptManagers.at(STARTUP).loadScripts()`（NekoJSMod.java:177、NekoJSFabricMod.java:168）与 set 写点 | 无（装配等价单测覆盖） |
| A4 | `NekoJSMod.modEventBus`（static） | `src/main/java/com/tkisor/nekojs/NekoJSMod.java:51` | loader-owned | 进程 | 保留：platform mod bus handle，loader 接线用（`NekoJSClient.register` 等）；非 lifecycle owner，删除条件 = 注入面全面落地后（W7 域，不在本票） | 无 |
| A5 | `ScriptErrorReporter.instance`（volatile Reporter） | `common/src/main/java/com/tkisor/nekojs/api/event/ScriptErrorReporter.java:13`；装配时 `ScriptErrorReporter.set(new ErrorTrackerReporter(errorTracker))`（`NekoJSMod.java:154`、`NekoJSFabricMod.java:144`） | root-owned 状态的进程级静态门面（errorTracker 在 `NekoCoreContext`，随 root） | 进程（一次装配绑定一份 tracker） | 保留（本票）：读点在 common 内部（`EventBusJS`、compiler validator、`ClientRenderRegistry`、`NativeEventsJS`）与 MC 层，无法在本票全部改注入；归账为"root-owned 的静态报告面"，删除条件 = W4（Script Execution Environment owner 集中 error 面）。**本票约束**：set 只发生在共享装配函数内一次，不形成第二 owner | 无直接单测；`errors()`/count 经命令烟测 |
| A6 | `ScriptManager.CONTEXT_TO_MANAGER`（static Map） | `common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java:53`（写 :191/:408，删 :703，读 :60/:78/:95） | root-owned（每 manager 注册自己的 Context；manager 随 root 创建/关闭） | 进程（跟随 manager 集合） | 保留：manager↔Context 反查是错误定位/日志面（ScriptTypeEnv.logger 反查），W4 域集中化后处置；本票不改 | 无 |
| A7 | `NekoModulePipeline.LEGACY_INSTANCE` + `SHARED_COMPILATION_PIPELINE`（static，`bindLegacyInstance`） | `common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java:30-31,43-48`；装配绑定（`NekoJSMod.java:162`、`NekoJSFabricMod.java:152`）；读点 `NekoModulePipelineCache.java:114,118` 与 `legacyPrepare` | 待删除 legacy bypass（static binding） | 进程 | **收口**（Phase 2 装配函数承载 bind，顺序不变）；删除条件 = W3 显式注入 pipeline/cache 后删 `bindLegacyInstance/legacyInstance/legacyPrepare` | 无直接单测 |
| A8 | `NekoModulePipelineCache.PREPARED_CACHE`（static Map<Path, PreparedEntry>） | `common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipelineCache.java:26` | domain adapter-owned（W3：Script Preparation + Module Resolution/Cache） | 进程（跨 reload 的模块缓存） | 保留（W3 处理失效语义与显式注入） | 无 |
| A9 | `NekoEsmVirtualModuleRegistry`（SOURCES/DISPLAY_PATHS/DISPLAY_PATHS_BY_FILE_NAME/KEY_BY_FILE_NAME/**GENERATIONS**/TYPES） | `common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoEsmVirtualModuleRegistry.java:20-27` | generation-owned（字段名已含 GENERATIONS） | 进程 + per-generation | **06/07 处理**（candidate/active 隔离、切换 generation）；本票不动 | 无 |
| A10 | `ScriptCompilerRegistry.current`（volatile）+ `INSTANCE` | `common/src/main/java/com/tkisor/nekojs/core/compiler/ScriptCompilerRegistry.java:14,16`；`NekoPluginRuntime.publish()`(:142) 写 | process-owned（plugin runtime publish 派生） | 进程 | 保留：由 `NekoPluginRuntime.publish` 一次性设置（AC8 计数点之一）；显式注入归 W3 | 间接（bootstrap 相关既有测试） |

## B. 进程级例外（AC2 点名，保留 + 可重复测试）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| B1 | `NekoRuntimeAccess.runtime`（volatile IPluginRuntime + fireInit/fireInitStartup/fireAfterInit 事件面） | `common/src/main/java/com/tkisor/nekojs/api/plugin/NekoRuntimeAccess.java:10`；装配 `fireInit()`/`fireInitStartup()`/`fireAfterInit()`（两 loader） | process-owned 例外（AC2） | 进程 | 保留：plugin 事件面是 api 层公开面，不得形成第二 owner —— 约束 = 只有共享装配函数调用 `bootstrapOwned`/`publish` 链路设置；本票补**可重复测试**（fire 事件计数、set 后 get） | **本票新增**（AC2/AC8） |
| B2 | `NekoPluginRuntime.current`（static current + bootstrap/bootstrapOwned/publish） | `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java:48,139-151` | process-owned 例外（AC2） | 进程（bootstrap 一次；普通 reload 不重跑） | 保留：plugin bootstrap 只在 loader bootstrap 一次（AC8 验证 reload 前后一次）；W2（ticket 08）迁移 plugin runtime | **本票新增**（独立 root 不互相污染时 current 单一） |
| B3 | `NekoJSBasePluginManager.ENTRIES/sortedView/ownedView`（plugin entries） | `common/src/main/java/com/tkisor/nekojs/core/NekoJSBasePluginManager.java:30-32` | process-owned（plugin entries，loader discovery 写入一次） | 进程 | 保留：loader bootstrap 期 discovery 写入；W2 处理（无调用者 manager facade 删除条件） | 间接 |
| B4 | `NekoSharedEngine.SHARED_ENGINE`（进程级共享 Graal Engine） | `common/src/main/java/com/tkisor/nekojs/core/NekoSharedEngine.java:6` | process-owned 例外（AC2；decision：共享 Engine） | 进程（永不关闭） | 保留：process-owned，root close 不得关闭它；本票补**独立 root 不互相污染测试**（两个 root 各自建 manager 共用 Engine 无状态泄漏） | **本票新增** |

## C. platform / path（进程级接线，保留）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| C1 | `Platform.INSTANCE`（volatile IPlatform） | `common/src/main/java/com/tkisor/nekojs/platform/Platform.java:11`；loader static block `Platform.init(...)` | process-owned（loader 接线一次） | 进程 | 保留：平台门面是 decision 03 的 compat 面，非 lifecycle owner | 间接 |
| C2 | `NekoIdCompat.ADAPTER` | `common/src/main/java/com/tkisor/nekojs/platform/NekoIdCompat.java:7`；loader static block | process-owned | 进程 | 保留（同上） | 间接 |
| C3 | `NekoJSPaths.INSTANCE`（volatile，lazy） | `common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java:21` | process-owned（数据面路径单例） | 进程 | 保留：03 号票已冻结数据面路径/格式；路径单例与 lifecycle 解耦 | 间接（datafix 契约测试） |

## D. schema / registry / event callback 状态（W5/W6/W7 域，本票保留）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| D1 | `ManagedCallbackSchemaRegistry.SCHEMA`（volatile，install 于 `NekoPluginRuntime.installManagedCallbackSchemas`） | `common/src/main/java/com/tkisor/nekojs/api/event/ManagedCallbackSchemaRegistry.java:53` | domain adapter-owned（W5 Managed Surface + Probe） | 进程（publish 派生） | 保留（AC8 计数点：install 只一次） | 有（`ManagedBindingSchemaTest` 等 schema 族） |
| D2 | `EventSchemaRegistry.SCHEMA` | `common/src/main/java/com/tkisor/nekojs/api/event/EventSchemaRegistry.java:9` | domain adapter-owned（W5） | 进程 | 保留 | 有（EventSchema 族） |
| D3 | `ScriptBindingSchema.SCHEMAS/GLOBALS` | `common/src/main/java/com/tkisor/nekojs/api/event/ScriptBindingSchema.java:14,20` | domain adapter-owned（W5） | 进程 | 保留 | 有（ScriptBindingSchemaInferTypeTest） |
| D4 | `ScriptEventRegistry.DEFINITIONS` | `common/src/main/java/com/tkisor/nekojs/api/event/ScriptEventRegistry.java:13` | domain adapter-owned（W5/W7 事件面） | 进程（reload 时 clearListeners 按 type） | 保留 | 有（ScriptEventRegistryTest、ScriptEventDefinitionClearListenersTest） |
| D5 | bindings EventGroup/EventBusJS static 实例（`ServerEvents.*`、`BlockEvents.*`、`RegistryEvents.REGISTER`、`ScriptEvents.*`、fabric `FabricServerEventBindings.SERVER_EVENTS` 等；含 `EventBusJS.externalCancellabilityPredicate`） | `src/main/.../bindings/event/*`、`common/.../api/event/EventBusJS.java:42`、`versions/26.1.2-fabric/.../event/*` | domain adapter-owned（W7：事件面迁移）；监听器实际生命周期由 `ScriptEventBridge.clearListeners(type)`（root close 调）与脚本 reload 管 | 进程容器 / per-reload 内容 | 保留：**root close 已清 bridge 监听器**（`NekoRuntimeRoot.closeSilently` :129-136），本票验证 close 冲刷语义即可；buses 迁移归 W7 | 有（EventBusJSHasListenersTest 等） |
| D6 | `RecipeTypeDefinitionStorage`（dataDriven/autoDiscovered/pluginOverrides/scriptSchemas） | `common/src/main/java/com/tkisor/nekojs/api/recipe/definition/RecipeTypeDefinitionStorage.java:14-17` | domain adapter-owned（W6 registry/recipe） | 进程（资源 reload 期 replace） | 保留 | 有（RecipeTypeDefinitionStorageTest） |
| D7 | `RecipeJsonTypeCatalog.CATALOG` | `common/src/main/java/com/tkisor/nekojs/api/recipe/RecipeJsonTypeCatalog.java:12` | domain adapter-owned（W6） | 进程 | 保留 | 有（RecipeJsonTypeCatalogTest） |
| D8 | `IngredientActionRegistry.ACTIONS` | `src/main/java/com/tkisor/nekojs/api/recipe/IngredientActionRegistry.java:45`（每轮 recipe 脚本前 clear，`RecipeManagerMixin.java:112`） | domain adapter-owned（W6/W7） | per-recipe-run | 保留 | 间接 |
| D9 | `NekoRecipeNamespaces.RECIPE_TYPES_BY_HANDLER` | `common/src/main/java/com/tkisor/nekojs/api/recipe/NekoRecipeNamespaces.java:19` | domain adapter-owned（W6） | 进程 | 保留 | 间接 |

## E. loader 侧 server/level/event/registry/network 状态

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| E1 | `RegistryEventAdapter.REPOSITORY/PASSED/collected` | `src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java:42-44` | domain adapter-owned（W6 Registry Runtime）；loader 接线期收集/抽干 | 进程（启动期一次性） | 保留（REGISTRY_STARTUP 只消费 root 启动时机，不阻塞本票） | 间接 |
| E2 | `FabricRegistryAdapter.REPOSITORY/collected` | `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java:24-25` | 同上（fabric 单批直注） | 进程 | 保留 | 间接 |
| E3 | `ServerEventListener.schemaAutoDiscovered`（src/main 与 versions/1.21.1 孪生） | `src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java:41`、`versions/1.21.1/src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java:39` | loader-owned（一次性 discovery flag） | 进程 | 保留：one-shot 守卫，无 owner 冲突 | 间接 |
| E4 | `VillagerTradeManager`（PENDING/HOLDER_SNAPSHOTS/ORIGINALS/PREVIOUSLY_REGISTERED/snapshotEpoch；1.21.1 孪生） | `src/main/java/com/tkisor/nekojs/villager/VillagerTradeManager.java:87-99` | domain adapter-owned（W7 villager trades → ticket 22） | 服务器会话（server start/reload/stopped 周期） | 保留 | 间接 |
| E5 | `ScriptPackDataManager.installedRepository/activeEntries/activeSignature` | `src/main/java/com/tkisor/nekojs/resource/ScriptPackDataManager.java:64-66` | domain adapter-owned（pack 域，03 冻结数据面） | 服务器会话（mount/reset 周期） | 保留 | 间接（datafix 契约） |
| E6 | `ScriptPackRegistry.INSTANCE` | `common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java:29` | process-owned（包注册表单例） | 进程 | 保留（pack 域 owner，非 runtime owner） | 间接 |
| E7 | fabric 侧 server 引用：`FabricServerEventBindings.currentServer/PENDING_LOGINS`、`FabricPlayNetwork.currentServer/lastClientLevel`、`FabricPDataSync.currentServer/lastClientLevel`、`FabricEntityEventBindingsV2.UNLOAD_REGISTERED` | `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java:111,118`、`FabricPlayNetwork.java:35,38`、`FabricPDataSync.java:31,34`、`FabricEntityEventBindingsV2.java:38` | loader-owned（fabric 无 `ServerLifecycleHooks`，进程内当前 server 引用即 loader 接线） | 进程（随 server 实例换值） | 保留：平台接线差异，删除条件 = 平台 Adapter 统一 currentServer 面（W7/W8） | 间接（fabric 烟测） |
| E8 | `LootTableEventJS.PENDING_SET/PENDING_REMOVE/LOADED_TABLES/REGISTRIES` | `src/main/java/com/tkisor/nekojs/wrapper/event/server/LootTableEventJS.java:40-46` | domain adapter-owned（W7） | 服务器会话 | 保留 | 间接 |
| E9 | `ItemModificationEventJS.SNAPSHOTS`、`BlockModificationEventJS.SNAPSHOTS` | `src/main/java/com/tkisor/nekojs/wrapper/event/server/ItemModificationEventJS.java:50`、`BlockModificationEventJS.java:57` | domain adapter-owned（W7 / ticket 39 modification） | 服务器会话（快照/重放） | 保留 | 间接 |
| E10 | `GoalRegistry.GOALS/APPLIED_JOIN_GOALS/TARGET_CLASSES` | `src/main/java/com/tkisor/nekojs/wrapper/entity/GoalRegistry.java:70-71,129` | domain adapter-owned（W7） | 进程容器 / 实体级 APPLIED | 保留 | 间接 |
| E11 | `DynamicRegistries.ITEM_SPECS`、`DynamicRegistryJS.INSTANCE`、`RegistrySurgery.FIELD_CACHE`、`NeoForgeRegistryQueryService.INSTANCE+registryKeysById` | `src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistries.java:62`、`DynamicRegistryJS.java:57`、`RegistrySurgery.java:48`、`api/registry/NeoForgeRegistryQueryService.java:38,50` | domain adapter-owned（W6 dynamic registry → tickets 16/21） | 进程 | 保留 | 间接 |
| E12 | `HolderAdapter.registryAccess`（volatile） | `src/main/java/com/tkisor/nekojs/js/type_adapter/HolderAdapter.java:37` | loader-owned（MC RegistryAccess 缓存） | 进程（随 registry 换值） | 保留：MC wrapper 域，非 lifecycle owner | 间接 |
| E13 | `KeyBindEvents.BINDINGS/activeKeyMappingsEvent/gameBusSubscribed/modBusSubscribed`、`KeyBindIds.CUSTOM_CATEGORIES/INSTALLED_CATEGORIES`（+fabric 孪生 `versions/26.1.2-fabric/.../KeyBindEvents.java:62`） | `src/main/java/com/tkisor/nekojs/bindings/event/client/KeyBindEvents.java:116-127`、`KeyBindIds.java:23-25` | domain adapter-owned（W7 / ticket 26 client input） | 进程（客户端） | 保留 | 间接 |
| E14 | `PostEffectManager.DEFINITIONS/POST_CHAIN_CACHE`（+1.21.1 孪生 lastSetId） | `src/main/java/com/tkisor/nekojs/client/posteffect/PostEffectManager.java:57,209` | domain adapter-owned（ticket 28） | 进程（客户端） | 保留 | 间接 |
| E15 | `ClientRenderRegistry.HUD_RENDERERS/WORLD_RENDERERS`、`WorldRenderContextJS.bufferSource/bufferSourceResolved/degradeWarned` | `src/main/java/com/tkisor/nekojs/client/render/ClientRenderRegistry.java:44-45`、`WorldRenderContextJS.java:32-34` | domain adapter-owned（ticket 27） | 进程（客户端） | 保留 | 间接 |
| E16 | `NativeEventsJS.REGISTERED_LISTENERS`（+CLASS_CACHE/CLASS_MISS_CACHE） | `src/main/java/com/tkisor/nekojs/bindings/static_access/NativeEventsJS.java:40,169,176` | domain adapter-owned（W7）；**close 相关**：`clearRegisteredListeners`(:45-48) 清脚本注册的原生监听器 | 进程容器 / per-reload 内容 | 保留：清空路径随 reload/close（W7 验证）；本票验证 close 冲刷不回归 | 间接 |

## F. 数据面（03 号票已冻结，本票不改；reload/close 行为只在烟测确认）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| F1 | `PDataSyncService.DIRTY_ENTITIES/SERVER_REVISIONS/CLIENT_ENTITY_MIRROR/CLIENT_REVISIONS` | `src/main/java/com/tkisor/nekojs/wrapper/pdata/PDataSyncService.java:27-30` | domain adapter-owned（pdata 域，03 冻结） | 世界/会话 | 保留；tick flush 经 `PDataSyncListener`（本票迁句柄） | 有（datafix 契约测试，03） |
| F2 | `ClientDataStore.SHARED` | `src/main/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataStore.java:39` | process-owned（客户端 KV，断线清） | 进程/会话 | 保留 | 间接 |
| F3 | `EntityPDataStore.current`（volatile Access，loader entry `install(...)`） | `common/src/main/java/com/tkisor/nekojs/api/inject/EntityPDataStore.java:26`；`NekoJSMod.java:89-111` | loader-owned（平台容器桥，loader 装配期 install 一次） | 进程 | 保留：平台差异桥（NeoForge 容器语义），删除条件 = 平台 Adapter 收口（W7） | 有（datafix，03） |
| F4 | `PackSyncClient.clientReloadHook/mainThreadLatch/activeBucket/activeAddress/expectedHashes` | `common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java:43-50`；NeoForge 侧 `PackSyncClientConnections.install()`（`src/main/.../network/NekoJSNetwork.java:49`）、fabric 侧 `FabricPackSync` | domain adapter-owned（pack sync 域，03 冻结）；**lifecycle 相关**：`clientReloadHook` 是 loader 注入的 CLIENT reload 钩子（现闭包直读 static root） | 进程 | **收口**：Phase 3 把 hook 闭包改为 loader entry 传 root 的窄 handle（`reloadClientScripts`），语义不变 | 间接 |
| F5 | `PackSyncTrustStore.instance`（volatile） | `common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncTrustStore.java:38` | process-owned（trust 存储，03 冻结） | 进程（客户端进程） | 保留 | 有（datafix，03） |
| F6 | `NekoJSPackSource.PACK_SOURCE_NEKO` | `src/main/java/com/tkisor/nekojs/resource/NekoJSPackSource.java:11` | process-owned（PackSource 常量） | 进程 | 保留 | 间接 |

## G. client 侧杂项（保留）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| G1 | `NekoSecurityWarningHandler.titleWarningShown/chatWarningShown/checkedHost/needsWarningThisSession` | `src/main/java/com/tkisor/nekojs/client/NekoSecurityWarningHandler.java:22-26` | domain adapter-owned（客户端一次性提示） | 会话 | 保留 | 无 |
| G2 | `NekoReloadProgressHud.installed` | `src/main/java/com/tkisor/nekojs/client/hud/NekoReloadProgressHud.java:37` | loader-owned（install-once 守卫） | 进程 | 保留 | 无 |
| G3 | `ReloadProgressTracker.STATE/SESSIONS/clock` | `common/src/main/java/com/tkisor/nekojs/core/lifecycle/ReloadProgressTracker.java:47-53` | domain adapter-owned（reload HUD 面，进程级快照） | 进程 | 保留 | 有（ReloadProgressTrackerTest） |
| G4 | `InventoryChangeListener.CACHE`（WeakHashMap per-player） | `src/main/java/com/tkisor/nekojs/listener/InventoryChangeListener.java:25` | domain adapter-owned（W7 inventoryChanged） | 会话（玩家生命周期） | 保留 | 间接 |
| G5 | `OnceJS.ONCE/CLEAR_ONCE`、`OnceRegistry.SHARED` | `common/src/main/java/com/tkisor/nekojs/bindings/static_access/OnceJS.java:33,36`、`core/lifecycle/OnceRegistry.java:19` | process-owned（脚本 binding 单例） | 进程（脚本语义） | 保留 | 有（OnceRegistryTest） |

## H. 错误 / 诊断 / telemetry / probe（保留）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| H1 | `Diagnostics.sink` | `common/src/main/java/com/tkisor/nekojs/core/error/Diagnostics.java:31` | process-owned（诊断 sink 门面） | 进程 | 保留 | 间接 |
| H2 | `JavaClassLoadTelemetry.sink/CURRENT`（ThreadLocal） | `common/src/main/java/com/tkisor/nekojs/core/JavaClassLoadTelemetry.java:7-8` | process-owned（telemetry） | 进程/线程 | 保留 | 间接 |
| H3 | `SourceMapRegistry.MAPPINGS_MAP/cachedRootUri` | `common/src/main/java/com/tkisor/nekojs/core/error/SourceMapRegistry.java:20,26` | process-owned（错误定位缓存） | 进程 | 保留 | 间接 |
| H4 | `NekoJSLoggers.CACHE/APPENDERS` | `common/src/main/java/com/tkisor/nekojs/core/log/NekoJSLoggers.java:41,49` | process-owned（logger 缓存） | 进程 | 保留 | 间接 |
| H5 | `ProbeCoordinator.DEFAULT`、`ProbeBackendRegistry.INSTANCE`、`ProbeConfig.PATTERN_CACHE` | `common/src/main/java/com/tkisor/nekojs/probe/ProbeCoordinator.java:88`、`ProbeBackendRegistry.java:22`、`ProbeConfig.java:145` | process-owned（probe 域） | 进程 | 保留 | 有（probe 族测试） |

## I. 工具单例 / 纯缓存（保留，无 lifecycle 语义）

| # | 符号 | 位置 | owner 分类 | 生命周期 | 可替换性 / 本票动作 | 测试覆盖 |
|---|---|---|---|---|---|---|
| I1 | `ClassFilter.INSTANCE`（有意单例，注释声明） | `common/src/main/java/com/tkisor/nekojs/core/fs/ClassFilter.java:35`；装配复用（`NekoJSMod.java:152` 注释：避免双实例状态分裂） | deliberate singleton（W4 域） | 进程 | 保留：装配函数继续复用 `ClassFilter.INSTANCE`（sandbox config 装载到同一实例），不制造第二份 | 间接（sandboxCheck） |
| I2 | `ScriptContextRegistry`（4 个 synchronized WeakHashMap） | `common/src/main/java/com/tkisor/nekojs/script/ScriptContextRegistry.java:28-46` | domain adapter-owned（W4：Script Execution Environment） | 进程容器 / Context 级（weak，随 Context 回收） | 保留：Context 生命周期即 key 生命周期 | 无 |
| I3 | `NekoGlobal.SHARED`（`global` binding 背书，跨 reload 存活） | `common/src/main/java/com/tkisor/nekojs/bindings/static_access/NekoGlobal.java:26` | process-owned（decision 10：global 语义；W5/ticket 10 处理 candidate 事务） | 进程（跨 reload） | 保留：**本票不重开决策 10**；06/07 candidate 事务时再动 | 无（ticket 10 的 fixture 前置） |
| I4 | `JavaMemberIndex` 4 个 ConcurrentHashMap 反射缓存 | `common/src/main/java/com/tkisor/nekojs/api/JavaMemberIndex.java:36-38,248` | process-owned 纯缓存（不可变值缓存） | 进程 | 保留 | 有（JavaMemberIndexTest） |
| I5 | 其余纯缓存：`IngredientResolver.REGEX_CACHE`、`FluidResolver.REGEX_CACHE`、`RegistrySurgery.FIELD_CACHE`（见 E11）、`NativeEventsJS.CLASS_CACHE/CLASS_MISS_CACHE`、`ProbeConfig.PATTERN_CACHE`（见 H5） | 各文件 | process-owned 纯缓存 | 进程 | 保留 | 间接 |
| I6 | 不可变常量类 INSTANCE/DEFAULT/GROUP 字段（`NullJsValueView`、`ConversionContext.EMPTY`、`NbtBinaryLimits.DEFAULT`、`CjsModuleRecord.EMPTY`、各 codec INSTANCE 等） | 各文件 | 非可变状态（排除项） | — | 不归账（记录排除口径） | — |
| I7 | `NekoRegistryPointsPlugin.infosHandle/typesHandle` | `src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryPointsPlugin.java:32-33` | process-owned（plugin 扩展点 handle 缓存） | 进程 | 保留（W2 域） | 间接 |
| I8 | registry builder 攒批容器：`EntityTypeBuilder.REGISTERED/SPAWN_EGGS`、`ItemBuilder.GROUP_ASSIGNMENTS/FUEL_ASSIGNMENTS`、`BlockBuilder.RENDER_TYPES`、`BuilderTags.PENDING` | `src/main/java/com/tkisor/nekojs/wrapper/registry/gen/*`、`BuilderTags.java:36` | domain adapter-owned（W6：先攒后建） | 进程（启动期攒批） | 保留 | 间接 |
| I9 | `PlayPacketDispatchers.current/warned` | `src/main/java/com/tkisor/nekojs/network/PlayPacketDispatchers.java:35-36` | loader-owned（network 注册句柄，NOOP 直到 install） | 进程 | 保留（network 注册计数属 AC8 烟测观察点） | 间接 |
| I10 | `ClientReloadExecutor`（无 static 字段，静态方法门面） | `src/main/java/com/tkisor/nekojs/client/ClientReloadExecutor.java` | loader-owned（CLIENT 线程归属策略门面） | 进程 | 保留：命令侧 CLIENT reload 转投 Render 线程的策略不动（行为保持） | 无 |

## J. test-only seam（本票不强迁）

- `common/src/test` 中直接构造 `NekoPluginRuntime`/registry 的测试不经 static root；生产 static 例外（B1/B2/B4）在测试里以 set/publish 显式注入——本票 AC2 新增测试沿用该 seam，不迁移任何 test 代码。
- `versions/*/build/generated/stonecutter` 下同名生成副本不在账内（生成证据，非第二事实源）。

## K. 分类统计

| 分类 | 条目数（簇） | 本票动作 |
|---|---|---|
| 待删除 legacy bypass（static root / 重复 manager 容器 / legacy pipeline binding） | A1、A2、A3、A7（4） | 迁移后删除（A7 仅收口，字段删除归 W3） |
| loader-owned（保留，接线面） | A4、E3、E7、G2、F3、I9、I10（7） | 保留；其中 F4 hook、E7 reload 闭包改经窄 handle |
| process-owned 例外（AC2 点名/派生） | B1、B2、B3、B4、A10、C1-C3、D1、E6、F2、F5、F6、G5、H1-H5、I3、I4、I7（约 20） | 保留 + 补可重复测试（B1/B2/B4） |
| root-owned（或 root 派生静态面） | A5、A6（2） | 保留；close 释放验证 |
| generation-owned（06/07） | A9（1） | 本票不动 |
| domain adapter-owned（W2–W7 功能域） | A8、D2-D9、E1、E2、E4、E5、E8-E16、F1、F4、G1、G3、G4、I2、I8（约 24） | 保留 |
| deliberate singleton | I1（1） | 保留（装配复用） |
| 非可变常量（排除） | I6 | 不归账 |

## L. AC1 维度覆盖映射

- runtime access → B1；plugin runtime current → B2；plugin entries → B3（+I7 handle 缓存）；shared engine → B4；
- platform/path → C1-C3；compiler/pipeline → A7-A10；schema → D1-D3、D6-D7；event callback → D4-D5、E16；
- loader 侧 server/level/event/registry → E1-E13（server 引用 E7、registry E1/E2/E11、level/entity E8-E10、network I9）；
- 数据面（config/world/pdata/pack/trust/workspace 路径）→ C3、F1-F6（03 号票冻结，格式/key/wire 不变）。
