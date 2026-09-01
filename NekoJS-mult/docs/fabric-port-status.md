# Fabric 移植状态与缺口台账（26.1.2-fabric）

> 状态：living document——本文档是 fabric 移植的缺口台账与批次记录。
> 勘察事实源：2026-09-01/02 两轮只读勘察（笔记存 `D:\mcmodDemo\NekoJS\.agent-notes\fabric-port-audit\`，
> 含事件矩阵 90+ 总线逐条对照、AW↔AT 对照表、TODO(fabric) 63 处全归类）。
> 关联 spec：[fabric-node-expansion.md](fabric-node-expansion.md)（版本图扩展，其第 0 步上移
> 会消化本文"节点孪生"小节的债）。

## 本轮已补全（2026-09-02）

1. **惰性 mixin 全量激活**（7 个编译进 jar 却无配置引用的共享 mixin）：
   `nekojs-fabric-shared.mixins.json`（MinecraftServerMixin / RecipeManagerMixin /
   ReloadableServerResourcesMixin / ResultSlotMixin / inject.MixinLevel / MixinPlayer /
   MixinMutableComponent）+ `nekojs-fabric-dynamic.mixins.json`（client:
   RegistryDataCollectorMixin），fabric.mod.json 模板挂载。
   Level/Player/Server/MutableComponent 四个扩展接口（`neko$*` 脚本面）由此生效。
2. **`server` 段挪公共 `mixins` 段**（存量 bug）：sponge-mixin 的 `server` 数组按物理端
   二选一，单人（物理客户端）下 ServerLevelMixin/ServerGamePacketListenerMixin/
   ServerCommonPacketListenerAccessor 原本不应用 → joinLevel/rightClicked 单人失效、
   FabricPackSync 对未 mixin listener 强转有 CCE 风险。
3. **配方脚本链路端到端打通**（本轮主体）：
   - 共享树连坐守卫去除（零 loader 依赖，纯"配方面未移植"连坐）：
     RecipeEntryJS / RecipeFilter / NekoRecipeNamespaces / RecipeRegistryProxy /
     RecipeJsonValueAdapter / IngredientAdapter / RecipeFilterAdapter /
     IngredientFactory / MinecraftRecipeHandler / GoalEvents / GoalRegisterEventJS /
     NekoErrorUIHelper（getErrorComponent 参数化，不再绑 NekoJSMod）
   - 流体面收进行内 `//? if neoforge` 守卫（fabric 无流体配方体系，与 FluidBuilder
     同判豁免）：RecipeEventJS 序列化×3、RecipeJsonBuilder 工厂×3、
     RecipeJsonValueConverter 的 fluid/sized case、RecipeEventSchemaHost 的
     FLUID_* case（fabric 求值补 default 抛 UnsupportedOperationException）
   - RecipeManagerMixin 共享树版从整文件 `neoforge && >=26` 改 `>=26` + 行内 loader
     分支（条件配方过滤 / server 与 runtime root 获取）；neoforge 求值产物逐行不变
   - IngredientResolver fabric 等价：wildcard/not/compound/intersection 用 vanilla
     展开集合运算（ingredientOfHolders 本就拍平成 Item[]，无功能损失）；
     fromStack 降级为纯物品形态（丢数据组件，随 ItemStackExtension 批次补）
   - IngredientJS 的 and/intersect/except/subtract/asStack/withCount 收进 neoforge
     守卫（数组语法 `["a","b"]` 在 fabric 仍覆盖 or 语义）
   - isCustom()（NeoForge patch）分支化：fabric 全部走 values 检查
   - fabric AW 补配方面 6 条（ShapedRecipe/ShapelessRecipe/SingleItemRecipe 的 result、
     ShapelessRecipe.ingredients、Ingredient.values）；AW 是按需补，非 AT 全量镜像
   - fabric 节点孪生 ServerEvents（同名组 "ServerEvents" 经 EventGroupRegistry 合并，
     补 recipes/afterRecipes 两条总线）
   - FabricCorePlugin 注册：Ingredient/RecipeSchema binding、minecraft 配方命名空间、
     ItemStack/Ingredient/RecipeFilter/RecipeJsonValue 四 adapter
   - FabricNekoJSCommands 的 reload server 补配方热重载（applyRecipeScripts）
4. **fireAfterInit 补调**（onInitialize 尾部，FabricRegistryAdapter 抽干后——与 NeoForge
   侧 FMLLoadComplete 的 onLoadComplete 后同位次）。
5. **GoalEvents 接活**：postRegister 在 STARTUP 脚本加载后调用（镜像 NekoJSMod），
   消费端（FabricEntityEventBindings.postJoinLevel → GoalRegistry）与 AW 早已就绪，
   原状态是"消费钩子在跑、注册面死"的残钩。
6. fabric.mod.json 补 `fabric-api: *` depends（callback 大量使用，缺声明在无 fabric-api
   环境会硬崩）；4 处过时注释修正（NekoJSFabricMod javadoc+死 import、FabricPlayNetwork、
   fabric-node.gradle.kts ×2）；guardLint 规则 7 升级为守卫深度感知（行内守卫内的 loader
   import 在对侧求值时整段消失，与整文件守卫同理豁免——配方面的流体分支因此合法）。

### 验证

- 四节点 compileJava 全绿（26.1.2 / 26.2.0 / 1.21.1 / 26.1.2-fabric）+ guardLint
  零问题（守卫块 251）。
- runClient 冒烟：mixin 全量应用零错误（defaultRequire=1 下注入失败即崩）。
- runServer 冒烟：配方脚本执行 + /nekojs reload server 热重载（见提交说明）。

## 节点孪生（待上移/合并）

- `fabric/event/FabricServerEventBindings`、`FabricNekoJSCommands`：fabric 桥，节点目录
  独有（expansion spec 第 0 步上移共享树后全节点共用）。
- `bindings/event/ServerEvents`（fabric 孪生）：共享树版是 NeoForge 原生事件签名整文件
  守卫，孪生只含 recipes/afterRecipes；后续把共享树版事件载荷中立化后合并回单副本。

## P0 剩余（破坏活面）

1. **网络自定义通道整链**：NetworkEvents 组注册 + NekoScriptPayload 双向注册
   （PayloadTypeRegistry.playC2S/S2C + ServerPlayNetworking/ClientPlayNetworking receiver）
   + NetworkJS 绑定（共享树守卫，需孪生或中立化）。发送面 PlayPacketDispatcher 已由
   FabricDispatcher 实现。脚本 `Network.sendToServer/sendToPlayer` 当前不可用。
2. **NekoScriptCatalog.setPlatformProvider 未调用**：fabric 的 probe/typings 不含 host
   扩展方法（运行时不受影响——@RemapByPrefix 成员重映射是共享的）。需要一个
   FabricCatalogPlatformProvider（镜像 NeoForgeCatalogPlatformProvider 的九个
   HostExtensionSource）。

## P1（功能面，现成 fabric-api 挂点）

- 注册 builder 7 件套（Item/Block/Fluid/EntityType/Enchantment/CreativeTab/ParticleType
  ——FluidBuilder 需 fabric 流体重设计）；NekoRegistryPointsPlugin 只注册了 5 个中性 builder。
- LevelEvents 全组（ServerLevelEvents.LOAD/UNLOAD、LevelTickEvents 现成）。
- PlayerEvents 剩余 15 总线（respawned/changedDimension 有现成 fabric-api 事件；
  containerOpened/inventoryChanged 需 mixin）。
- ItemEvents.tooltip（ItemTooltipCallback 现成）、canPickUp/pickedUp/dropped/foodEaten（mixin）。
- KeyBindEvents（KeyBindingHelper + ClientTickEvents 轮询）。
- CommandEvents.register（CommandRegistrationCallback 现成——FabricNekoJSCommands 已挂
  同款回调，补脚本命令注册面即可）。
- 测试树放行三个中立适配器测试（BlockPos/Vec3/HolderAdapter——被测面已在 fabric 注册）。
- common-api-processor 接线（annotationProcessor + -Anekojs.platform=fabric）。

## P2（大块移植，按需排期）

脚本编辑器同步 8 包 + 工作区 GUI（NekoWorkspaceScreen/NekoCodeEditor 需 AW：MultiLineEditBox
等）；配方面余量（ItemModification/BlockModification 重放、tagsUpdated/lootTableLoad/
datapackSync、generateData）；ItemStackExtension+MixinItemStack 链（ItemStack 脚本扩展面）；
BlockStateExtension（hasTag）；TagLoaderMixin（ServerEvents.tags）；后处理三件套
（ShaderManagerMixin/PostEffectManager）；VillagerTrades（fabric 无 RegisterVillagerTradesEvent
等价，需 mixin 或重写）；JEI；BlockModelGenerator（fabric 无 datagen 事件，需自建管线）。

## B 类豁免（NeoForge 专属概念，移植=重新设计）

CapabilityEvents（fabric 无 capability API，DataComponent 方案另议）；
BlockEvents.toolModification；generateData/generateAssets/lang（datagen 事件模型）；
流体配方面（FluidResolver/FluidIngredientJS/SizedIngredientJS/FluidJS——fabric 无流体体系）。

## 语义差异记录（非缺口）

- fabric 的 ServerEvents.starting/aboutToStart 都在 SERVER_STARTING（世界装载前）触发
  （NeoForge 的 Starting 在世界装载后）——脚本请到 started 再访问玩家列表。
- damagePre 只能取消不能改伤害量（fabric ALLOW_DAMAGE 语义）。
- CLIENT 脚本加载在 CLIENT_STARTED（晚于 NeoForge 的构造期）。
- fabric 无条件配方系统：neoforge:conditions 配方在 fabric 全量进工作集。
