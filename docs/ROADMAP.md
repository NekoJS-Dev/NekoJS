# NekoJS 路线图

NekoJS 的目标是在 NeoForge（26.1 / 26.2 / 1.21.1）与 Cleanroom（1.12.2，Forge）上提供一个基于 GraalVM/GraalJS 的现代 Minecraft JavaScript 脚本运行时。当前方向是保持轻量、JSON-first、贴近 Minecraft / NeoForge 原生类型，同时吸收 KubeJS 中能明显改善脚本体验的 helper、wrapper、adapter 和调试能力。

## 当前设计原则

- [x] 保持 datapack JSON-first 的 recipe 设计，不迁移 KubeJS 完整 `RecipeSchema` / `RecipeComponent` / 自动 builder 生成系统。
- [x] 保留 vanilla Java 类名绑定，例如 `Item`、`ItemStack`、`Items`、`Ingredient`、`FluidStack`。
- [x] 额外能力优先通过 `ItemJS`、`Ingredient`、`Fluid`、mixin extension 和轻量 wrapper 提供。
- [x] 避免把 Graal `Value` 暴露为常规脚本 API 参数，优先使用 Java 类型或函数式接口。
- [x] 平台相关验证优先编译两个平台模块，不单独编译 `common`。

## 2026-07 近期更新

### 近期完成

- [x] **架构治理（2026-07-24）**：
    - 移除已失效的 `architectureCheck` 正则计数任务（`gradle/architecture-check.gradle` + 根 `apply from`），架构迁移改由代码评审、定向测试和 `ai_arch/plan.md` 清单跟踪。
    - **Probe 替换机制修复**：内置 `ProbeOrchestrator` 改为 fallback（`ProbeRegistry.setFallback`），单个第三方实现（`setGenerator`）现在能真正替换它；多个第三方实现仍确定性冲突。`ProbeRegistry` 解耦 `NekoJS`，改用 JDK `java.util.logging`。新增 `ProbeRegistryTest` 覆盖 fallback 生效、第三方替换、多方冲突、锁后注册。
    - **事务式完整 reload**：SERVER/CLIENT/TEST 的 `reloadScripts` 改为先在候选 Context 加载、成功才切换并关闭旧 Context；失败时丢弃候选并保留旧 Context，避免旧实现「先销毁旧环境再加载」造成的半失效崩溃。STARTUP 因涉及不可逆注册保持 reset+load 语义。`runTestScripts` 同样改为事务式。
    - **HostAccess 初始化顺序修复**：`NekoSharedHostAccess` 从静态单例改为构造注入实例，基于已冻结的 adapter snapshot 构建，消除类初始化时机固化 adapter 风险。
    - **Probe 原子输出**：`ProbeOrchestrator.generate` 改为生成到 staging 目录、成功后整体替换 outputDir，失败保留旧声明；入口恢复崩溃残留的 staging/backup 中间态。
    - **NeoForge 26.1/26.2 共享源码**：新建 `platforms/neoforge-26-shared/`，迁入 142 个共享 Java + 共享资源/模板，两平台用 srcDir 引入；4 个 API 重命名差异文件 + mixins.json + 独有文件保留本地。
    - **NeoForge 脚本网络功能补全**：原 `NetworkJS` 发送 `NekoScriptPayload` 但未注册、无接收侧，脚本网络通信完全失效。新建 `NetworkEvents`（server/client dispatch bus）、`NetworkDataEventJS`（getter 与 Cleanroom 一致，保证跨平台 API 一致）、`NetworkMessageHandler`（主线程切换后分发）；26.1/26.2 注册 payload 双向。脚本：`NetworkEvents.server("ch", e=>...)` / `NetworkEvents.client("ch", e=>...)`。
    - **workspace ESM 对齐 / 死代码清理 / Gradle 去重**：`JSConfigModel.module` 从 `"CommonJS"` 改为 `"ESNext"`（脚本生态本就 ESM-first）；删除 `TypeOutputLayout.typeRoot()` 死方法（返回陈旧 `probe-types`、实际为 `@side-only/<type>`、零调用）；新建 `gradle/neoforge-common.gradle` apply script 下沉三 NeoForge 平台字节级相同的 base/repositories/jar/publishing/unifiedPublishing/idea 块，各平台仅 `ext.minecraft_version`/`ext.ENV` 暴露参数、版本与差异块本地维护。四平台编译 + jar 产物名验证通过。
    - **NeoForge 配方热重载 + KubeJS 工具（JsonIO/global）**：`RecipeManagerMixin` 加永久 `baseJsons` 缓存让 `nekojs$applyScripts()` 可重入，`/nekojs reload server` 后自动重新应用配方脚本（26-shared + 1.21.1）；common 新增 `JsonIO`（gson，parse/toString/read/write）与 `global`（跨 ScriptType 进程级共享 Map）绑定。NBTIO / Pos adapter / 缺失事件（inventoryChanged/foodEaten/randomTick）留后续批次。
    - **KubeJS API 扩充（TimeJS / Pos adapter / foodEaten）**：`TimeJS` 加 `parseTime`/`parseMs`（`"5s"`→tick/ms）；26-shared + 1.21.1 新增 `BlockPosAdapter`/`Vec3Adapter`（`{x,y,z}` / `[x,y,z]` 输入）与 `ItemEvents.foodEaten`（基于 `LivingEntityUseItemEvent.Finish`）。NBTIO、`inventoryChanged`/`randomTick`（需 mixin）、TextJS（Component builder）、cleanroom 同步留下批。
    - **HostAccess 初始化顺序修复**：`NekoSharedHostAccess` 从类初始化静态单例改为构造注入实例（`new NekoSharedHostAccess(adapters)`），由 `NekoSandboxFactory` 持有，基于已冻结的 `IPluginRuntime.adapters()` snapshot 构建，不再有「类初始化时机固化 adapter」风险。
    - **静态状态收敛**：`ScriptManager` 直接注入 `IPluginRuntime`，生命周期核心路径不再调用 `NekoRuntimeAccess.get()`；`NekoSandboxFactory` 直接注入 `NekoJSPaths`。保留 `NekoRuntimeAccess`、`ScriptType.path` 等简洁门面，不追求零 static。
- [x] **插件系统增强**：`@RegisterNekoJSPlugin` 增加 `priority`（默认 1000，**数值大先加载**）/ `clientOnly` / `requiredMods`（AND 语义）；`NekoJSBasePluginManager.registerClass` 统一负责按 priority 排序 + clientOnly/requiredMods 过滤 + 实例化，过滤逻辑从 4 平台 PluginLoader 上提到 common；内置 CorePlugin 用 `NekoJSPlugin.CORE_PRIORITY`（`Integer.MAX_VALUE`）确保最先注册基础设施。
- [x] **probe 内置 TypeScript 声明生成**：`ProbeOrchestrator` 直接遍历 `NekoScriptCatalog` 在 `.neko_probe/` 下生成 `.d.ts`（`@package` java alias、`@side-only` events/bindings、`@special` registry 字面量类型、recipe schema 带类型重载），无需外部 ProbeJS mod。recipe schema 由 `MinecraftRecipeSchemaScanner` 扫描 `RECIPE_SERIALIZER` 的 MapCodec 自动发现（下方 Recipe 重构「阶段 1 原版 schema 自动生成」已完成）。
- [x] **jsconfig 自动生成**：`WorkspaceGenerator` 为每个脚本目录和 `.neko_probe/` 生成 `jsconfig.json`，paths 映射 `java:` / `@side-only` / `@special` 模块说明符，让 VSCode 等编辑器解析 probe 声明；`/nekojs probe` 末尾幂等补全这些配置。
- [x] **配方热重载（Cleanroom 1.12.2）**：`/nekojs reload server` 解冻 ForgeRegistry → 移除旧 nekojs 配方 → 重跑配方脚本 → 重新冻结，并通过 `HeiRefresher`（`JeiProxyAccessor` mixin @Accessor，类型安全非反射）自动刷新 HEI/JEI 面板。
- [x] **健壮性修复**：`Platform.INSTANCE` 加 `volatile`（跨线程可见性）；`ScriptManager.reloadScripts` / `RegistryEventListener.reloadRecipes` 加重入保护（`synchronized`）；`EventBusJS` 4 处 catch 不再吞 `InterruptedException`/`Error`。
- [x] **common 上提**：`WorkspaceGenerator`、`PluginLoader` 的过滤+实例化、`RecipeRegistryProxy` 常量（`RecipeRegistryKeys`）从平台层上提到 common；`postModifyWorkspaceConfigEvent` 平台 event bus 机制改为 `NekoJSPlugin.modifyWorkspaceConfig` 钩子，删除 4 平台 `ModifyWorkspaceConfigEvent` 死代码。
- [x] **事件契约（portable-core 0.8.0）**：契约模型新增 `events` 字段（schemaVersion 1→2），约定 34 个跨平台事件（ScriptEvents 2 + ServerEvents 7 + LevelEvents 5 + PlayerEvents 18 + CommandEvents 2）的名称/分发/可取消性/可移植 payload 视图；三平台取消性经 GitHub 源码核实（chat/command/entityInteract 可取消），payload 属性按 Graal 属性访问一致性核实（`server`/`message`/`username` 承诺，`level`/`world`/`command` 命名不一致不承诺）；`ApiContractReader` 增加事件语义校验（重复/分发键/字段类型）。平台独有事件（datapackSync/lootTables/tags 等）留作平台事实 API。
- [x] **T2 RegistryEvents 契约 + 跨平台 create(id, consumer) 统一（portable-core 0.9.0）**：契约追加 10 个 registry 事件（item/block/entityType/fluid/creativeModeTab/soundEvent/mobEffect/potion/villagerType/enchantment，STARTUP/PLAIN，payload `create` CALLBACK 字段）；NeoForge 7 个原缺 Consumer 重载的事件补 `create(String, Consumer)`（item/creativeModeTab/soundEvent/mobEffect/potion/villagerType/enchantment），与 cleanroom + block/entityType/fluid 对齐；cleanroom `ItemJS` 补 `id`/`idOf` + 新建 `BlockJS`（id/idOf）实现三平台 `Item.id`/`Block.id` 一致。particleType/paintingVariant 不进契约（cleanroom 无）。
- [x] **代码清理**：消除 10 处 `printStackTrace`、删除 `.jswrapper` 空垃圾目录、cleanroom 的 `NativeEventsJS`/`ScriptEventsJS` 桩改为显式 `UnsupportedOperationException`。

### 下一阶段待办（按 ROI 排序）

- [x] **上层 KubeJS 风格 API 补全**（用户迁移最卡的高 ROI 项）：全局 `setTimeout`/`setInterval`（timers shim 已有，注册为全局绑定）、`Java` 绑定、`Block.id(s)`/`Item.id(s)` 查找（三平台均有）；`BlockState` 输入 adapter（`minecraft:stone[prop=val]`）；`BlockEvents.randomTick`/`blockEntityTick`（NeoForge 通过 mixin 注入 `BlockBehaviourMixin`/`LevelChunkBoundTickingBlockEntityMixin`，事件类 + binding 注册全到位）。已补齐：`global` 共享 map、`JsonIO`、`NBT`（含文件读写 `NBT.read`/`NBT.write`、`parse`/`toObject`/`fromObject`，沙箱化 gzip 二进制 NBT）、`BlockPos`/`Vec3` adapter、`PlayerEvents.inventoryChanged`、`ItemEvents.foodEaten`、富文本 `TextJS`（链式 color/bold/italic/underlined/strikethrough/obfuscated/insertion/font/click/hover/append/translatable，不可变设计）。**次要功能缺口**（非阻塞，后续按需补）：TextJS 的 `keybind`/`score`/`selector`/16 色快捷方法/`translateWithFallback`/`join`；cleanroom `ItemEvents.tooltip` tier 分歧（CLIENT/NeoForge vs SERVER/cleanroom，未进契约）。
- [x] **`ServerEvents.tags`**：tag 修改事件（`add`/`remove`/`replaceAll`）。NeoForge 1.21.1/26.x 经 mixin 注入 `TagLoader.build` 实现完整 `TagEventJS`；cleanroom 1.12.2 用 OreDictionary 适配（dispatch 键固定 `'ore_dict'`，`add`/`remove`/`removeAll`/`replaceAll`/`getEntries`，在 `serverAboutToStart` 触发一次）。跨平台语义边界已在 wiki 记录；tags 不进 portable-core 契约（平台特定，与 recipes/lootTables 同级）。
- [x] **NeoForge 26.1/26.2 共享源集（2026-07-24）**：新建 `platforms/neoforge-26-shared/`（纯目录，不进 settings.gradle），迁入 142 个字节相同/差异可忽略的 Java（140 字节相同 + `NekoJSMod` 取 26.2 干净版 + `RecipeEventJS` 取 26.2 版）+ 共享 `accesstransformer.cfg`/`interface_injection.json`/`neoforge.mods.toml`。26.1/26.2 用 `srcDir '../neoforge-26-shared/...'` 引入；4 个 MC API 重命名差异文件（`EntityType`↔`EntityTypes`、`getApiDescription`↔`getBackendDescription`、`screen`↔`gui.screen()`、`getToastManager`↔`gui.toastManager`）+ `nekojs.mixins.json` + 独有文件保留本地。clean 重建后两平台 jar 产物完整（26.1=566 类、26.2=564 类）。
- [ ] **NeoForge 1.21.1 共享评估**：1.21.1 与 26.x 有 92 个同路径文件不同（API 演进跨度大），强合并需大量条件编译或抽象，ROI 低；暂不纳入共享，后续单独评估。
- [x] **NeoForge 配方热重载**：`RecipeManagerMixin` 永久缓存数据包阶段的原始配方 JSON（`baseJsons`），`ReloadableServerResourcesMixin` 在资源 reload 收尾、组件/标签绑定后触发 `nekojs$applyScripts()`；`/nekojs reload server` 在脚本 reload 后主动调用，从 `baseJsons` 重建工作集、重跑 `ServerEvents.recipes`/`afterRecipes` 脚本，并用脚本产出 JSON 重新解析替换 `RecipeManager.recipes`（可重入）。3 平台（26.1/26.2/1.21.1）均实现。
- [ ] **测试覆盖**：common / common-api 已有较完整的纯函数测试（路径穿越、adapter 转换、facade 行为、契约符号计数、NBT SNBT 往返等）；仍缺平台模块的可运行测试与运行时集成验证。后续优先补平台侧 smoke 测试脚本统一化与 CI 门禁。

## 已完成的基础能力

- [x] 独立 GraalJS Context + 共享 Graal Engine 的脚本运行基础。
- [x] `startup_scripts/`、`server_scripts/`、`client_scripts/` 脚本目录。
- [x] `.js/.mjs/.cjs` 直接执行；`.ts` 由内置 erasable TypeScript 前端支持；`.jsx/.tsx` 由内置轻量 classic runtime lowering 支持；后续高级 TS/TSX/JSX 语法优先收敛到 NekoJS 本体语言前端。
- [x] 核心事件组、绑定注册、脚本属性和插件注册机制。
- [x] 工作区生成、游戏内工作区 UI、脚本同步和基础编辑体验。
- [x] `NekoId` 稳定脚本侧 ID 类型，替代 `IDJS.of` 返回 `Object` 的旧设计。
- [x] `JSTypeAdapter.getPrecedence()`，降低宽泛 adapter 参与 Graal overload resolution 时的歧义。
- [x] 26.1 早期 reload 阶段的 `ItemStack` 安全构造，避免 `Components not bound yet`。
- [x] CI Build workflow：编译/构建两个平台模块，区分 dev/release artifacts，并用 commit 首行提取 release version / summary。
- [x] `RecipeJsonValue` 边界类型：recipe builder/custom 的任意 JS JSON 输入收敛到 adapter/converter 层，builder 不直接暴露 Graal `Value` / 宽泛 `Object`。
- [x] 脚本事件错误日志增强：recipe/event/timer callback 错误同时写 script logger 与主 NekoJS logger，并显示 JS 行列、上下文和列指针；错误追踪按脚本路径替换/清理，避免同一文件修改或重跑后 stale error 累积。
- [x] `ScriptEvents` startup API：startup scripts 可把 NeoForge 原生事件注册成 server/client 脚本侧事件方法，并接入 reload listener 清理；“任意脚本类型”语义由 `ScriptTypePredicate.any()` 表达，不再使用 `ScriptType.COMMON`。

## 已完成的 KubeJS-lite API 迁移

- [x] `ItemJS.of(...)` 返回 `ItemStack`，支持脚本友好的物品栈创建。
- [x] `ItemStack` mixin extension：`withCount`、`copy`、`getId`、`getMod`、`getBlock`、`enchant`、`hasEnchantment`、`matches`、`asIngredient`、`weakNBT`、`strictNBT` 等。
- [x] `Ingredient` helper：`of`、`item`、`tag`、`any`，并明确暂不支持 `not` 的假语义。
- [x] `IngredientFactory` wrapper：`or`、`and`、`intersect`、`except`、`subtract`、`matches`、`first`、`stacks`、`displayStacks`、`withCount`。
- [x] `SizedIngredientJS` 与 `SizedIngredientAdapter`。
- [x] `Fluid` / `FluidIngredient` / `SizedFluidIngredient` 相关 helper、wrapper 和 adapter MVP。
- [x] recipe JSON builder、recipe entry wrapper、filter、递归 `replaceInput` / `replaceOutput`。
- [x] Recipe lifecycle hooks：外部插件可通过 `registerRecipeLifecycleHooks` 或 `RecipeLifecyclePlugin` 注册 `beforeRecipeLoading` / `afterRecipes`；脚本侧可用 `ServerEvents.afterRecipes` 在普通 recipe 脚本后、最终 codec parse 前检查或最终改写 recipe JSON。
- [x] `RecipeJsonValueAdapter` / `RecipeJsonValueConverter`，支持 JS object/array 内嵌 `IngredientFactory`、`ItemStack`、fluid wrapper 等 recipe-aware JSON 序列化。
- [x] `event.recipes.minecraft` 常用 vanilla recipe helper。
- [x] `event.forEach(...)` 使用 `Consumer<RecipeEntryJS>`，不把 `Value callback` 作为常规 API。

### Recipe 系统重构（参考 KubeJS Schema，保持 NekoJS 轻量）

**目标**：让用户能像 KubeJS 一样用类型安全的方式调用配方类型，但不引入完整的 `RecipeSchema/RecipeComponent` 系统。

```js
// 目标体验
event.recipes.minecraft.crafting_shaped('result', ['AAA'], { A: '#planks' })
event.recipes.create.mixing('result', ['input1', 'input2'])
```

KubeJS 的做法是 `RecipeSchema(keys).constructors()`，每个 key 有 `RecipeComponent<T>` 定义类型+Codec。插件用 Java 代码注册 `namespace("minecraft").register("crafting_shaped", schema)`。

NekoJS 的做法是增强已有的 `RecipeTypeDefinition`（data-driven JSON + `RecipeFieldKind`），让它兼职 schema 角色：

```java
// 新设计：RecipeTypeDefinition 升级为轻量 Schema
RecipeTypeDefinition {
    type: "minecraft:crafting_shaped",
    keys: [
        { name: "result",   kind: ITEM_STACK,   role: OUTPUT },
        { name: "pattern",  kind: JSON,          role: INPUT },
        { name: "key",      kind: JSON,          role: INPUT },
        { name: "group",    kind: STRING,        role: OTHER, optional: true }
    ]
}
```

与 KubeJS 的关键差异：**NekoJS 用 data-driven JSON 定义 + 原版 Codec 自动推断，而不是 Java 代码手写 Schema**。

**实现阶段**：

- [x] **阶段 1 — 原版 schema 自动生成**: 启动时扫描 `BuiltInRegistries.RECIPE_SERIALIZER`，对每个 recipe type 从它的 Codec 推断出 field 结构。不需要手写 ~200 个 schema。（已实现：`common` 层 `RecipeSchemaAutoDiscovery` SPI + 各平台 `MinecraftRecipeSchemaScanner`，接入 `ServerEventListener`。）
- [x] **阶段 2 — 类型安全构造器**: `RecipeFieldDefinition`（per-field record）携带 `role`（INPUT/OUTPUT/OTHER）和 `optional`；`RecipeNamespaceProxy.resolveArgs()` 按构造器 arity 校验参数数量，`RecipeEventSchemaHost.encodeField` 校验参数类型，错误带 recipe type + 字段名 + 期望/实际 JS 类型。（注：错误尚无 JS 行号——见阶段 5。）
- [x] **阶段 3 — Codec 集成**: 序列化走原版 Codec（`Ingredient.CODEC`/`ItemStack.CODEC`/`FluidStack.CODEC`/`FluidIngredient.CODEC`/`SizedFluidIngredient.FLAT_CODEC`，`RecipeEventJS` serialize* 方法用 `encodeStart(JsonOps.INSTANCE)`）；kind→Codec 分派在平台 `RecipeEventSchemaHost.encodeField`（保持 loader 无关，cleanroom 有等价实现）。与 vanilla 格式完全一致。
- [x] **阶段 4 — Component-aware replacement**: `replaceInput`/`replaceOutput` 用 schema `role` 字段定位 INPUT/OUTPUT key（schema 已知配方得 role 精确 key，未知配方回退保守候选名），并用 Codec 反序列化后做 item stack 比较（支持复合 ingredient 部分匹配——修复 tag ingredient 多于匹配项的旧行为），`replacementWithCount` 保留 result.count。
- [~] **阶段 5 — 错误上下文**: `RecipeCreationContext` 携带 `api/type/prefix/scriptId`（`RecipeJsonBuilder.captureScriptId()` 从 Graal Context 捕获脚本 id），出错时输出 recipe type + 字段名 + script id。**JS sourceLine 未实现**：Graal proxy 调用路径（`ProxyExecutable.execute`）下，Java 侧无法直接获取调用点的 `SourceSection`——源位置只能从 `PolyglotException` 栈帧提取（仅抛异常时可用）。实现 sourceLine 需引入 Graal interop 管道（非小改），暂不纳入；scriptId 已足够定位脚本文件。`

**不采纳的部分**：
- 完整的 `RecipeComponent<T>` 类型系统（40+ Java 类）— 改用 `RecipeFieldKind` enum + data-driven JSON
- 自动 builder 方法生成 — 那是 Rhino `jsToJava()` 能力，NekoJS 用 `DataDrivenRecipeNamespaceProxy` 的 `ProxyExecutable` 模式
- Schema JSON loader — NekoJS 已有 `RecipeTypeDefinitionJsonLoader`

## common 迁移状态

### 已迁移或已经在 common 的能力

- [x] 事件基础设施：事件组、事件总线、脚本事件桥接所需的 common 抽象。
- [x] 脚本管理基础：`NekoJSScriptManager` 相关可共享逻辑。
- [x] 绑定聚合：`NekoBindings`。
- [x] 脚本同步通用逻辑：`ScriptSyncService`、`ScriptSyncFiles`、`ErrorSummaryDTO`。
- [x] 静态基础 helper：`IDJS`、`ColorJS`、`UUIDJS`、`StringUtilsJS`、`TimeJS`、`UtilsJS`。
- [x] `JSTypeAdapter` 和 `NekoSandboxBuilder` 的共享 adapter 注册机制。

### 可优先迁移到 common 的候选

- [ ] 事件声明类：`CommandEvents`、`EntityEvents`、`ItemEvents`、`LevelEvents`、`PlayerEvents`、`RegistryEvents`、`ServerEvents`。
- [ ] 纯 helper / static access：`ItemJS`、`FluidJS`、`FluidIngredientJS`、`NativeEventsJS`。
- [ ] 低版本差异 adapter：`JsonObjectAdapter`、`ComponentAdapter`、`CompoundTagTypeAdapter`、`IngredientAdapter`、`FluidStackAdapter`、`FluidIngredientAdapter`、`SizedFluidIngredientAdapter`、`SizedIngredientAdapter`、`RecipeFilterAdapter`。
- [ ] 低版本差异 wrapper：`NekoWrapper`、`RecipeRegistryProxy`、`FluidAmounts`、`FluidIngredientJS`、`SizedIngredientJS`。
- [ ] `MinecraftRecipeHandler`：先确认两个平台 recipe JSON 字段和 serializer helper 是否保持一致，再迁移。

### 需要 compat 层后再迁移的候选

- [ ] `ResourceLocationAdapter` / `IdentifierAdapter`：需要统一 `ResourceLocation` 与 `Identifier` 差异。
- [ ] recipe 核心类：`RecipeEventJS`、`RecipeEntryJS`、`RecipeJsonBuilder`、`RecipeFilter`、`RecipeJsonValue`、`RecipeJsonValueConverter`、`FallbackNamespaceProxy`。
- [ ] 物品/方块/实体/tag adapter：`ItemAdapter`、`BlockAdapter`、`ItemStackAdapter`、`EntityTypeAdapter`、`TagKeyAdapter`。
- [ ] `IngredientFactory` / `IngredientResolver`：需要隔离 `Ingredient` 展开、holder、component ingredient 的版本差异。
- [ ] 网络 packet record 与 `NekoJSNetwork`：需要先抽象 payload / channel 注册差异。

- [x] **common→platform 跨模块类型消除**: 尝试抽取 `RecipeEventContext` 接口到 common，但 handler 方法需要调用的 `RecipeJsonBuilder` / `serializeIngredient` 都是平台类型，common 无法引用。当前 `Function<Object,Object>` + `NekoJSCorePlugin` 中一处 cast 是现实约束下的最简方案。

### 暂不建议迁移的内容

- [ ] registry builder、registry event 和 food builder 等强平台 API 绑定代码。
- [ ] mixin、injected extension API 和需要直接改写 Minecraft 类的代码。
- [ ] GUI screen 和客户端渲染代码，除非先拆出纯数据/工具层。
- [ ] `EventBusForgeBridge` 这类直接依赖平台事件总线的桥接类。

## 短期任务

- [x] 初步收敛 adapter / resolver 边界语义：已通过 26.1 runnable 测试覆盖 null、数组/对象、无效 ID、非正 count、可变 stack copy 等关键路径。
- [x] 为 26.1 优先补 adapter / resolver 回归测试，覆盖 Graal `Value` object/array 输入与错误路径。
- [x] 继续收敛 adapter / resolver 边界语义：已覆盖 null/EMPTY、数组/对象、fluid amount 覆盖、host object copy、无效 shape，并修复 26.1 recipe reload 阶段 tag wrapper 的 registry owner 安全序列化。
- [x] 为 `RecipeJsonValue` / `RecipeJsonValueConverter` 补 26.1 runnable 测试，已覆盖 nested JS object/array、`IngredientFactory` tag wrapper、`SizedIngredientJS`、`ItemStack`、fluid wrapper、fallback namespace，并提供默认禁用的 invalid-shape 手动测试。
- [x] 增强 recipe 错误上下文：记录 builder/custom/copy 来源、recipe id、type、创建 API、prefix，并在最终 codec 失败时输出上下文和 JSON。
- [x] 增加 recipe path 操作：`setPath`、`removePath`，支持按 `ingredients.0` / `result.count` 这类 JSON path 修改/删除字段。
- [x] 细化 recipe path 操作：支持自动创建中间 object/array、反斜杠转义点号字段名。
- [x] 继续细化 recipe path 操作：支持批量路径编辑 `setPaths` / `removePaths`，以及点分、数组下标、反斜杠转义点号、括号/引号字段语法。
- [x] 增加 recipe dump/print 调试工具：`event.dump(filter)`、`event.print(filter)`。
- [x] 为 `event.recipes.minecraft` 增加 datapack type 名 raw JSON alias：`crafting_shaped(json)`、`crafting_shapeless(json)`。
- [x] 增加插件侧 recipe lifecycle hooks：`beforeRecipeLoading` 可在脚本 recipes 事件前预处理 raw JSON，`afterRecipes` 可在脚本修改后做校验、统计或最终改写。
- [x] 增加脚本侧 `ServerEvents.afterRecipes`，在普通 `ServerEvents.recipes` 后、最终 recipe codec parse 前运行。
- [x] 为 `event.recipes` 增加 namespace/type introspection：`namespaces()`、`types(namespace)`、`hasNamespace(namespace)`、`hasType(namespace, type)`，同时保留未知 namespace raw JSON fallback。
- [x] 保持 README、`docs/ROADMAP.md`、`ai_docs/` 与当前实现同步；本轮短期任务已同步整理相关文档。
- [x] 低风险 Java 清理：删除死代码（NekoUnifiedIR、NekoIRNode、NekoIRImport/Export/Binding/Scope、NekoJsDeclarationStatement、NekoModuleTransformResult）、修复 Bug（linkedEsmRecord 错误处理、SourceMap 缓存失效、moduleCache.put 竞态、isInteger("-")、ctimeMs 混淆）、消除 ESM 双重解析、精简编译器管线、IngredientJS→IngredientFactory 重命名。

## 中期任务

- [ ] 按“低版本差异、无平台副作用、可编译验证”的顺序推进 common 迁移。
- [ ] 为 Create 等常见 mod 增加轻量 JSON helper，例如 mixing、crushing、pressing，但不引入 schema 系统。
- [ ] 扩展脚本同步安全校验：路径穿越、绝对路径、扩展名、文件数量、批量大小。
- [x] 添加受控调度器：延迟任务、重复任务、reload 自动取消旧任务。Node 兼容 `timers`/`node:timers`/`timers/promises` 已实现 `setTimeout`/`clearTimeout`/`setInterval`/`clearInterval`/`setImmediate`/`clearImmediate` 及 promise 版，Context close 与单文件 reload 会取消旧任务。
- [x] 添加 CI，至少构建两个受支持平台模块。
- [x] 分析确认 common 模块因无 Minecraft 依赖，仅 6 个纯工具类可迁移（RecipeCreationContext、RecipeJsonPath、RecipeJsonValue、FluidAmounts、NekoWrapper、JsonObjectAdapter）。其余 ~120 个平台文件均有 Minecraft/NeoForge import，需 compat 层才能迁移。
- [ ] 为路径校验、脚本发现、事件总线、adapter 和 recipe filter 增加聚焦测试。

## Catalog 与外部类型工具契约

NekoJS 本体既提供稳定、可枚举的 catalog metadata、workspace layout 和 snippet 数据，也内置 probe（`ProbeOrchestrator`）直接遍历 catalog 生成 TypeScript artifacts（`.d.ts`）。外部工具仍可消费 catalog 替换或增强类型生成（通过 `ProbeRegistry.setGenerator`），但不再需要单独安装 ProbeJS 这类外部 mod。

### NekoJS 侧元数据契约

这些能力属于 NekoJS core / common 契约，应保持稳定并继续补齐富元数据。

- [x] 建立统一 catalog snapshot API：`NekoScriptCatalog.snapshot()` / `snapshot(side)`，聚合 bindings、events、adapters、recipe namespaces、host extensions、snippets、output layout。
- [x] 建立中立 catalog DTO：`BindingCatalogEntry`、`EventCatalogEntry`、`AdapterCatalogEntry`、`RecipeNamespaceCatalogEntry`、`HostExtensionCatalogEntry`、`SnippetCatalogEntry`、`TypeOutputLayout`。
- [x] 建立平台 catalog provider：平台显式贡献 recipe namespace、host extension manifest 和 output layout。
- [x] 让 recipe namespace 注册可被枚举；`NekoRecipeNamespaces` 提供有序 handler class view。
- [x] 建立 mixin extension manifest 基础：平台 provider 显式列出 target class / extension interface，catalog 复用 `MemberVisibilityQuery` 得到 JS 暴露名。
- [x] 解耦 `WorkspaceGenerator` 对 `.probe/{env}/probe-types` 的硬编码，改为读取 `NekoScriptCatalog.outputLayout()`。
- [x] 将默认类型输出根从 `.probe/` 改为 `.neko_probe/`，避免与 ProbeJS 冲突。
- [x] 增加 `registerTypeDocs` / catalog contribution 轻量插件钩子，让 NekoJS 插件能显式贡献富声明数据；旧 `api.probe` facade、`Probe*Doc` DTO、`NekoProbeMetadataProvider` 和 `NekoContextSnapshot` 已删除，统一改用 `api.catalog.NekoScriptCatalog`。
- [x] 为 `BindingCatalogEntry` 补声明元数据：是否 host class 的人工修正、人工类型覆盖、文档、示例。
- [x] 为 `EventCatalogEntry` 提供基础 snippet 模板。
- [x] 为 `AdapterCatalogEntry` 提供常见 adapter input shape、错误策略与示例。
- [x] 为 `RecipeNamespaceCatalogEntry` 提供 fallbackSupported、handler recipe type 方法名与初始 examples。
- [x] 为 wrapper/helper 提供人工声明补充，例如 `IngredientFactory`、`RecipeJsonBuilder`、`RecipeJsonValue`、recipe filter、fluid/item helper、`PersistentDataJS`、EntityType/Goal 注册 API 的链式返回和 union 输入类型；内置 manual declaration 注册已收敛到 common 的 `NekoCommonManualDeclarations`。
- [x] 增加可选 Java class-load telemetry / `Java.loadClass` 等价 hook，供外部类型工具收集用户脚本实际加载过的类；通过用户脚本层 `Java.type` / `Java.loadClass` wrapper 记录成功加载，并由 `ClassFilter` 记录 lookup attempt，不通过 mixin 拦截内部 wrapper。
- [x] `TypeOutputLayout` 默认类型输出根使用 `.neko_probe/`；workspace 生成器只读取 output layout，不硬编码具体目录。

### 本项目不负责的类型生成实现

以下能力现已由内置 `ProbeOrchestrator` 提供（外部工具可通过 `ProbeRegistry.setGenerator` 替换或增强）；保留此处作为「可被替换的边界」说明：

- 类型生成插件 API、class discovery、alias、special docs、side docs、snippet 注入。
- metadata graph / IR 到 `.d.ts`、snippet 和 workspace config 的完整 emitter。
- Java class registry、递归字段/方法/构造器/泛型发现和 JAR 扫描策略。
- Java package declarations、全局绑定、side-specific declarations、special aliases 和 index 文件生成。

NekoJS 本体只需保证 `NekoScriptCatalog`、manual declarations、snippets、`JavaModuleImportPolicy`、workspace layout 等输入稳定准确。

### 编辑器体验

- [x] 从 `SnippetCatalogEntry` 生成 VSCode snippets；当前 catalog 已提供 server started、recipe event、afterRecipes、recipe namespace introspection、fallback namespace、shapeless recipe、recipe builder 的初始片段，VSCode snippets JSON 序列化已收敛到 common 的 `NekoSnippetJson`。
- [x] 为工作区生成 per-script-dir `jsconfig.json`，让 `startup_scripts`、`server_scripts`、`client_scripts`、`test_scripts` 获得对应 side 类型。
- [ ] 保持 workspace layout、side-aware `jsconfig.json` / `tsconfig.json` 与 catalog 输出一致，供外部类型工具接入。
- [ ] 将未来 live editor bridge 作为外部增强，不作为 NekoJS 本体核心依赖。

### 明确不照搬 ProbeJS 的部分

- [ ] 不依赖 KubeJS plugin、KubeJSPaths、Rhino wrapper 或 `Java.loadClass` mixin hook。
- [ ] 不为了类型生成迁移 KubeJS 完整 recipe schema / component 系统。
- [ ] 不把 VSCode 扩展连接作为第一阶段必需能力，先保证纯 `.d.ts` 可用。
- [x] 不把脚本 API 改回大量 `Object` / `Value` 入口；`RecipeJsonBuilder` / `RecipeEventJS.custom` 已使用 `RecipeJsonValue` 中间类型收敛任意 JS JSON 输入。

## 下一阶段脚本能力规划

### PData typed API 与自动同步

- [x] 提供通用 `PersistentDataJS` 链式 wrapper，封装 `CompoundTag` 的 typed get/put/remove/merge/copy/replace API。
- [x] 为实体/玩家暴露 `pdata()`，脚本侧直接得到 `PersistentDataJS`，不需要直接操作裸 `CompoundTag`；底层使用实体原生 `getPersistentData()` 的 `NekoJSPersistentData` 子 tag。
- [x] 为 `PersistentDataJS` 补 server authoritative 自动同步 MVP：dirty tracking、`PDataSyncPacket`、服务端 tick 批量 full-tag sync、客户端只读 mirror。
- [x] 将 pdata 同步目标从全体在线玩家优化为 tracking/self，并增加 revision、每 tick 同步数量上限和 tag size 上限，避免旧包覆盖和基础网络压力。
- [x] 为 26.1 增加 runnable server/client smoke 测试，覆盖 player pdata 服务端写入、tick dirty、立即 sync、客户端 mirror 读取和客户端只读拒绝写入。
- [x] 增加 `test_scripts/` 脚本类型，用于显式运行 smoke/regression 脚本；默认不参与 startup/server/client 自动加载，第一版作为 server-like 测试环境，可复用 server binding/event，事件监听和 timer 生命周期按 TEST 自身隔离，并提供 TEST-only `Test` 断言 helper。
- [ ] 为 1.21.1 增加 runnable / client-server smoke 测试，覆盖实体/player pdata 持久化和同步。

### Node-compatible API 与 VFS

目标是在不引入完整 Node runtime 的前提下，为脚本提供尽量贴近 Node.js 的基础内置模块：`require('fs')`、`require('path')`、`require('util')`、`require('timers')`、`require('buffer')`、`require('process')`、`require('events')`，并支持 `node:` 前缀别名。所有真实文件访问必须通过 NekoJS VFS，最多只能访问 game root / `.minecraft` 目录内的内容。

- [x] 增加 26.1 runnable 探测脚本 `server_scripts/src/test_node_api_probe.js`，先记录当前 `require('fs')`、`path`、`util`、`timers`、`buffer`、`process`、`events` 可用性和 VFS 越界行为，不让缺失模块中断 reload。
- [x] 加强 VFS 路径校验：统一 `resolveGamePath` / `resolveNekoWritePath`，对相对路径、绝对路径、符号链接、创建新文件时的父目录 real path 做一致校验。
- [x] 明确默认访问策略：读路径限制在 `.minecraft` 内；写/删除默认限制在 `.minecraft/nekojs`；用户可在 `nekojs/config/engine.toml` 设置 `allowFsWriteOutsideNekojs = true` 允许写/删整个 `.minecraft`，但仍禁止越过 game root。
- [x] 收紧 `NekoJSFileSystem` 的危险入口：默认禁用 `createSymbolicLink`，避免脚本通过 symlink 创建外部访问通道。
- [x] 在 CommonJS `require` 外层安装 core module shim：保留现有相对路径/`node_modules` 解析，只拦截 `fs`、`node:fs` 等内置模块名。
- [x] 增加 issue #23 Java module import 解析：`require('java:java/lang')` / `import { $Integer } from 'java:java/lang'` 通过懒加载 namespace proxy 按 `$Class` 解析 Java 类型；`java:java/lang/Integer` class-level module 会直接返回 Java class 并暴露 `$Integer`/default。`JavaModuleImportPolicy` 允许类型生成器在 package module 与 class module 之间切换。
- [x] 增加轻量 data-driven recipe type definition：数据包可通过 `data/<namespace>/nekojs/recipe_types/<type>.json` 声明 `event.recipes.<namespace>.<type>(...)` 的 constructors、fields 和 JSON path 映射；静态 Java handler 仍优先，未知 namespace/type 继续 raw JSON fallback。
- [x] 将 Node shim JS 从 Java text block 拆到 classpath resources：`common/src/main/resources/nekojs/node/modules.list` 按顺序加载 `internal/define.js`、各 builtin module 和 `bootstrap.js`，避免依赖 jar 内目录扫描。
- [x] 实现 `fs` 同步基础 API：`existsSync`、`readFileSync`、`writeFileSync`、`appendFileSync`、`mkdirSync`、`rmSync`、`unlinkSync`、`readdirSync`、`statSync`、`lstatSync`、`renameSync`、`copyFileSync`、`realpathSync`、`readlinkSync`。
- [x] 实现 `fs` callback API：`readFile`、`writeFile`、`appendFile`、`mkdir`、`rm`、`unlink`、`readdir`、`stat`、`lstat`、`rename`、`copyFile`、`realpath`，错误优先 callback 行为尽量贴近 Node。
- [x] 实现 `fs/promises`：`readFile`、`writeFile`、`appendFile`、`mkdir`、`rm`、`unlink`、`readdir`、`stat`、`lstat`、`rename`、`copyFile`、`realpath`。
- [x] 实现 `path`：`join`、`resolve`、`normalize`、`dirname`、`basename`、`extname`、`relative`、`isAbsolute`、`parse`、`format`、`sep`、`delimiter`、`posix`、`win32`；该模块只做字符串处理，不做权限判断。
- [x] 实现轻量 `Buffer` / `node:buffer`：`Buffer.from`、`Buffer.alloc`、安全零填充的 `Buffer.allocUnsafe`、`Buffer.isBuffer`、`Buffer.isEncoding`、`byteLength`、`concat`、`toString`、`length`、基础下标访问；保证 `fs.readFileSync(path)` 未传 encoding 时返回 Buffer-like 对象。
- [x] 实现轻量 `process` / `node:process`：`cwd`、受 VFS 限制的 `chdir`、`platform`、`versions`、只读 `env`、`nextTick`、`argv`、`exitCode`、`pid`、`uptime()`、`hrtime()`。
- [x] 实现 `timers` / `node:timers` 与 `timers/promises`：`setTimeout`、`clearTimeout`、`setInterval`、`clearInterval`、`setImmediate`、`clearImmediate`，以及 promise 版 `setTimeout`、`setImmediate`、`setInterval` async iterator；type reload/Context close 时取消旧任务，单文件 reload 会取消目标入口脚本直接注册的旧 timer，避免脚本重载后定时器泄漏。
- [x] timer 回调按脚本 side 安全 flush：`server_scripts` 在 `ServerTickEvent.Post` 执行，`client_scripts` 在 `ClientTickEvent.Post` 执行；`startup_scripts` 只允许 immediate/0ms timer 并在 startup load 结束后 flush 一次。
- [x] 实现 `util`：`format`、`inspect`、`promisify`、`callbackify`、`deprecate`、`inherits`、`isDeepStrictEqual`、`debuglog`、`inspect.custom` 和 `types` 中常用判断函数。
- [x] 实现 `events`：轻量 `EventEmitter`、`once`、`on`、`prependListener`、`prependOnceListener`、`listenerCount`、`eventNames`、`rawListeners`、`setMaxListeners`、`getMaxListeners` 和未监听 `error` 事件抛出行为，满足常见 npm 小模块依赖。
- [x] 实现轻量 `assert` / `node:assert` 与 `test` / `node:test` shim；`assert` 已支持 `match` / `doesNotMatch`，`node:test` 仅在 `test_scripts` 可用，当前支持基础 runner、`describe` / `it`、`before` / `after` / `beforeEach` / `afterEach`、子测试、skip/todo 与 Promise 测试，复用 TEST-only `Test` helper 输出 section/pass，适合 smoke/regression 脚本。
- [x] 为 Node-compatible API 补 catalog manual declarations，让外部类型工具可为 `require('fs')` / `require('node:fs')` 等生成轻量 shim 对应的类型。
- [x] 为 26.1 添加 Node/VFS runnable smoke test，覆盖 `nekojs/` 内读写、默认写越界拒绝、父路径越界拒绝、Buffer 返回和 TEST timer promise/callback 基础行为。
- [x] 补 Node/VFS 剩余专项：26.1 runnable 增加 symlink 逃逸夹具（宿主允许创建 symlink 时断言 VFS 拒绝逃逸读取；不允许时环境门控跳过），并同步 1.21.1 run-dir smoke fixture。
- [x] Node API 完善：新增 `os` 模块（arch/platform/cpus/freemem/totalmem/homedir/hostname/tmpdir/uptime/userInfo/networkInterfaces/endianness/loadavg/release/type/version/EOL/constants/devNull）；Buffer 补全 slice/subarray/fill/indexOf/includes/copy/equals/compare 及 14 个多字节读写方法（readUInt8~writeDoubleBE）；Stats 补全 isBlockDevice/isFIFO/isSocket/dev/ino/mode/nlink/uid/gid/rdev/blksize/blocks；process 补全 memoryUsage/cpuUsage/hrtime.bigint/env 真实环境变量；path.posix/win32 补全 format/parse/resolve/relative/isAbsolute；fs.accessSync 支持 mode 参数（F_OK/R_OK/W_OK/X_OK）。

### 原生 ESM runtime 与后续 CJS runtime

目标是实现 NekoJS 自有的 native ESM module runtime，而不是把 ESM 降级转换成 `require` / CommonJS。ESM 应由 Java 侧 lexer/parser/AST/IR 构建模块记录，执行层按 ESM 的 parse / instantiate(link) / evaluate 思路处理 import/export binding、live binding、循环依赖、`import.meta` 和 dynamic import。JS glue 只用于 Graal 执行边界必要的最小包装，不作为 Babel/SWC 这类模块转换器，也不把 CJS 当作 ESM 的最终执行模型。

NekoJS 的 ESM 仍然不是传统 npm package-main/import-graph 脚本发现模型：普通脚本入口继续由 NekoJS 从 `startup_scripts/`、`server_scripts/`、`client_scripts/`、`test_scripts/` 独立发现并加载；`import` 主要用于导入特定值、helper、JSON、Node/`node:` 模块、Node ESM-style module 或 `java:` Java 模块。入口发现不依赖 `package.json main`，也不依赖某个 root import graph 才决定哪些脚本会运行。CJS 后续也要实现，但应作为 NekoJS 自有兼容模块格式，与 native ESM 互操作，而不是让 ESM 永久转成 CJS。

实现路线更新为：Babel / Babel bundle / transformer 构建工具 / npm 依赖保持移除；现有 Java ESM→CJS transformer 只能视为临时原型和 parser 探路，不作为最终方向继续扩展。后续应把 `NekoEsmParser` 继续推进成稳定 AST/IR 前端，并替换执行层为 native ESM module graph/runtime。

当前 ESM 已可覆盖常用 authoring 路径：native Graal ESM evaluation 已承担实际执行，Java 侧 module record/linker/rewriter 负责 resolver、diagnostics、`import.meta`、dynamic import、JSON/special/CJS synthetic module、TLA async evaluation 和 CJS require ESM namespace capture。parser 前端已从 regex import/export 拆分推进为 NekoJS 自有 token/IR parser，能够稳定提供 statement/specifier/binding span，并已记录常见函数参数、catch binding、class static block var 作用域、对象 method body、block arrow function body、private class method、computed class method 和 class field initializer 内 method/arrow 的函数作用域边界。`NekoEsmModuleAst.program()` 现在额外暴露源码感知 `NekoJsProgram`，包含 runtime/default-export expressions、block body、function-like 和 class body/element AST。当前 TS/JSX/TSX sourcemap chain 已接入 prepared module cache 和 `node:test` mapped stack。后续优先级应保持明确：补齐 decorator/complex class element 精度、更深 expression tree 与 diagnostics；随后推进真正模块实例级热替换、Java AST/IR-backed CJS runtime 和完整自有 evaluator。这样先解决用户混用 `import` / `require` 的高频问题，避免过早投入复杂 evaluator。

- [x] 重构脚本编译接口：新增 `IScriptCompiler.compileDetailed(...) -> ScriptCompileResult(code, sourceMap)`，让语言插件/legacy 编译器能返回 sourcemap；旧 `compile(...) -> String` 暂作为兼容默认入口保留。内置 `.ts` erasure 和 `.jsx/.tsx` classic lowering 已产出标准 v3 sourcemap，其中 `.tsx` 先生成 JSX 到原始 TSX 的直接映射，再复用 whitespace-preserving TS erasure。
- [x] 新增 shared prepared module cache：`NekoModulePreparationCache` 作为 loader/linker/rewriter/VFS 的统一准备入口，按 canonical path 的 mtime/size 缓存 compiled source、source map、AST 和 mode，并集中注册 source map；后续本体 TS/JSX/TSX 编译器应接入这一层。
- [x] 建立 bootstrapped plugin registration snapshot：`NekoPluginRuntime` 在插件加载后一次性收集 script language/compiler、script property、bindings、client bindings、JS type adapters、events、client events、type docs/manual declarations、recipe namespaces 和 recipe lifecycle hooks；插件 API 保留多入口 typed hooks（`registerScriptCompilers`、`registerScriptProperty`、`registerBindings`、`registerClientBindings`、`registerAdapters`、`registerEvents`、`registerClientEvents`、`registerTypeDocs`、`registerRecipeNamespaces`、`registerRecipeLifecycleHooks`），bootstrap 由 extension point descriptor 列表驱动，每个 descriptor 负责一个 hook 的插件类型、client gating 和收集动作。内置 descriptor 覆盖 core hooks，外部插件可通过 `NekoPluginExtensionProvider` 注册新的 typed plugin interface descriptor（例如 startup-only binding hook），最终统一写入 `NekoPluginRuntime`，读路径不再各自 lazy 遍历 plugin manager。`ScriptCompilerRegistry`、`ScriptPropertyRegistry` 和 extension context 暴露的 bootstrap registry 都会在 bootstrap 后 freeze，插件保存旧 registry 后延迟注册会 fail-fast。`NekoJSCorePlugin` 通过 `registerScriptCompilers` 注册内置 `.ts` erasable TypeScript 前端，外部插件可后注册覆盖 `.ts` 或补 `.tsx/.jsx`。script discovery、resolver extension probing、VFS `.js` fallback 和 module pipeline 都从当前 runtime registry 读取。
- [x] 建立语言插件流水线 Phase 1 壳：新增 `NekoLanguagePlugin -> NekoLexer -> NekoParser -> NekoSourceAst -> NekoAstLowering -> NekoUnifiedIR -> NekoJSBackend` 的最小 API，`ScriptCompilerRegistry` 同时支持新 language plugin 与旧 `IScriptCompiler` legacy adapter，`NekoModulePipeline` 已先通过 `NekoCompilationPipeline` 产出 IR/backend 输出再转换回现有 `NekoPreparedModule`，保持 `.js/.mjs/.cjs/.ts` 当前运行语义不变；JS、TS 和 legacy 编译器输出现在统一收敛到 `NekoEsmSourceAst`，并复用 `NekoEsmToUnifiedIrLowering` 生成 unified IR；公共 IR record 不再暴露 `Object native*` 底层 AST 节点，内部 ESM module AST 由 pipeline 私有处理。
- [x] 继续重构 `NekoModulePipeline` 第一阶段：新增 pipeline/cache facade 接管 prepared module 与 sourcemap 生命周期，`NekoModuleReadService` 接管 transformed/virtual module 读取和 `.js` fallback，ESM module record 构建与 link metadata 经 context-scoped cache/facade 委托。
- [x] 增加 revision-aware targeted invalidation：模块 targeted reload 会 bump module revision，同步失效 ESM record/link cache 与 native virtual URI generation，并在 affected-module invalidation 中保留 dependency graph 拓扑；entry subtree rerun 仍会按需要清理 graph 节点。26.1 runnable regression 已覆盖 CJS require cache、ESM namespace/link metadata、dynamic import virtual URI 和失败后恢复。
- [x] 继续推进真正模块实例级热替换：新增 `ModuleVersion`（sourceStamp + generation 追踪）、`ModuleSliceRelinker`（依赖切片拓扑排序、快照回滚、逐模块重链）。`NekoScriptModuleLoaderHost.hotReloadModule()` 支持非入口依赖变更时仅重链受影响模块而无需重跑入口。`NekoJSScriptManager.reloadScriptFile()` 集成了 hot-reload 优先策略，失败时自动退回到入口重跑。JS bridge 已暴露 `hotReloadModule`。
- [x] 实现 NekoJS 自有脚本 loader 第一版实验路径：由 `NekoScriptModuleLoaderHost` + `internal/script-loader.js` 执行入口和相对模块，支持稳定局部 `require`、相对模块、模块缓存、JSON 模块、目录 `index`、扩展候选解析、Node builtin / `node:` alias 和 `java:` Java 特殊模块。文件级 `/nekojs reload <type> <file>` 已接入依赖图失效：helper reload 会沿反向父边找受影响入口，entry reload 会先清理该入口已知静态依赖子树；失效会同步清理 CJS cache、ESM module record、prepared module cache、dependency graph 和 native ESM virtual source generation。
- [x] 将脚本执行入口切到统一 NekoJS loader：`NekoJSScriptManager` 不再按旧实验 flags 或 Graal CommonJS 三路径分流，统一由 `NekoScriptModuleLoaderHost` 进入自有 resolver/runtime。
- [x] 移除旧 native GraalJS ESM 实验分流和 `.js -> .mjs` alias 方向；后续稳定路线不是路径伪装或 regex import rewrite，而是 NekoJS 自有 AST/IR-backed native ESM runtime。
- [x] 移除 per-module `require-patch` prepend：Node builtins、`node:` alias、`java:` Java 模块和相对模块解析由自有 loader/runtime 处理，避免污染用户模块行号。
- [x] 增加可配置脚本错误日志格式：`engine.toml` 默认 `conciseScriptErrorLogs = true`，script/test/event 错误优先输出原因、用户脚本路径、行列号和代码片段；设为 `false` 时输出完整 verbose diagnostics/stack，便于调试分析。
- [x] 重做 `SourceMapRegistry`：删除 regex 解析方案，改为 Gson 解析的 v3 sourcemap 模型，支持 `sources`、`sourcesContent`、`names`、`sourceRoot`、prepended line offset 和最终执行路径到原始源码位置映射；错误日志、mapped stack、`node:test` stack line 和 prepared module cache invalidation 已接入 mapped path/source content。
- [x] 建立当前 TS/TSX/JSX sourcemap chain：`TS` erasure 生成 identity map，`JSX/TSX` classic lowering 产出到原始源码的 v3 map，`.tsx` 在 whitespace-preserving TS erasure 后保留 JSX->TSX map，最终由 prepared module cache 注册执行代码到原始源码的映射。
- [x] 完全移除 Babel 路线：已删除 Babel transformer Java wrapper、generated bundle、transformer build tools 和仅服务于 Babel transformer 的 npm package 文件；运行时不保留 Babel fallback。
- [x] 建立 NekoJS 自有 Java AST/IR parser 初版：`.mjs` 强制 ESM，`.cjs` 强制 CJS，`.js/.ts/.tsx/.jsx` 通过 Java lexer/parser 检测真实 top-level import/export，避免 regex/contains 误判。
- [x] 将当前 Java ESM→CJS transformer 降级并停止作为目标扩展：默认 ESM 路径已改为 native ESM module record / linker / evaluator，运行时不再把 ESM 降级到 `require` / CommonJS。
- [x] 设计 ESM AST/IR 初版：显式表达 import declaration、export declaration、re-export、runtime expressions、dependency table、source spans 和初始 diagnostics，不用零散字符串替换作为核心语义；当前 module syntax 前端已改为 NekoJS 自有 token/IR parser，不再用 regex 拆 import/export，parser 会为静态 specifier literal 提供准确 span，native ESM source rewriter 直接使用该 span；parser 已记录 import/export、顶层声明、嵌套函数/块作用域声明、常见函数参数、catch binding、class static block var 作用域、对象 method body、block arrow function body、private class method、computed class method、class field initializer 内 method/arrow 和常见解构声明形成的本地 binding table，解构 parser 会跳过默认值表达式并识别 rest binding 与 computed property binding；linker 能诊断 `export { missing }`、同一 lexical scope 内 duplicate local binding、重复 explicit export 和 ambiguous star export；诊断 span 会优先指向具体 binding/export 名称。通用 `module.jsast` 已从骨架推进到源码感知 `NekoJsProgram`，可暴露 import/export statement、binding/scope、runtime/default-export expression、block body、function-like raw/name/kind/parameters/body、class body/element 和 field initializer AST；后续继续补 decorator/complex class element 精度与更深 expression tree。
- [x] 实现 native ESM module record 初版：记录 module id/path、prepared source/AST、link metadata、status、namespace、failure、evaluation future 和同步/异步 evaluation 状态位；入口加载已接入 async evaluation 等待和 timer flush，后续继续补 dfs index 与完整 binding 表。
- [x] 实现 ESM linker 初版：按 NekoJS resolver 解析静态依赖，生成 dependency/local export/indirect export/star export metadata，并对可静态确认的 ESM/JSON dependency 缺失导出提前给出文件行列 diagnostic；export shape 已提升为 `NekoEsmExportShape` typed model，会区分 explicit/local、indirect re-export、star export、ambiguous star export，并保持 `export *` 不转发 default；module graph 只用于依赖链接，不用于决定脚本目录入口发现。
- [x] 实现 ESM live binding 与 cycle 语义初版：入口和依赖通过 canonical virtual URI 交给 Graal native ESM evaluation，测试覆盖 live binding 与 ESM↔ESM cycle；后续 Java module record 仍需补完整 binding 表和冲突诊断。
- [x] 实现 ESM evaluator 初版：Java module record 管理 resolver/link/cache/diagnostics，Graal 以 `application/javascript+module` 执行模块并提供 native ESM namespace/live binding 语义；top-level await 已走 Graal native async evaluation，Java 侧通过 async-safe namespace capture 完成 entry/dynamic import future。
- [x] 支持 native `import.meta.url/filename/dirname/resolve` 和 dynamic `import(specifier)` 初版；`import.meta.resolve(specifier)` 现在返回可直接交给 native dynamic import 的最终 module URI：物理 ESM 返回稳定 native virtual URI，JSON/CJS/special module 返回 synthetic module URI。字面量 dynamic import 在 rewrite 阶段解析为 NekoJS resolver URI，非字面量表达式运行时调用 resolver 后仍交给 Graal 原生 `import()` 返回 namespace，并支持动态导入 Node builtin / `node:` / `java:` special module 的 default/namespace synthetic module。物理 ESM source 通过 module-id 稳定 virtual URI 注册，入口和依赖共享同一模块身份，并用 reserve/register 处理 ESM cycle 的递归展开风险；virtual module 同时记录用户可读展示路径，供默认简洁错误日志隐藏 `.native_esm_modules/<hash>.mjs`。loader cache clear 会同步清理 prepared module cache 和 virtual registry，避免 reload 后旧 source/path 残留。后续继续补完整 module cache/link/evaluate 集成。
- [x] 增加 token parser 回归 fixture：覆盖 import/export 文本出现在字符串、模板和正则中不会污染模块语法解析，本地 `export { a as b }` 按源 binding 校验并按别名导出，同一条 `export const a, b` 的所有 binding 都能进入 export table。
- [x] 实现 top-level await 第一阶段：pipeline 不再硬拒绝 TLA，ESM entry/static dependency/dynamic import 会等待 Graal native async module evaluation 完成；CJS `require()` 遇到 TLA/async-descendant ESM 会抛出清晰同步错误，而不是用 CJS Promise 包装模拟。
- [x] 增加 `.mjs` / `.cjs` 脚本扩展支持，并定义 `.js` auto、`.mjs` ESM、`.cjs` CJS 的行为：pipeline/resolver 均按该规则分类，script language registry 也内置这些扩展。
- [ ] 实现 CJS runtime 第二阶段：Java AST/IR-backed CJS parser/loader，支持 `require`、`module.exports`、`exports`、`__filename`、`__dirname`、JSON、Node builtin、`java:`，并与 native ESM 建立互操作规则。
- [x] 定义 ESM/CJS interop 第一阶段：ESM import CJS 的 default/namespace/named 读取 `module.exports`，CJS require 已支持的同步 ESM 通过 native namespace capture module 返回 namespace；测试覆盖 default/namespace/named import CJS、CJS require ESM 和 require TLA ESM 的同步拒绝。后续仍需补 CJS↔ESM cycles 和更完整 CJS runtime。
- [x] 增加命令级依赖索引 reload：`/nekojs reload <server|client|startup|test> [file]` 支持按 type 整体重载、重跑指定入口，或在指定 helper module 时通过 runtime dependency graph 反向找到已加载的受影响入口并重跑；目标入口会清理自身 event listener、timer、错误记录，并按受影响模块切片失效 CJS cache、ESM module record、prepared cache 和 virtual ESM source。virtual ESM URI 现在带 module generation，reload 后会生成新 URI，避免 Graal 继续复用旧 native ESM module instance，同时避免每次 helper reload 都清空整个 runtime module cache。
- [ ] 增加真正模块实例级热替换：以 real path、lastModified、size、可选内容 hash、compiler/parser/runtime version、module format 为 key，拆分 source/compile/AST/link/evaluate 缓存；维护 generation/module instance，helper module 变更时只 invalid/relink/re-evaluate 受影响图切片而不是重跑入口，并隔离失败 reload。
- [x] 增加 native ESM invalid diagnostics 自动断言：`/nekojs test` 通过 CJS helper 显式加载 disabled invalid fixtures，覆盖 duplicate export、import/local duplicate binding、same-scope duplicate binding、重复参数/catch binding、ambiguous star export、missing dependency named import、missing re-export、re-export alias 诊断名和 `export *` 不转发 default；TLA 已迁移为正向 runtime fixture，并补 CJS require TLA ESM 的清晰同步错误断言。
- [x] 增加 native ESM runnable 测试：builtin import、relative import、default/named export、namespace import、side-effect import、re-export、`export *`、cycles、dynamic import、`import.meta`、JSON import、Node builtin / `node:` import、`java:` named import、TLA static/dynamic evaluation、ESM/CJS interop、TS/TSX sourcemap 报错映射。
- [x] 把 parser edge case 从 fixture 扩成 invalid/diagnostic 覆盖：新增 computed `import.meta` 边界、re-export alias diagnostic、decorator/class 边界和重复参数/catch binding 诊断；后续仍可继续补更深 decorator/complex class element AST 精度。
- [ ] 为 native ESM/CJS 更新 catalog manual declarations 和 workspace 配置输入，让外部类型工具可按现代 JS/TS 模块语法提示。
- [x] 实现 NekoJS 内置纯 Java `.ts` erasable TypeScript 第一阶段：类型标注、`type` / `interface`、`import type` / `export type` 等会在语言前端擦除，保留 ESM/CJS runtime 语义，unsupported enum/namespace 等语法给出清晰错误；`.ts` 现已注册为 `NekoTypeScriptLanguagePlugin`，通过 `NekoTypeScriptLexer` / `NekoTypeScriptParser` 产出 `NekoEsmSourceAst`，再复用 `NekoEsmToUnifiedIrLowering` 输出 JS-compatible `NekoUnifiedIR`，旧 `NekoTypeScriptCompiler` 仅保留为 legacy adapter 兼容。`.jsx/.tsx` 现已注册为 `NekoJsxLanguagePlugin`，提供轻量 classic runtime lowering，并复用同一 ESM parser/lowering；TS/JSX/TSX sourcemap chain 已接入运行时 mapped stack。后续继续在本体补 decorator/parameter property、自动 JSX runtime、更完整 TSX、高级 TS diagnostics 和复杂 JSX/TSX sourcemap 精度。

### Painter API 与 client render events

- [x] 增加 client-only `PainterJS` 链式 API；1.21.1 包装 `GuiGraphics`，26.1/26.2 包装 `GuiGraphicsExtractor` / render state API（`fill`/`text`/`blit`/`item`/`outline`/`pose` 均为新 API，无 `GuiGraphics`）。
- [x] 增加 `ClientEvents.hud` / screen render 事件，事件对象携带 `PainterJS`、尺寸等上下文（`RenderGuiEvent.Post` 经 `bindTransformed` 包装）。
- [x] MVP 绘制能力：`color/resetColor`、`rect/fill`、`outline`、`gradient`、`text/centerText`、`texture`、`item`、`push/pop/translate`、`scissor`。
- [x] 确认 dedicated server 不加载 painter/client class（`ClientEvents` 为 client-only 组，绑定只在客户端初始化路径注册）。

### 原生 EntityType 与 Goal 注册

- [x] 扩展 `RegistryEvents` / `RegistryEventListener`，增加 startup-only `entityType` registry builder。
- [x] 增加 `EntityTypeBuilderJS`，支持 category、size、tracking、update interval、fire immune、no save/no summon、attributes。
- [x] 增加属性注册和客户端 no-op renderer，保证默认脚本实体可被服务端生成且客户端不会因缺 renderer 崩溃。
- [x] 增加 Goal 注册 MVP：`floatInWater`、`panic`、`randomStroll`、`meleeAttack` vanilla goal factory。
- [x] 对已有实体追加 goal 时使用 join-level 注入并做 identity marker 去重。
- [x] 增加 spawn egg 子阶段（`EntityTypeBuilder.spawnEgg(bg, hl)` 自动注册 `<id>_spawn_egg`；1.21.1 运行时染色，26.x 纹理数据驱动 + 自动模型生成）。
- [x] 增加 target/look/avoid 等更多 vanilla goal factory，并评估按 `EntityType` 映射目标 class 的脚本 API。（结论：`EntityType.getBaseClass()` 自 1.21.x 硬编码 `Entity.class` 不可用——采用**内置 36 实体→类映射** + `Java.type(...)` 传类兜底，NekoJS 脚本实体统一 `NekoScriptMob`；`target/hurtByTarget` 走 `targetSelector`）
- [ ] 增加 functional-interface backed script goal，内部捕获脚本异常并限流日志。

### Loot Table API

参考 LootJS 但针对 NekoJS（GraalVM）重新设计。LootJS 基于 Rhino/KubeJS 的 `TypeWrapperRegistry` 和 builder 模式；NekoJS 采用**混合模式**：底层 JSON 访问 + 高层便利 builder，与 recipe 系统的 JSON-first 理念一致。

**LootJS vs NekoJS 设计差异分析**：

| 方面 | LootJS (Rhino) | NekoJS 适配 |
|------|---------------|------------|
| 类型转换 | `TypeWrapperRegistry` 自动转 `ItemStack→LootEntry` | `JSTypeAdapter` 显式适配，避免 Graal 重载歧义 |
| ItemFilter | 自建全套 Predicate builder（hasEnchantment/tag/toolAction 等） | **去掉**：复用 NekoJS 已有的 `IngredientFactory` + `ItemJS` |
| Predicate 包装 | Java builder 包装所有原版 Predicate | **简化**：允许直接传 JSON，由 Codec 反序列化 |
| 战利品表修改 | `MutableLootTable` 完整 builder | **混合**：底层 `getJson/setJson` + 高层 `modify(table, consumer)` |
| ID 类型 | `ResourceLocation` | `NekoId`（NekoJS 统一 ID 类型） |
| 条件/函数 | 链式 builder（`addCondition`/`addFunction`） | 保持链式，但支持 raw JSON fallback |

**推荐实现阶段**：

- [x] **阶段 1 — JSON 访问（MVP for 80% 用例）**: `ServerEvents.lootTables` 事件，`event.getJson(id)` / `event.setJson(id, json)` / `event.getIds()` / `event.remove(id)` / `event.create(id, json)`。与 recipe 系统理念一致，脚本可以直接操作 `JsonObject`。（注：`getIds` 暂不带 filter、`create` 直接带 json；修改在服务器数据 reload 时统一应用，经 NeoForge `LootTableLoadEvent` 生效）
- [x] **阶段 2 — 便利 builder**: `event.modify(id, table => table.addPool(pool => pool.addEntry(...)))`，`LootEntryJS.of(item/ItemStack/'#tag'/JS对象)` + `weight/when/group` 链式，`LootPoolJS.rolls/addEntry/when`。JSON-first：builder 直接操作底层 JsonObject，冷门字段走 raw JSON。
- [x] **阶段 3 — 全局 modifier 事件**: `ServerEvents.LOOT_MODIFIERS` 不引入运行时事件——调查确认 26.x `LootModifierManager` 只读（无 setter/事件），运行时增删需改 `neoforge:loot_modifiers/global_loot_modifiers.json` 配置 + 数据文件。改用**现有 `generateData` 写 datapack JSON** 实现（`neoforge/loot_modifiers/global_loot_modifiers.json` + `<ns>/loot_modifiers/<id>.json`），已在 wiki 记录。
- [x] **阶段 4 — Predicate builder（可选）**: 不包装 vanilla Predicate——builder 的 `when(conditionJson)` 直接传 raw JSON 已覆盖用例（ROADMAP 原判），保持简单。
- [x] **阶段 5 — 批量操作**: `modifyBlockLoot(filter, consumer)` / `modifyEntityLoot(filter, consumer)`（精确 id / `#tag` 展开 / `*` / Predicate 过滤）。

**设计原则**：
- NekoJS 不要照搬 LootJS 的 `ItemFilter`（与 `IngredientFactory` 功能重叠，会造成两种写法混淆用户）
- NekoJS 不要照搬 LootJS 的 Predicate builder（那是 Rhino 类型优势的产物，对 Graal 无优势）
- Builder API 保持简洁：只覆盖高频操作（addPool/addEntry/setRolls/removePool），冷门操作走 raw JSON
- 用 `NekoId` 而不是 `ResourceLocation`，保持 NekoJS API 一致

### PowerfulJS-like Capability 集成

- [x] 设计轻量 `Capabilities` startup binding，不复制 PowerfulJS/KubeJS 完整能力系统。
- [x] 第一版只支持标准 energy/item/fluid capability，使用平台原生 backing storage 保存状态（ItemStackHandler/EnergyStorage/FluidTank 自带 NBT 序列化）。
- [x] 在 `RegisterCapabilitiesEvent` 中为 block entity 注册 provider（`CapabilityEvents.register`）；暂不做任意 Java interface capability 生成。entity/item/block provider 留待后续。
- [x] **26.x 适配**：正向桥接（IItemHandler/IEnergyStorage/IFluidHandler → ResourceHandler/EnergyHandler），参考官方反向适配器实现，立即提交语义；旧接口标记待删除，@SuppressWarnings(removal)。
- [ ] 将 pdata 作为脚本业务数据层，capability 作为 NeoForge 生态访问层，两者可复用 NBT 工具但不共用同一个 tag。

## 长期方向

- [ ] 定义稳定的 NekoJS API 版本和破坏性变更迁移策略。
- [ ] 保持统一元数据可供外部工具生成 `.d.ts`、编辑器类型输出和 API 文档。
- [ ] 逐步收紧 HostAccess，区分可信本地开发模式与更安全的服务器运行模式。
- [x] 增加性能诊断：改为**用户驱动**的 Performance 全局绑定（`Performance.now()`/`time(fn)`/`bench(fn,runs)`/`start(label)`+`PerfTimer.mark/end/report/elapsedMillis`），而非被动检测慢监听器/reload 耗时（被动检测被明确否决，已删除自带脚本执行时间 log）。用户可在脚本里自行测量函数耗时与分段计时。
- [ ] 维护 NekoJS、GraalJS、Minecraft、Java、NeoForge 兼容矩阵，并记录外部工具对 catalog 契约的兼容要求。
- [ ] 只有在两个平台都能稳定表达 AnyHolderSet / wildcard ingredient 后，再考虑 `Ingredient.all()` / `Ingredient.not(...)`。

## 架构迁移：显式依赖与生命周期治理

> 目标：在不牺牲脚本 API 简洁性的前提下，将有生命周期、初始化顺序或测试污染风险的全局状态迁移到显式对象图。
> 当前实施清单：[ai_arch/plan.md](../ai_arch/plan.md)

### Phase 0：架构规则冻结（历史阶段）

- [x] 盘点 `NekoJS.COMMON`、`current()`、static mutable config 等隐藏依赖并完成第一轮收敛。
- [x] 架构迁移改由代码评审、定向测试和 `ai_arch/plan.md` 清单跟踪；旧的正则计数型 `architectureCheck` 已退役。

### Phase 1：核心值对象、配置和错误状态实例化

- [x] `SandboxConfig` record + `SandboxConfigLoader`（TOML 读取集中）
- [x] `ClassFilter` 实例化 + `INSTANCE` legacy facade
- [x] `NekoJSPaths` 实例化：`fromGameDir(Path)` factory + 实例 getter + 实例校验方法 + `legacy()` seam
- [x] `ScriptFilePolicy`：解耦 paths 与 compiler registry（已委托 `ScriptFilePolicy.legacyRuntime()`）
- [x] `ErrorTracker` 接口 + `DefaultErrorTracker` 实例 + `NekoErrorTracker` static facade 委托实例
- [x] `NekoJS.COMMON`: 33 → 15 → 4（commands/platform listeners 逐步迁移到 `NekoRuntimeRoot`）

### Phase 2：组合根和生命周期所有权

- [x] `NekoCoreContext` record（paths/engine/sandboxConfig/classFilter/errorTracker，5 字段，无 get/current）
- [x] `ResourceTracker`：AutoCloseable，track/cleanup 逆序、addSuppressed 聚合
- [x] `NekoRuntimeRoot`：composition root，lifecycle API（reload/reloadFile/runTests/errors/close）
- [x] platform bootstrap (`NekoJSMod`) 创建 `NekoCoreContext` → `NekoRuntimeRoot`，script managers 双写
- [x] commands 改用 `NekoJSMod.RUNTIME_ROOT` lifecycle API

### Phase 3：Sandbox、module pipeline 和 module reload owner 实例化

- [x] `NekoSandboxFactory`：注入 `NekoCoreContext`+`ScriptCompilerRegistry`；`NekoSandboxBuilder` 退为 legacy
- [x] `NekoModulePipeline` 实例服务：注入 `NekoCompilationPipeline`+`ScriptCompilerRegistry`+`SandboxConfig`
- [x] `NekoModuleResolver` 注入 `NekoJSPaths`+`ScriptFilePolicy`+`ScriptCompilerRegistry`
- [x] `NekoModulePipelineCache` 接入实例 pipeline（`legacyPrepare` → `legacyInstance().prepare`）
- [x] `ModuleReloadCoordinator`：统一失效顺序（bump revision → CJS/ESM/link cache → dependency graph → virtual registry → prepared cache → source map）

### Phase 4：Script environment 和 ScriptManager 依赖显式化

- [x] `ScriptContextRegistry` + `ScriptContextSeam`：窄 Context 身份注册表（scriptTypeOf/currentScriptIdOf，不返回 manager）
- [x] `ScriptEnvironmentFactory`：接管 Context/Node/bindings/event/telemetry 初始化
- [x] `ScriptExecutor`：current script id + telemetry scope + entry 加载 + TLA 等待 + 错误记录
- [x] `ScriptReloadCoordinator`：统一 targeted reload 顺序（listener cleanup → timer cancel → error clear → preload → rerun）
- [x] `ScriptManager` 删除 `NekoJS parent`，构造器注入具体协作者（6 参数 + `ScriptEnvironmentFactory`）
- [x] `ScriptManager` 实现 `AutoCloseable`（Context/Node/listener closedown 由 `closeRuntimeResources` 统一）
- [x] `NekoJS` 顶层只保留常量（`MODID`/`VERSION`/`LOGGER`）+ `@Deprecated` legacy facade

### Phase 5：错误模型细化和异常边界整理

- [x] `ScriptErrorReporter` 为 bootstrap seam（`NekoErrorTracker` class-load 绑定 → `bindLegacy` 重绑）
- [x] `catch(Throwable)` 审计：18 处均为脚本执行/callback/清理安全边界，无需要改动的业务路径

### Phase 6：LoaderHost 代码组织与 legacy facade 删除

- [x] 6A：LoaderHost interop shell 固定（16 个 `@CalledByDynamicCode` Graal callback 入口已标注）
- [x] 6C：缓存生命周期边界固化（`ModuleReloadCoordinator` 为一等 owner，`NekoModulePreparationCache` dead facade 删除）
- [x] 6D：legacy facade 标记 `@Deprecated`（`NekoJS.COMMON`/`NekoJSPaths`/`NekoErrorTracker`/`NekoSandboxBuilder`/`ClassFilter.INSTANCE`）
- [x] 6E：architecture allowlist baseline 更新（`NekoJS.COMMON` 33→4，`NekoJSPaths.static` 120→138，`NekoErrorTracker.static` 28→30）
- [x] 6B：`EsmModuleLifecycle` / `CjsModuleLoader` / `ModuleHotReloader` 从 LoaderHost 实体提取（EsmModuleLifecycle + CjsModuleLoader 已提取，~200 行下沉，LoaderHost 692→487 行，-30%）

### 验证状态

- [x] 4 平台 `compileJava` 通过（cleanroom-1.12.2、neoforge-1.21.1、26.1、26.2）
- [x] 四个平台继续通过编译和定向测试验证共享架构行为。
- [x] `NekoJS.COMMON` 从初始 33 降至 4（仅剩 bootstrap 赋值 + `NekoPluginBootstrap` 静态引用）
