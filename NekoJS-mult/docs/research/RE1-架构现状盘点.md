# RE1 架构现状盘点（基线）

- 日期：2026-08-28
- 票：[#39](https://github.com/NekoJS-Dev/NekoJS/issues/39)
- 范围：只做现状盘点，不含重构建议
- 代码基线：master `621f465`（W7 wrap-up）；路径均相对 `NekoJS-mult/`
- 方法：全部结论来自本地源码通读与 grep 统计（primary source = 源码本身）

---

## 一、扩展点清单与注册链路

### 1.1 机制核心（common/src/main/java/com/tkisor/nekojs/core/plugin/，共 17 个文件 1736 行）

| 文件 | 行数 | 职责 |
|---|---|---|
| `NekoPluginExtensionPoint.java` | 147 | 扩展点定义 record：`id + pluginType + enabled 谓词 + initializer(累积器工厂) + collector + finisher`；工厂 `of / ofContext / clientOnly / clientOnlyContext` + 2 个兼容旧形态的退化工厂 |
| `BuiltinPluginExtensionPoints.java` | 394 | 14 个内置扩展点（`nekojs:*`）的构造与各点可变累积器（bucket 内部类） |
| `NekoPluginBootstrap.java` | 263 | bootstrap 引擎：扩展点注册窗口 → freeze → 点优先收集；内部类 `ExtensionRegistry`、`BootstrapContext` |
| `NekoPluginRuntime.java` | 358 | 结果容器：`pointId → 产物` 的只读视图 + 类型化访问器 + 静态 `current` 单例 |
| `NekoPluginExtensionContext.java` | 32 | 收集期上下文：`client()` + `result(pointId, type)`（读先注册点产物） |
| `NekoPluginExtensionProvider.java` | 53 | 第三方插件注册自定义扩展点的 SPI（`registerPluginExtensionPoints(registry)`） |
| `NekoPluginExtensionRegistry.java` | 13 | 注册器接口（freeze 后写、id 重复即抛） |
| `NekoPluginExtensionHandle.java` | 49 | 注册返回的产物凭据（`isFinished`/`result`） |
| `PluginLifecycleRegister / RecipeLifecycleRegister / RecipeNamespaceRegister / RecipeSchemaRegister / TypeDocsRegister` | 34/10/9/13/10 | 各点累积器实现的收集接口 |
| `NekoCommonBuiltinPlugin.java` | 66 | 平台无关内置插件（`@RegisterNekoJSPlugin`）：`once/clearOnce/clientData` 绑定、TS/JSX 编译器、4 个 ScriptProperty、node module 类型文档 |
| `NekoCommonManualDeclarations.java` | 190 | type_docs 点的手写声明集合 |
| `AttachedDataHooks.java` / `PluginGenerationHooks.java` | 47/48 | **非扩展点**：直接遍历 `NekoJSBasePluginManager.getPlugins()` 触发 `attach*Data` 与 `generateData/Assets/Lang` 钩子 |

### 1.2 内置扩展点全表（14 个，均在 `BuiltinPluginExtensionPoints.builtIn()` 中按下列顺序注册）

| # | 扩展点 id | 对应 `NekoJSPlugin` 钩子 | 累积器 | finisher 产物 / 收尾副作用 | 环境谓词 |
|---|---|---|---|---|---|
| 1 | `nekojs:script_compilers` | `registerScriptCompilers` | `ScriptCompilerRegistry.createRuntimeRegistry()` | `freeze()` 后的 registry | 全环境 |
| 2 | `nekojs:script_properties` | `registerScriptProperty` | 传入的 `ScriptPropertyRegistry`（bootstrap 参数） | `Impl.freeze()` | 全环境 |
| 3 | `nekojs:bindings` | `registerBinding` | `ScriptTypedValue<BindingRegistry>`（按 ScriptType 惰性建槽） | `freezeBindings` → `Map<ScriptType, Map<String, Binding>>` | 服务器进程不收集 CLIENT 槽（`bindingPredicate`） |
| 4 | `nekojs:adapters` | `registerAdapters` | `JSTypeAdapterRegistry.Impl` | `List.copyOf(view())` | 全环境 |
| 5 | `nekojs:type_docs` | `registerTypeDocs` | `TypeDocsBucket` | `TypeDocsSnapshot`（priority 排序） | 全环境 |
| 6 | `nekojs:node_modules` | `registerNodeModules` | `NodeModulesBucket`（同 id 首胜 + warn） | `Map<String, String>` | 全环境 |
| 7 | `nekojs:node_type_docs` | `registerNodeTypeDocs` | `TypeDocsBucket` | `TypeDocsSnapshot` | 全环境 |
| 8 | `nekojs:events` | `registerEvents` | `EventGroupRegistry.Impl` | freeze 后 `Map<String, EventGroup>` | 全环境 |
| 9 | `nekojs:client_events` | `registerClientEvents` | 合并 events 产物的 Impl（initializer 里 `context.result(events)`） | 同上；专用服务器整点跳过 | `clientOnly` |
| 10 | `nekojs:recipe_namespaces` | `registerRecipeNamespaces` | `RecipeNamespacesBucket`（冲突 fail-fast） | `Map<String, RecipeNamespaceEntry>` | 全环境 |
| 11 | `nekojs:recipe_schemas` | `registerRecipeSchemas` | `RecipeSchemasBucket`（同 (ns,type) 首胜 + warn） | `Map<String, Map<String, RecipeTypeDefinition>>` | 全环境 |
| 12 | `nekojs:recipe_lifecycle` | `registerRecipeLifecycleHooks` | `RecipeLifecycleBucket` | `RecipeLifecycleHooks` | 全环境 |
| 13 | `nekojs:lifecycle` | `registerLifecycleHooks` | `LifecycleBucket` | `LifecycleHooks`（5 组钩子列表） | 全环境 |
| 14 | `nekojs:probe_backends` | `registerProbeBackends` | `ProbeBackendRegistry` | `lock() + setInstance()`（装配副作用） | 全环境 |

`NekoJSPlugin`（common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java）共声明约 28 个 default 钩子；其中 14 个走扩展点通道，其余走直接遍历（`registerApiSurface` 由 `bootstrapOwned` 单独循环、`attach*Data`/`generate*`/`modifyWorkspaceConfig` 由 hooks 工具类触发）。

### 1.3 注册链路（NeoForge 侧时序，src/main/java/com/tkisor/nekojs/NekoJSMod.java:102-142）

1. `NekoJSMod` 构造器 → `initializeScripts()`：`NeoForgePluginLoader.loadAnnotatedPlugins()` 发现 `@RegisterNekoJSPlugin` 类 → `NekoJSBasePluginManager`（priority 降序，缺省 1000，同序按实现类 FQN 兜底）。
2. `NekoPluginRuntime.bootstrapOwned(ownedPlugins, scriptProperties)`（NekoPluginRuntime.java:111）→ `CoreManagedApiBootstrap.load` 取 contracts/contributions。
3. `NekoPluginBootstrap.bootstrapOwned` → `collect()`（NekoPluginBootstrap.java:110-133）：
   - 新建 `BootstrapContext(Platform.isClient())` + `ExtensionRegistry`；
   - 注册 14 个内置点（`BuiltinPluginExtensionPoints.builtIn`）；
   - 按插件列表序回调 `NekoPluginExtensionProvider.registerPluginExtensionPoints`（自定义点永远排在内置点之后）；
   - `freeze()` 关闭注册窗口 → **点优先执行序**：逐点 `enabled 谓词 → initializer → 按插件序 collector（pluginType.isInstance 过滤）→ finisher → 产物发布到 3 处（bootstrap context / handle / 结果容器）`。
4. `registerApiSurface` 循环 → `buildLegacyReservations`（bindings + merged event groups → `LegacyGlobalReservation`）→ `FrozenApiRegistrySet`。
5. `new NekoPluginRuntime(...)`：构造器内 `publishRecipeSchemaOverrides()` 写入 `RecipeTypeDefinitionStorage.setPluginOverrides`。
6. `publish()`：`current` 单例 + `NekoRuntimeAccess.set` + `ScriptCompilerRegistry.useRuntime` + `installManagedCallbackSchemas`（`EventContractReflector.extractEvents` 从 eventGroups 反射派生契约事件 schema）。
7. 之后 `fireInit()` → `bindRuntime`（ScriptEventsJS）→ STARTUP 脚本加载 → `fireInitStartup()`。

### 1.4 产物消费者

| 产物 | 消费者（文件:行） |
|---|---|
| bindings | `common/.../script/ScriptEnvironmentFactory.java:64-75`（putMember 成 JS 全局）；`api/catalog/NekoScriptCatalog.java:169`（编目）；`NekoPluginBootstrap.buildLegacyReservations`（诊断） |
| event groups | `common/.../core/DefaultScriptEventBridge.java:34-39`（`EventGroupJS` 装成全局）；`NekoPluginRuntime.installManagedCallbackSchemas`（契约事件反射）；`ScriptEnvironmentFactory.addEventGroupSchema` |
| adapters | `common/.../core/NekoSandboxFactory.java:73`（`NekoSharedHostAccess`）；`NekoScriptCatalog` |
| node modules | `common/.../core/node/NekoNodeModuleInstaller.java:74` |
| script compilers | `ScriptCompilerRegistry.useRuntime`（NekoPluginRuntime.publish） |
| recipe schemas | `RecipeTypeDefinitionStorage.setPluginOverrides`（构造器副作用）+ `recipeSchemaOverrides()` 访问器 |
| probe backends | `ProbeBackendRegistry.setInstance`（finisher 副作用） |
| type docs / manual declarations | `NekoScriptCatalog`（优先级合并 type_docs + node_type_docs） |
| lifecycle / recipe lifecycle | `fireInit/fireInitStartup/...` 与 `beforeRecipeLoading/afterRecipes`（NekoPluginRuntime） |

### 1.5 加一个内置扩展点的牵连面（现状事实：要动的文件）

1. `common/.../api/NekoJSPlugin.java` — 加 default 钩子方法；
2. `common/.../core/plugin/BuiltinPluginExtensionPoints.java` — id 常量 + `builtIn()` 列表项 + （多数情况）bucket 内部类 + 产物 record；
3. （可选）新 Register 接口 `common/.../core/plugin/XxxRegister.java`；
4. `common/.../core/plugin/NekoPluginRuntime.java` — 产物字段 + 构造器取产物 + 类型化访问器；
5. 视消费位置再动 `ScriptEnvironmentFactory` / `DefaultScriptEventBridge` 等下游；
6. 测试与文档惯例：`common/src/test/.../plugin/NekoPluginExtensionPointTest`、`NekoPluginExtensionProviderTest`、`wiki/项目架构.md` §3。

### 1.6 自定义扩展点现状

`NekoPluginExtensionProvider` 生产代码 **零实现**（全仓 grep 仅 `NekoPluginBootstrap.java:116` 的 instanceof 分支 + 两个测试类）。机制自包含、有测试覆盖，但没有真实第三方消费者。

---

## 二、注册表包装清单

### 2.1 wrapper/event/registry/ 的 13 个 XxxRegistryEventJS

`BlockRegistryEventJS`、`CapabilityRegistryEventJS`、`CreativeTabRegistryEventJS`、`EnchantmentRegistryEventJS`、`EntityTypeRegistryEventJS`、`FluidRegistryEventJS`、`ItemRegistryEventJS`、`MobEffectRegistryEventJS`、`PaintingVariantRegistryEventJS`、`ParticleTypeRegistryEventJS`、`PotionRegistryEventJS`、`SoundEventRegistryEventJS`、`VillagerTypeRegistryEventJS`。

**共性**（11/13 严格遵循同一模板）：
- 构造器 `XxxRegistryEventJS(RegisterEvent rawEvent)` 持有原生事件；
- `create(Identifier id)` → `new XxxBuilderJS(id)` 加入 builders 列表并返回；另有 `create(String id, Consumer<XxxBuilderJS>)` 消费者重载；
- `registerAll()` 统一收口：`rawEvent.register(Registries.X, builder.getLocation(), builder::create)`；
- 文件级 `//? if neoforge {` 守卫包裹（等 fabric 端口）。

**差异点**：
- `BlockRegistryEventJS`：`create(String)` 无 Identifier 重载（自动补 `nekojs:` 命名空间）；静态 `PENDING_BLOCK_ITEMS` 跨 pass（BLOCK pass 收集 → ITEM pass 注册 BlockItem）；`RENDER_TYPES` 静态表在 `>=26` 存在、`<26` 走客户端即时 apply（守卫 5 处）。
- `ItemRegistryEventJS`：多一个 `createCustom(Identifier, Supplier<Item>)`；静态 `GROUP_ASSIGNMENTS` 由 `BuildCreativeModeTabContentsEvent` 监听消费。
- `EntityTypeRegistryEventJS`：静态 `PENDING_SPAWN_EGGS`（ENTITY_TYPE pass 收集 → ITEM pass 注册蛋，26.x 用 `SpawnEggItem`、旧版 `DeferredSpawnEggItem`）。
- `FluidRegistryEventJS`（最大，7.3KB）：**不走构造器模式**——post 出的事件对象是空壳，注册全靠静态 `registerTypes/registerFluids/registerBlocks/registerItems` 在不同 registry pass 分别落位；单个 `FluidBuilderJS` 连带注册 FluidType/源流体/流态流体/液体方块/桶（"连带注册"的现成范例）。
- `CapabilityRegistryEventJS`：挂的是 `RegisterCapabilitiesEvent`（非 `RegisterEvent`），`registerBlockEntity(id, capability, provider)` 先入 pending 列表，`apply(event)` 再落位；守卫 9 处（26.x transfer API vs 旧 IEnergyStorage/IFluidHandler/IItemHandler）。
- `EnchantmentRegistryEventJS`：`builder.create(RegisterEvent)` 是唯一接收事件对象的 create 签名。

### 2.2 wrapper/registry/ 的 BuilderJS（15 个）+ 支撑类

`BlockBuilderJS`、`CreativeTabBuilderJS`、`EnchantmentBuilderJS`、`EntityAttributeBuilderJS`、`EntityTypeBuilderJS`、`FluidBuilderJS`、`FoodBuilderJS`、`ItemBuilderJS`、`MobEffectBuilderJS`、`PaintingVariantBuilderJS`、`ParticleTypeBuilderJS`、`PotionBuilderJS`、`SoundEventBuilderJS`、`VillagerTypeBuilderJS` + `TaggableBuilder`（接口）+ `BuilderTags`。

- **共性**：构造器一律 `XxxBuilderJS(Identifier location)` + `getLocation()`；链式 setter；产物工厂方法。
- **命名不统一**：工厂方法 9 个类叫 `create()`，其余为 `createItem()`（Item）、`createBlock()`（Block）、`createAttributes()+createEntityType()`（EntityType）、`create(RegisterEvent)`（Enchantment）、Fluid 是 5 个工厂（`createType/createSourceFluid/createFlowingFluid/createLiquidBlock/createBucketItem`）。
- `FoodBuilderJS`、`EntityAttributeBuilderJS`、`BuilderTags` 是嵌套/辅助 builder，不直接对应注册表。
- `TaggableBuilder<Self>` 接口（`getTagRegistry()` + `tag(...)`）由 6 个 builder 实现：Block/Enchantment/EntityType/Fluid/Item/PaintingVariant。
- 守卫密度（if 守卫数）：CapabilityRegistryEventJS 9、PaintingVariantBuilderJS 8、FluidBuilderJS 8、ItemBuilderJS 7、EntityTypeBuilderJS 7、BlockBuilderJS 7、FluidRegistryEventJS 5、EnchantmentBuilderJS 5、BlockRegistryEventJS 5，其余 0-3。

### 2.3 分发链路

`src/main/java/com/tkisor/nekojs/listener/RegistryEventListener.onRegister(RegisterEvent)` 是唯一分发点（`NekoJSMod.registerEventListeners` 挂到 mod bus）。按 `event.getRegistryKey()` 的 if-else 链 13 个分支，每支固定三步：`new XxxRegistryEventJS(event)` → `RegistryEvents.XXX.post(eventJS)`（startup 脚本监听在此消费）→ `eventJS.registerAll()`。跨 registry pass 的连带逻辑（流体方块、BlockItem、桶、生物蛋）也集中在此文件的 ITEM/BLOCK 分支里，通过 XxxRegistryEventJS 的静态 Map 传递。

---

## 三、脚本侧注册 API 面（"脚本 API 必须稳定"的输入）

### 3.1 入口与脚本类型约定

- 目录：`nekojs/<type>_scripts/`，四类 `ScriptType`：STARTUP/SERVER/CLIENT/TEST（common/.../api/ScriptType.java:15-18）；AUTO_LOAD = STARTUP+SERVER+CLIENT；默认入口脚本 `src/main.js`（`ScriptBootstrap.generateDefaultScripts`）。
- 脚本属性（`@`-header）：4 个内置 `ScriptProperty`：`AFTER / MODLOADED / DISABLE / PRIORITY`（NekoCommonBuiltinPlugin 注册）。

### 3.2 事件组全局绑定（JS 全局名 = EventGroup 名，成员 = 事件名，`.listen(cb)`）

| 组（JS 全局） | 侧别 | 总线数 | 备注 |
|---|---|---|---|
| `ServerEvents` | server | 19 个定义（含 <26/>=26 双分支重复 4 个） | recipes/afterRecipes/tick*/生命周期 8 个/generateData/tags/lootTables… |
| `PlayerEvents` | server | 18 | loggedIn/crafted/inventoryChanged… |
| `EntityEvents` | server | 13 | damagePre/death/tickPre…（dispatchByEntity/Type 分发键） |
| `BlockEvents` | server | 14（本体 1 + `NeoForgeBlockEvents.bootstrap()` 补 13） | rightClicked/randomTick/modification… |
| `ItemEvents` | server+client | 9 | tooltip 为 client |
| `LevelEvents` | server | 10 | loaded/tick/explosion… |
| `CommandEvents` | server | 2 | register/command |
| `NetworkEvents` | server+client | 2 | server/client（按 channel key 分发） |
| `ProbeEvents` | server | 4 | modifyType/assignType/addGlobal/snippets |
| `ScriptEvents` | **startup** | 2 | server/client——STARTUP 脚本动态注册自定义命名事件组（`ScriptEventsJS.register`） |
| `RegistryEvents` | **startup** | 12 | item/block/entityType/fluid/creativeModeTab/soundEvent/mobEffect/potion/particleType/paintingVariant/villagerType/enchantment |
| `CapabilityEvents` | **startup** | 1 | register |
| `GoalEvents` | **startup** | 1 | register |
| `ClientEvents` | client | 20（含同事件别名：tick vs tickPre/tickPost 等） | generateAssets/lang/hud/screenRender… |
| `KeyBindEvents` | client | 3（**仅 >=26**） | pressed/released/tick |
| `RecipeViewerEvents` | client | 5 | JEI 集成（`integration/jei/RecipeViewerEventsPlugin` 单独注册） |

合计约 16 组 / 130+ 个事件总线定义（含守卫分支重复与别名）。前 13 组由 `NekoJSCorePlugin.registerEvents/registerClientEvents` 注册；RecipeViewerEvents 由独立插件注册。

### 3.3 全局绑定（Bindings）

- 平台无关（`NekoCommonBuiltinPlugin.registerBinding`）：`once`、`clearOnce`、`clientData`。
- 主体（`NekoJSCorePlugin.registerBinding`，src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java:123-205，46 次 register 调用）：
  - 无条件 ~38 个：`Ingredient`、`RecipeSchema`、`Fluid`、`Capabilities`、`FluidIngredient`、`FluidAmounts`、`Fluids`、`FluidStack`、`Color`、`UUID`、`StringUtils`、`Time`、`Utils`、`Network`、`ClientData`、`global`、`ItemStack`、`Items`、`Item`、`Block`、`BlockPos`、`Direction`、`Vec3`、`AABB`、`MutableComponent`、`DyeColor`、`SoundEvents`、`ParticleTypes`、`Blocks`、`EntityType`、`CompoundTag`、`Identifier`、`MobEffects`、`MobEffectInstance`、`DamageTypes`、`Component` 等（其中 Item/Block/Ingredient/Fluid/Capabilities/FluidIngredient 是 `DelegatingBinding` 代理 + `Binding.of` 显式 valueType 供 preflight 反射）；
  - 条件：`NativeEvents`（仅 STARTUP，reload 时 close() 注销旧原生监听）、`Test`（仅 TEST）、`TriState` 与 `Assets`（>=26；<26 的 TriState 走旧类守卫分支）、`Minecraft/Screen/Window/KeyMapping/InputConstants`（仅 CLIENT）。
- 其他插件补充：`VillagerTrades`（SERVER，VillagerTradesPlugin）、`EntitySelectors`（EntitySelectorsPlugin）、`DynamicRegistryBinding`（DynamicRegistryPlugin）、`PostEffects`（CLIENT，NekoPostEffectPlugin）。

### 3.4 绑定如何变成 JS 全局

`ScriptEnvironmentFactory.create`（common/.../script/ScriptEnvironmentFactory.java:54-98）：`eventBridge.bindEvents`（事件组 → `EventGroupJS` 全局）→ `pluginRuntime.bindings(scriptType)` 逐个 `putMember`（值为 Class 的经 `Java.type` 包装）→ `bindManagedGlobals`（managed API facade 代理）→ `ScriptBindingSchema.register` + knownGlobals（globalThis 属性名 ∪ polyglot 绑定键）供未定义标识符检查。

---

## 四、守卫密度统计（版本树 src/main/java）

### 4.1 总量

- 250 个 .java 文件中 **190 个含守卫（76%）**；`//? if` 守卫 **855 行**；`//?} else {` 双分支 **463 个**；闭合标记（`//?}`/`*///?}`）1318 处。
- 谓词分布：`>=26` **652（76%）**、`neoforge` **127（15%）**、`<26` **76（9%）**；`//? if fabric` 目前 **0 处**（fabric 节点以 41 处 `TODO(loader-port)` 注释标记未完成端口）。
- `forge/`（1.20.1 独立分支树）0 守卫；资源目录守卫 0（stonecutter `handlers.inherit` 把 json5/cfg 处理器套到 `nekojs.mixins.json`/`neoforge.mods.toml`）。

### 4.2 最密的 10 个文件（`//? if` 行数）

| # | 文件 | 守卫数 |
|---|---|---|
| 1 | `src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceScreen.java` | 55 |
| 2 | `src/main/java/com/tkisor/nekojs/client/gui/components/NekoCodeEditor.java` | 47 |
| 3 | `src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java` | 46 |
| 4 | `src/main/java/com/tkisor/nekojs/platform/nbt/NeoForgeNbtBinaryCodec.java` | 30 |
| 5 | `src/main/java/com/tkisor/nekojs/villager/VillagerTradeManager.java` | 24 |
| 6 | `src/main/java/com/tkisor/nekojs/wrapper/event/server/RecipeEventJS.java` | 21 |
| 7 | `src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java` | 21 |
| 8 | `src/main/java/com/tkisor/nekojs/client/posteffect/PostEffectManager.java` | 19 |
| 9 | `src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java` | 17 |
| 10 | `src/main/java/com/tkisor/nekojs/api/registry/NeoForgeRegistryQueryService.java` | 16 |

（紧随其后：`core/NekoJSCorePlugin.java` 15、`api/recipe/RecipeFilter.java` 15、`wrapper/fluid/FluidResolver.java` 14。）

按包分布（if 守卫数）：client 258、wrapper 193、api 83、bindings 55、js 49、mixin 43、platform 38、villager 26、network 22、listener 21、core 19、util 15、integration 2。

### 4.3 差异类别归纳（哪类差异最常引发守卫）

1. **版本轴 >=26/<26（85%）是绝对主体**，集中在：
   - 客户端 GUI 三屏（WorkspaceScreen + CodeEditor + ErrorDashboard 合计 148 守卫）：26.x `GuiGraphics` API 方法改名/删改（文本渲染、组件布局），类名级改名已被 replacement 兜底，方法级差异仍须守卫；
   - 26.x transfer API 替代旧 capability 接口（`CapabilityRegistryEventJS` 9 守卫：`ResourceHandler/EnergyHandler/FluidResource/ItemResource` vs `IEnergyStorage/IFluidHandler/IItemHandler`）;
   - 渲染机制变化（`BlockRegistryEventJS.RENDER_TYPES`、`PostEffectManager`）；
   - NBT/网络编解码（`NeoForgeNbtBinaryCodec` 30）、spawn egg 构造、payload 注册（`NekoJSNetwork` 17）。
2. **加载器轴 neoforge（15%）**：整文件级包裹（wrapper/event/registry 全部 13 个、listener、bindings/event 大部分），为 fabric 端口预留；目前 fabric 节点走 `stonecutterGenerate` 预处理副本（`stonecutter active "26.1.2"` 直编）。
3. **注册表包装层自身的版本差异**：第二节列出的守卫数（Capability 9、PaintingVariant/Fluid/Item/EntityType/Block builder 各 7-8）即"逐类型手写包装"在版本轴上的开销样本。

### 4.4 相关基础设施（现状事实）

- `stonecutter.gradle.kts` replacements 两组：`!mc_ids`（默认启用：`ResourceLocation↔Identifier`、`GuiGraphics↔GuiGraphicsExtractor`、`drawCenteredString↔centeredText`，注释记载曾把守卫 991 → 764）与 `mc_legacy_api`（默认关闭，27 个文件以 `//~ mc_legacy_api` 局部启用：`.location()↔.identifier()`、`displayClientMessage↔sendSystemMessage`、`listRegistries↔listRegistryKeys`）。
- 自研 `guardLint` Gradle 任务：守卫配对检查、文本块内守卫、分支首行 `/*` 歧义三类 lint。

---

## 附：关键计数速览

| 维度 | 数字 |
|---|---|
| core/plugin 文件 / 行数 | 17 / 1736 |
| 内置扩展点 | 14（id 见 §1.2） |
| 自定义扩展点生产实现 | 0（仅 2 个测试类） |
| RegistryEventJS / BuilderJS | 13 / 15（+TaggableBuilder+BuilderTags） |
| 事件组 / 事件总线定义 | ~16 组 / ~130（含守卫重复与别名） |
| 全局绑定 | ~50（core 46 次注册 + 其他 4 插件 5 个 + common 3 个） |
| 版本树文件 / 含守卫文件 | 250 / 190（76%） |
| `//? if` 守卫总数 | 855（>=26:652, neoforge:127, <26:76） |
| TODO(loader-port)（fabric 待端口） | 41 |
