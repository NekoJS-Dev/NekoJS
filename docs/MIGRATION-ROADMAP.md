# NekoJS 架构重设计迁移路线图

> 依据：wayfinder 地图 [#38](https://github.com/NekoJS-Dev/NekoJS/issues/38) 全部 12 张决策票 + ADR-0001~0009 + [RE1 现状盘点](https://github.com/NekoJS-Dev/NekoJS/issues/39) / [RE2 外部先例](https://github.com/NekoJS-Dev/NekoJS/issues/40)。
> 起点：PR #37 记录的三大痛点（扩展点插桩少、EP 互相依赖难处理、内置 EP 与核心链耦合）+ 全树繁复感。
> 本文档是执行清单：按阶段推进，每阶段可独立验收。

## 成功判据与验收方式

| # | 判据 | 验收 |
|---|---|---|
| ① | 新增一种注册表类型 ≤ 1 个新类 | P2 末**试做验收**：真加一个新类型（如 Enchantment 变体），数变更文件 |
| ② | 新增一个扩展点 = 1 个自包含文件 + 清单 1 行 | P1 末**试做验收**：真加一个 EP，数变更文件 |
| ③ | 贡献者不看内部文档即可新增注册表类型 | **真人测试**（理想人选：ZZZank） |

## 发布策略（ADR-0009）

三阶段两发布：P1 可发布（插件 API breaking、脚本无感）→ **P2 唯一脚本 breaking 发布**（大版本 + wiki 迁移表）→ P3 无破坏。breaking 只发生一次。

---

## P0 纪律先行（无破坏）✅ 2026-08-28 完成

先让工具看住，再动代码。

- [x] `guardLint` 扩展：密度阈值（文件 ≤20 硬限 / 连续守卫段 >8 软告警）、`// guard-exempt(20): 理由` 豁免标记校验与清单输出、wrapper 层零 loader import 检查（报告模式，`wrapperLoaderImportHardFail` P3 翻转）（ADR-0008）
- [x] 模块边界检查：L1/L2 零 MC/Loader/Graal import——落地于 guardLint 的 import 扫描（ADR-0007 修订：与密度检查同一工具入口；原定 processor 方案改为 guardLint，核心决策"不引新依赖"不变）；负向测试已验证拦截
- [x] 现状守卫基线：7 个超限文件打豁免标记（GUI 三屏 55/47/46 → P3 26.2 基准化；NbtBinaryCodec 30 / VillagerTradeManager 24 → P3 facade 候选；RecipeEventJS 21 → P2 改写；RecipeManagerMixin 21 → P3 复查）；wrapper 层 26 文件 55 处 loader import 进入报告基线

## P1 扩展点系统（插件 API breaking，脚本无感）

ADR-0001 / 0002 / 0003 / 0007。对应判据②。

- [x] V2 核心落 `common`（全部完成：builder 唯一入口 + merge 必填 + 拓扑 freeze + 两档访问 + 密封；**七个过渡工厂已删**，测试改 builder，全绿）
- [x] datagen 四钩子 Point 化（GenerationPoint.Contributor 直发模式，全绿）；**契约迁移已解锁**：NekoJSPlugin 归零引擎包签名，迁移=搬文件+根脚本接线。原侦察记录：：瘦身后剩余签名类型中 `JSConfigModel`（core.fs）、`DataGeneratorJS`/`LangGeneratorJS`（wrapper）在引擎包，直接迁移引发级联；且 `api.*` 包目前在 common 与 common-api 间**分裂共存**。推荐路线：先把 datagen 钩子（generateData/generateAssets/generateLang/modifyWorkspaceConfig）Point 化成 Contributor，NekoJSPlugin 归零引擎签名后再迁；另 common 无独立 build.gradle.kts，依赖接线在根构建脚本
- [ ] 契约物理迁移 common-api——**配方已定（包名保持，零 import 改动）**：`common` 已依赖 `common-api`（common/build.gradle:62 `api project(':common-api')`），故只需**按包平移文件**：`NekoJSPlugin` + 四个活签名类型（`ApiContributionRegistry`→连带 api/surface 簇、`RecipeLifecycleContext`→连带 api/recipe 簇、`ScriptType`/`ScriptTypePredicate`、`AttachedData`）从 common 移入 common-api **同包名目录**，编译报错即把下一层依赖类型一并平移，循环至绿；common-api 已有 `checkApiBoundaries` 检查模式可验证
- [x] **契约物理迁移完成（2026-08-29，全平台绿）**：`NekoJSPlugin`/`ScriptType`/`ScriptTypePredicate`/`ApiContributionRegistry`+surface 簇/capability 簇/`RecipeLifecycleContext`/`AttachedData` 包名平移入 common-api；`EnvironmentKeyFactory`（平台耦合）留守 common；common-api 补 jspecify。前置：ScriptType 抽薄（`ScriptTypeEnv` 承载 scriptsDir/logger/logFile/makeId，约 40 调用点改写）
- **ScriptType 抽薄设计（已定待实施）**：① 枚举常量停止捕获 `NekoJSPaths`（public `path` 字段是最大耦合，类初始化期 fs 访问）→ 迁引擎侧 `ScriptTypeEnv.of(type).path()/logger()/logFile()`（common），契约枚举只留 name/cname/predicate；② `.logger()` 15 文件、`.path` 使用点全量改写为访问器；③ 完成后即可整体平移契约簇。工作量：中等（一次专注会话）
- [x] 14 个内置 EP → Point 文件（全部完成，三节点 + 全量测试 + guardLint 绿）
  - 进度：**14/14 全部接线完成（三节点编译 + 全量测试 + guardLint 绿）**——含闭包特例 script_properties/bindings（静态工厂）与 7 实现方的 bindings 收官；NekoBuiltinPointsPlugin 清单 14 行齐备，BuiltinPluginExtensionPoints 仅剩死代码待删——…前七个见历史；**events / client_events**（V2 dependsOn+result 首例上线：ClientEventsPoint 声明时序依赖 + initializer 数据依赖读 events 产物合并；mergedEventGroups 助手迁 EventsPoint；实现方 NekoJSCorePlugin/RecipeViewerEventsPlugin/MixedEventsPlugin 已迁 Contributor）
  - **⚠️ lifecycle 系特例**：`registerLifecycleHooks`/`registerRecipeLifecycleHooks` 默认实现把 `this::init` 等便捷钩子接进注册表——迁移时**所有覆盖 `init()`/`initStartup()`/`afterInit()`/`before(After)ScriptsLoaded()`/`beforeRecipeLoading()`/`afterRecipes()` 的插件都要补 implements Contributor**（普查钩子名会漏掉这些便捷覆盖，需按方法名单独扫）；Contributor 接口携带同样的默认实现，覆盖便捷方法的插件只需加 implements
  - 小坑：Point 内嵌 product record 用 public 时 javac 报"规范构造器无效（更强访问权限）"——保持与原 Builtin 相同的包私有即可（Runtime 同包）
  - **Point 化配方**（每个 EP 照抄）：① 建 `XxxPoint`（ID 常量 + `Contributor extends NekoJSPlugin`（钩子签名原样搬入）+ `Bucket`（冲突处走 `MergePolicy.resolveDuplicate`，实现 `Sealable`）+ `POINT` 常量 builder（merge 必填））② `NekoBuiltinPointsPlugin` 清单加一行 ③ `BuiltinPluginExtensionPoints` 删条目+bucket ④ `NekoJSPlugin` 删钩子（javadoc 引用同步修）⑤ `NekoPluginRuntime`/下游常量改引 `XxxPoint.ID` ⑥ grep 钩子名迁全部实现方（加 `implements XxxPoint.Contributor`）⑦ `:common:test` + `:26.1.2:compileJava` 验绿
  - 特例预告：`bindings`（client 谓词闭包）与 `script_properties`（scriptProperties 闭包）不能是静态常量——Point 文件提供静态工厂方法，清单注册时构造；`client_events` 用 `dependsOn(events)` + initializer `result(events)`（V2 语义首秀）
- [x] `NekoBuiltinPointsPlugin`（清单 14 行 + bootstrap 显式提升 + 闭包构造参数）；`BuiltinPluginExtensionPoints`（394 行）**已删除**
- [x] `NekoJSPlugin` 瘦身（33 → 15 default 方法，纯生命周期 + apiSurface + 便捷钩子，直接 breaking）
- [ ] `NekoPluginRuntime` 访问器迁 handle（迁移期委托保留，**P1 末删除**）
- [ ] 验收：判据②试做 + 既有启动冒烟（`/nekojs` 命令、probe 生成、脚本三目录加载）

## P2 通用注册表 + 脚本面切换（唯一脚本 breaking，大版本）

ADR-0004 / 0005 / 0006。对应判据①。

- [x] **NeoForge 平台适配层完成（垂直切片贯通）**：`RegistryEventAdapter`（订阅 RegisterEvent，首 pass 前投递一次收集事件 → 按 loader 序逐 pass 抽干；连带派生条目在目标注册表自身 pass 投递，目标 pass 已过即报错跳过；load-complete 诊断未消化内容）+ `RegistryEventJS`（ProxyObject 糖方法从 registry_types default 派生 + custom/register）+ `RegistryRepository`（先攒后建 + 对象层 failFast）+ `RegistryEvents.register` 单入口（同名组合并挂 bus，与旧 12 入口共存至 Builder 重写完成）+ 首个内置类型 soundEvent/basic。途中修复：EventSchemaRegistry 同名组整组覆盖 bug、回调预检对 ProxyObject 动态成员误报
- [x] **registry_infos / registry_types 两个 EP 完成（版本树 wrapper/registry/gen/）**：贡献式扫描根（反射 Registries ResourceKey 字段）+ overrideWarn 类型表 + default 类型 + dependsOn+result 数据依赖（V2 用例②）+ RegistryObjectBuilder 基类（public field / implements Supplier / handleAdditionalObjects）；经 NekoRegistryPointsPlugin（版本树 provider）注册。**判据②试做通过**：新增 2 个引擎级 EP = 2 个 Point 文件 + 1 个 provider 文件 2 行，零核心类改动
- [x] **12 个新 Builder 重写完成（gen/ 包，覆盖旧 15 个的注册面）**：public field + 三件套全部落地——Block 预创建 item 子 builder（`b.item.maxStackSize=16` 直改 / `noItem()` 置 null）+ BlockItem 连带注册；Fluid 四子（type/flowing/block/bucket）经 handleAdditionalObjects 一次收齐，全部投给 FLUID 之后的 pass，旧跨 pass 静态状态消亡；EntityType spawn egg 连带 ITEM + 属性经 drainPendingAttributes 挂 EntityAttributeCreationEvent；Item groupTab / Block renderType 侧通道由现有监听器并集消费；子 builder（Food/attributes/goals）复用旧类。**判据①静态部分兑现：新增注册表类型 = 1 个 Builder 类 + 清单 2 行**（neoforge 守卫内 7 类：item/block/fluid/entityType/enchantment/particleType/creativeModeTab——后两者因 fabric 映射差异（builder() 无参 / 构造器 protected）不可跨平台）
- [x] **脚本面切换完成（旧管道整体拆除）**：旧 12 类型化入口（bindings/event/RegistryEvents）、12 个 XxxRegistryEventJS、12 个 XxxBuilderJS、RegistryEventListener（13 分支 if-else 与流体四 pass 静态协调机）全部删除；幸存钩子（EntityAttributeCreation / BuildCreativeModeTabContents / EntityJoinLevel→GoalRegistry）迁入 RegistryEventAdapter / NekoJSMod；GoalRegistry、NekoJSClient（渲染器+蛋模型）、BlockModelGenerator 改接 gen EntityTypeBuilder/BlockBuilder 账本；（按用户裁定：validator 不加旧写法迁移指引，报错保持普通形式；wiki 迁移表承担迁移 UX）；TypeDoc 目录与 nekojs.entity-goal 手写 .d.ts 声明更新为新 API
- [x] **probe/catalog 验证完成**：`nekojs.registry` 手写声明（gen/NekoRegistryDeclarations，挂 TypeDocsPoint）——RegistryEvent 全糖方法双形态 + 12 个 builder 的 public field 面，neoforge 糖方法平台守卫、painting title/author 版本守卫；`nekojs.entity-goal` 声明同步为 public-field 面
- [x] **wiki 重写完成**：《注册新内容》整页重写（单入口 + 12 builder 参考 + 连带注册说明 + 迁移表）；《快速开始》示例、《事件参考》RegistryEvents 节/契约表/契约语义同步
- [x] **判据①真机冒烟通过（26.2.0 runServer，2026-08-29）**：`RegistryEvents.register` 收集回调执行、item/block（含预创建 item 子 builder）/fluid/potion 四例 builder 全生成、连带注册全投递（零 undelivered/duplicate 诊断）、服务器 `Done (0.294s)` 全日志 0 ERROR。途中修复：回调预检对 ProxyObject payload 的 managed 契约路径误报（rootValue 整体退回 Unknown）；暴露并绕过 dev 服务器残留进程锁世界锁的问题。**P2 全部完成**

## P3 模块与守卫治理（无破坏）

ADR-0007 / 0008。

- [x] **GUI 主干 26.2 化完成**：三屏（NekoWorkspaceScreen/NekoErrorDashboardScreen/NekoCodeEditor）共享树改纯 26.2 求值版（各剩 1 个整文件守卫），1.21.1 完整求值变体落 `versions/1.21.1/src`（节点 src 不走守卫求值，须放已求值版——实测得出）。148 行守卫消失
- [x] **剩余 4 豁免文件节点拆分完成（facade 计划由节点拆分替代，更贴合 M2 裁定）**：RecipeManagerMixin / NeoForgeNbtBinaryCodec / VillagerTradeManager / RecipeEventJS 同法拆分——共享树纯 26.x + 1.21.1 节点求值变体
- [x] **守卫密度达标：豁免清零**（守卫块 863 → 626，超限文件 7 → 0；guardLint 全绿）
- [ ] wrapper 层零 loader import 达标（guardLint 拦截）

## P4 收尾与跟进

- [x] **guardLint 挂 CI 强制**（ci-build.yml 新增独立步骤；processor 测试原已在 CI。wrapper loader import 维持警告基线，hardFail 待 Fabric 功能端口完成后翻转）
- [x] **Fabric 脚本运行时 bring-up 完成（26.1.2-fabric，2026-08-29）**：`FabricPluginLoader`（内置清单 + nekojs entrypoint，bootstrap 内部构造的 EP 清单插件不入列）+ `FabricCorePlugin`（BlockEvents 组挂进绑定）+ `FabricRegistryAdapter`（收集事件 post 一次 → vanilla Registry.register 单批抽干，跨注册表懒引用免序）+ `NekoJSFabricMod` 完整装配（与 NekoJSMod 同构）。**真机冒烟通过**：startup 脚本加载、RegistryEvents.register 收集回调执行（soundEvent/mobEffect 糖方法）、服务器 Done (0.498s) 零错误。GraalMC curse 文件按加载器分 build（用户提示查明）：8456810=NeoForge 构建（catalog 默认）、8456812=fabric 构建（fabric.gradle.kts resolutionStrategy 定向），两平台冒烟均通过。ICU4J 踩坑记录：loom 对 MC manifest 库去重导致 icu4j 依赖被从 dev run 类路径剥离（bundled/runtimeOnly/api 传递全灭；探针对照 commons-text 可进），解法 = 类提取进 sourceSet 输出目录（loom 不过滤源集输出，分发 jar 亦随之携带）。**尚未接**（后续批次）：ServerEvents/PlayerEvents 等主体事件组 fabric 桥、网络 payload 通道、ScriptEvents 自定义事件、客户端专属装配
- [x] **Fabric 服务端事件面 v1（2026-08-29，P4-c）**：中立 payload（ServerLifecycleEventJS / ServerTickEventJS / PlayerLifecycleEventJS，共享树无守卫，成员名对齐契约 getter）+ FabricServerEventBindings（生命周期 5 时机 + tickPre/Post + loggedIn/Out，同名组 EventGroupRegistry 合并挂载）+ SERVER 脚本加载钩子（SERVER_STARTING → reload(SERVER)，对齐 NeoForge 在 datapack reload listener 注册期的首载时机）。真机冒烟通过：starting/started（event.server.getPlayerList() 实取值）/tickPost 全触发、零错误。已知时序差异：fabric starting 早于 NeoForge 同名事件（世界装载前，playerList 未就绪）。余量：chat/EntityEvents/ItemEvents、网络、ScriptEvents、客户端装配
- [x] **Fabric 实体事件 + 类型适配器 + chat（2026-08-29，P4-d）**：① FabricCorePlugin 挂 AdaptersPoint——注册 17 个平台无关类型适配器（EntityType/Block/Item/Identifier/...），这是 dispatch 字符串键（EntityEvents.death('minecraft:zombie',...)）落注册表类型的必要通道（此前缺失报 Unsupported target type，适配器注册此前只在 NeoForge 核心插件）；② EntityEvents.death 经 ServerLivingEntityEvents.ALLOW_DEATH（死亡判定前，语义对齐 NeoForge LivingDeathEvent）+ LivingDeathEventJS 中立 payload；③ PlayerEvents.chat 经 ServerMessageEvents.CHAT_MESSAGE + ServerChatEventJS（player/username/message 字符串成员，对齐契约）。真机冒烟通过：forceload + PersistenceRequired 僵尸 /kill → death 按实体类型 dispatch 触发、source=genericKill。踩坑记录：无玩家时出生区块不保持加载（实体被丢弃非死亡）、敌对生物无玩家瞬间 despawn、armor stand 重写死亡路径绕过钩子——冒烟须用 PersistenceRequired 常规生物 + forceload。joinLevel 暂缺：fabric-api 无「实体加入世界」等价事件（ENTITY_LOAD 仅覆盖存储装载），待 fabric mixin 通道
- [ ] 判据③真人测试（ZZZank）
- [ ] Forge 1.20.1 端口照原 roadmap（B4 之后），不进本图

---

## 决策记录索引

| ADR | 主题 | 票 |
|---|---|---|
| [0001](adr/0001-extension-point-model-v2.md) | 扩展点模型 V2（Collector + Point 文件 + 四档 merge） | E1 #41 |
| [0002](adr/0002-extension-point-dependency-semantics.md) | 依赖语义（dependsOn + result 双轨、拓扑 fail-fast） | E2 #42 |
| [0003](adr/0003-builtin-extension-point-registration.md) | 内置点注册（清单插件 + 显式提升） | E3 #43 |
| — | 原型验证（`prototype/ep-v2-skeleton`，9 违例场景全过） | E4 #44 |
| [0004](adr/0004-generic-registry-model.md) | 通用注册表（三层解耦 + 先攒后建 + 三阶段生命周期） | R1 #45 |
| [0005](adr/0005-builder-and-co-registration.md) | Builder 与连带注册（public field + 回调三件套） | R2 #46 |
| [0006](adr/0006-script-registry-api-clean-switch.md) | 脚本面一次性切换（无兼容层） | R3 #47 |
| [0007](adr/0007-module-boundaries.md) | 模块边界（四层归属判据） | M1 #48 |
| [0008](adr/0008-guard-discipline.md) | 守卫纪律（阈值 + GUI 26.2 基准 + replacements 边界） | M2 #49 |
| [0009](adr/0009-release-and-acceptance-strategy.md) | 发布与验收（三阶段两发布、试做验收、CI 强制） | G1 #50 |

术语表见根目录 `CONTEXT.md`。地图：[#38](https://github.com/NekoJS-Dev/NekoJS/issues/38)。
