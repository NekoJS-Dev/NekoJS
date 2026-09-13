# Ticket 25 实施报告：DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径

> 工单：`docs/architecture-refactor/implementation-tickets/25-query-tools.md`（已认领 in-progress，Assignee=zcode-agent）。
> 权威 spec：`docs/architecture-refactor/specs/04-public-contract-and-plugin-model.md`（contract/tier/capability 口径）、
> `08-ported-features-event-surface.md`（EntitySelectors 保持 factory/query binding，不事件化）、`07-validation-and-migration.md`。
> 依赖消费：09 号票交付的 managed surface 机制（`NormativeApiContract`、`CapabilityStatus` 三态、`LegacySurfaceAdapter`
> 观察面、deterministic fixture 模式、`REGENERATE.md` 纪律）。
> 基线 commit：`98478559`（本票认领提交）。工作分支 `ticket-25-query-tools`。

## 0. Commit 清单

| # | Commit | 内容 |
|---|---|---|
| 1 | `4d2bc744` docs(baseline): ticket 25 query tools inventory | Phase 1 现状盘点（§1） |
| 2 | `ef938958` test(api): ticket 25 query binding contract classification + capability matrix | Phase 2 source contract 归类 + capability 三态（§2） |
| 3 | `62fceb99` feat+test(query): ticket 25 DataMap query binding fixture | Phase 3（§3 #3） |
| 4 | `c2492524` feat+test(query): ticket 25 EntitySelectors query binding fixture | Phase 4（§3 #4） |
| 5 | `a9ba2010` test(query): ticket 25 TS/Python declaration parity via shared probe IR | Phase 5（§3 #5） |
| 6 | `fix(query): ticket 25 让查询 binding 真正可命中`（源码修复，§5.1） | DataMap 实例注册；EntitySelectors 实体作用域 / 未知 type tag 报错 |
| 7 | `test(query): ticket 25 fixture 对齐游戏内实读语义` | fixture 重写 + declaration golden 对齐 + capability golden 注释纠错 |
| 8 | `docs(baseline): ticket 25 capability/trace/删除条件 + AC 判定 + runServer 证据` | 本报告 §4-§8 + `evidence/` |

写作顺序：先 inventory/归类/fixture 骨架（1-5），再用 `runServer` 实读把「脚本可见语义」钉死（6-7），最后收口（8）。
第 6 步是**修复**而非重构：三条发现都在游戏内实证为「公开文档示例拿不到结果 / 静默 no-op」，不修则 AC1/AC2/AC3 拿不到可验证证据。

## 1. 现状盘点（Phase 1，不改代码）

### 1.1 DataMap 域

| 观察物 | 现状 |
|---|---|
| binding 类 | `src/main/java/com/tkisor/nekojs/bindings/static_access/DataMapJS.java`（整文件 `//? if neoforge` 守卫，1.21.1/26.x NeoForge 共享同一文件；无版本内守卫） |
| runtime member | `furnaceFuel(ItemStack) -> Integer`、`compostable(ItemStack) -> Float`；命中返回值，未命中返回 `null`（javadoc 明示 `??` 兜底语义） |
| 注册入口 | `NekoJSCorePlugin.registerBinding`（:165 起）`registry.register("DataMap", ...)`——**票前是 class binding**（`DataMapJS.class`），全部 ScriptType 可见；`NekoJSCorePlugin` 整文件 neoforge 守卫 |
| MC-facing 类型持有 | `NeoForgeDataMaps.FURNACE_FUELS` / `COMPOSTABLES`（`net.neoforged.neoforge.registries.datamaps.builtin`）；holder 经 `BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())`——全部在 binding 类内，共享层不含 MC 类型（DataMapJS 本身住共享树但有 loader 守卫） |
| 替代查询面（managed） | `Registry` 全局（common `RegistryFacade`/`RegistryView`）已有 `dataMapIds()` / `dataMapValue(typeId, id)`，portable-core contract 反射符号（`global:Registry`、`member:RegistryView.*`），实现 SPI `RegistryQueryService`（common，零 MC import）+ `NeoForgeRegistryQueryService`（根 src，neoforge-only） |
| 测试覆盖（本票前） | 仅 `CoreBindingsGlobalsTest` 断言 `DataMap` 名字注册进 SERVER 绑定集（preflight 未定义标识符防护）；无行为/声明/capability fixture |
| 文档 | `wiki/全局绑定.md` 无 DataMap 章节 |

### 1.2 EntitySelectors 域

| 观察物 | 现状 |
|---|---|
| binding 类 | `src/main/java/com/tkisor/nekojs/util/selector/EntitySelectorsJS.java`（工厂 + 查询入口，无守卫=全节点编译）、`EntitySelectorBuilderJS.java`（26.x 形态；1.21.1 镜像在 `versions/1.21.1/src/main/java/com/tkisor/nekojs/util/selector/EntitySelectorBuilderJS.java`，头注释要求两侧同步） |
| runtime member | factory：`create(Consumer<Builder>)`、`builder()`、6 个预设（`allPlayers/allEntities/nearestPlayer/nearestEntity/randomPlayer/randomEntity`）；query：`find(ServerLevel, EntitySelector[, x,y,z])`。builder：`type/typeTag/isAlive/x/y/z/dx/dy/dz/distance/distanceBelow/distanceAbove/name/gamemode/team/tag/limit/order*/create` |
| 注册入口 | `EntitySelectorsPlugin`（`@RegisterNekoJSPlugin`，实现 `NekoJSPlugin` + `BindingsPoint.Contributor`）——**仅 SERVER 与 TEST** ScriptType 注册 `EntitySelectors`；NeoForge 走注解扫描发现，Fabric 走 `FabricPluginLoader` 内置清单（`versions/26.1.2-fabric/.../FabricPluginLoader.java:37`） |
| 版本 Adapter | `EntitySelectorFactory`（`//? if >=26`）：26.1/26.2 的 `MinMaxBounds` 包名差异（criterion/predicates）经反射按 FQN 解析 `Doubles` 工厂与 13 参 `EntitySelector` 构造器；1.21.1 镜像不走工厂（FQN 唯一，直接静态构建）。构建的 selector `usesSelector=false`，不触发原版 selector 权限校验 |
| 测试覆盖（本票前） | 无 |
| 文档 | `wiki/全局绑定.md` 有章节；**但标注「仅 NeoForge」与代码矛盾**（Fabric 内置清单注册了该插件且类无守卫）——capability 以代码为准，wiki 差异记入 §7（wiki 非本票写集，不改） |
| 错误现状 | `Objects.requireNonNull(level, "level")`（裸 "level"，无域/入口）、builder 校验消息（`limit must be at least 1` 等）无域/入口前缀、`find` 执行失败兜底 `IllegalStateException("entity selector query failed", e)` |

### 1.3 既有 tier / contract 归类事实（09 机制口径）

- `DataMap`、`EntitySelectors` 经 `BindingsPoint`（`NekoJSPlugin.registerBinding`）收集 → `NekoScriptCatalog.bindings(...)` 转成 `BindingCatalogEntry` → `LegacySurfaceAdapter.convert` 产出观察 kind `global:<名>` + 占位 void 签名的 `ApiSymbol`，存入 `NekoScriptCatalogSnapshot.legacySurface`（**独立字段**，不与 `managedApis` 合并）。
- portable-core contract（`CoreManagedApiBootstrap.buildContract`，反射 7 facade + 5 数据类型 + common 事件注册类）**不含** `global:DataMap` / `global:EntitySelectors`——「当前入口存在 ≠ managed stable」在结构上成立。
- 两域均无事件 owner（`EntitySelectorsPlugin` 不实现 `EventsPoint`/`ClientEventsPoint` Contributor）、无生命周期 Point、无事件包装器。
- `DataMap` 的替代查询面（`RegistryView.dataMap*`）**已在** managed contract 内（`Registry` facade 反射符号），返回 JSON 字符串（portable），不暴露可变 registry view。

### 1.4 版本/加载器 API 差异（实读）

| 差异点 | 1.21.1 | 26.1 / 26.2 | fabric |
|---|---|---|---|
| data map API | NeoForge `NeoForgeDataMaps`（datamaps 自 20.4+ 存在，1.21.1 编译通过） | 同左（26.x `ItemStack` 无 `getItemHolder`，经 `wrapAsHolder` 等价） | 无 NeoForge data map 体系；`DataMapJS` 整文件守卫不编译，binding 不注册；`RegistryQueryService` 无 fabric 实现（默认方法静默空值） |
| `EntitySelector` 构造 | 13 参构造器直接调用（`MinMaxBounds.Doubles.max()` 返回 `Optional`） | 13 参构造器经 `EntitySelectorFactory` 反射（`Doubles.max()` 返回 `Optional`、包名两版本不同） | 与 26.x 同（共用共享树 + 工厂反射） |
| `Registry` tag 枚举 | `getTagNames()` | `getTags()`（`getTagNames()` 已移除） | 与 26.x 同 |
| `Identifier`/`ResourceLocation` | stonecutter `!mc_ids` 改名共享树统一写 26.x 形态 | 同左 | 同左 |
| `EntitySelector` 执行 | `selector.findEntities(source)`，`CommandSourceStack` 语义一致 | 同左 | 同左 |

### 1.5 裸 JUnit 约束（fixture 设计输入）

- 26.x 节点裸 JUnit 无 FML Loader：`BuiltInRegistries` 类初始化链抛错（`src/test/java/com/tkisor/nekojs/testfixture/VanillaRegistryProbe.java` 实测固化）→ **依赖真实注册表/数据包/`ServerLevel` 的命中值断言无法在 `check` 内执行**，走 `runServer` + `/nekojs test` 脚本 fixture（§5）。
- 不依赖注册表的输入校验与反射面断言可在裸 JUnit 执行（builder 校验、null 守卫、binding 注册、probe 派生）。

## 2. source contract 归类与 capability（Phase 2）

### 2.1 归类结论（按 09 机制口径）

| 域 | source contract 归类 | tier / 承载面 | 事件 owner | 生命周期 Point |
|---|---|---|---|---|
| DataMap | query binding（只读 data map 快捷查询） | 共享树 neoforge-guarded class/instance binding（`BindingsPoint` 收集）→ catalog 观察面 `global:DataMap`（占位 void 签名，`LegacySurfaceAdapter`，独立 `legacySurface` 字段）；**不进** portable-core managed contract | 无 | 无 |
| EntitySelectors | query binding（factory/builder/query） | 共享树 binding（SERVER/TEST）→ catalog 观察面 `global:EntitySelectors`；**不进** managed contract | 无 | 无 |
| （替代面）Registry dataMap 查询 | managed portable query | `RegistryFacade`/`RegistryView.dataMapIds`/`dataMapValue` 已是 portable-core contract 反射符号；MC-facing 实现由 `NeoForgeRegistryQueryService`（平台 Adapter）持有 | 无 | 无 |

测试：`QueryToolContractClassificationTest`（5 用例）——观察面归类、managed contract 不含两查询全局而含 Registry 替代面、插件无事件 Contributor/钩子、SERVER/TEST 可见性、runtime member 锚点。

### 2.2 capability 三态（`CapabilityStatus`，真实探针派生）

测试：`QueryToolCapabilityMatrixTest`（loader 轴按运行时可加载性判定；golden 只读对比；fabric 节点选 fabric golden、neoforge 节点选 neoforge golden）。golden：
`src/test/resources/golden/query/capability-matrix-neoforge.txt`、`capability-matrix-fabric.txt`。

| 域 | neoforge 1.21.1 / 26.x | fabric 26.x | 依据 |
|---|---|---|---|
| DataMap binding（4 ScriptType） | `SUPPORTED`（SERVER/STARTUP/TEST 实注册探针；CLIENT 同一无条件路径，source trace——裸 JVM 无法初始化 client 绑定分支的 MC client 类） | `UNAVAILABLE`（`NekoJSCorePlugin`/`DataMapJS` 不存在；preflight 未定义标识符=显式失败，非静默 no-op） | 真实注册探针 + 类存在性探针 |
| DataMap portable 替代面（`Registry.get(...).dataMap*`） | `SUPPORTED`（`NeoForgeRegistryQueryService` 在位；contract 符号 `member:RegistryView.dataMap*`） | `UNAVAILABLE`（`IPlatform.registryQueryService()` 默认 `null` → `CoreManagedApiBootstrap` 回落 `EMPTY_REGISTRY_SERVICE` → **运行时静默空值/null**，已记录 deviation，owner loader-port） | 类存在性 + FabricPlatform 覆写探针 |
| EntitySelectors binding | `SUPPORTED` SERVER/TEST（实注册探针）；STARTUP/CLIENT `UNAVAILABLE`（契约性不注册：查询需 ServerLevel） | `SUPPORTED` SERVER/TEST（`FabricPluginLoader` 内置清单成员探针 + 实注册探针；**runtime query smoke 未在 fabric 上执行**，见 §7）；STARTUP/CLIENT `UNAVAILABLE` | 注册路径探针（两 loader 各自的真实收集路径） |
| EntitySelectors runtime 执行 | `SUPPORTED`（runServer `/nekojs test` fixture，§5） | not-verified（§7） | — |
| DataMap runtime 执行（命中/缺失/转换） | `SUPPORTED`（runServer `/nekojs test` fixture，§5） | `UNAVAILABLE`（binding 不注册） | — |

### 2.3 与 09 机制集成的摩擦（实现注记）

- **stonecutter active 节点直接编译原始共享文件**（`nekojs.neoforge-node.gradle.kts` 的 `stonecutterProcessed` 注释：「active 节点返回原文件」）。因此共享 `src/test/java` 中的测试文件必须在 26.1.2（active，neoforge）上按原文可编译——**fabric 专属测试不能整文件守卫放在共享测试树**（守卫在 active 节点不生效，javac 会直接编译它）。本票的处理：capability 测试单文件化，loader 轴与 loader 专属类全部走运行时反射探针（`Class.forName(name, false, cl)`，且**不初始化**——26.x 裸 JVM 初始化 `NeoForgeDataMaps`/注册表类会抛 `There is no current FML Loader`，与 `VanillaRegistryProbe` 同款事实）。
- golden 派生：capability 矩阵每行由探针派生后与只读 golden 对比（根测试树的既有 golden 纪律，`EventApiSurfaceGoldenTest` 模式）；无 regen 开关，变更须改探针+更新 golden+REPORT 留痕。
- **09 的 `REGENERATE.md` 在查询域用不上**：查询域的 declaration 基线来自根测试树的既有 fixture 机制（`BindingDeclarationGenerator` + 手写小 golden），不需要走 `:common:regenerateGoldens`；managed surface 的 golden 与查询域无交集（本票不改 `common/src/test/resources/nekojs/**` 与 `common/src/test/probe-ts/**`，`npm run test:probe-types` 原样通过）。这是「09 机制已消费但机制面不重叠」的实情，不是摩擦。

## 3. fixture 矩阵（Phase 3/4/5）

测试落点：`src/test/java/com/tkisor/nekojs/bindings/query/`（共享测试树，五节点按守卫/探针裁剪）；
golden：`src/test/resources/golden/query/`（只读）。行为级（注册表/数据包/ServerLevel 依赖）fixture
走 `runServer` + `/nekojs test`（§5），不在裸 JUnit 冒充（`VanillaRegistryProbe` 事实）。
脚本 fixture 与诊断脚本落点：`bench/query/fixtures/**`（`negative/` 只做负向证据，不进默认集）。

| # | fixture | 域 | 层级 | 载体 | 覆盖 |
|---|---|---|---|---|---|
| 1 | `QueryToolContractClassificationTest` | 两域 | source contract 归类 | 裸 JUnit（5 用例） | 观察面 `global:` kind + 占位签名；managed contract 不含 `global:DataMap`/`global:EntitySelectors`、含 `member:RegistryView.dataMap*`；插件无事件 owner；SERVER/TEST 可见性；runtime member 锚点 |
| 2 | `QueryToolCapabilityMatrixTest` | 两域 | capability 三态 | 裸 JUnit + golden（1 用例） | 真实注册探针/类存在性探针派生 `CapabilityStatus` 矩阵（neoforge + fabric 两套 golden）；fabric `DataMap` 显式 UNAVAILABLE；Registry 替代面 fabric 静默空值 deviation 记录 |
| 3 | `DataMapQueryBindingTest` | DataMap | 只读面 + 非法输入 + declaration | 裸 JUnit + golden（3 用例） | 公开方法集合恰为 {compostable, furnaceFuel}、Integer/Float 快照值、`RegistryView.dataMapValue` 返回 portable JSON 字符串；null 栈错误带域+入口无修复提示；生产 `BindingDeclarationGenerator` TS declaration 重复生成逐字节稳定 + golden |
| 4 | `EntitySelectorsQueryBindingTest` | EntitySelectors | 非法输入 + 声明面 | 裸 JUnit + golden（4 用例） | null level / null config 错误带域+入口；工厂预设按 runtime 反射锚定；TS declaration golden（实例 binding 渲染 `$EntitySelectorsJS` 类型标注） |
| 5 | `QueryToolDeclarationParityTest` | 两域 | declaration/Probe parity | 裸 JUnit（TempDir，2 用例） | 生产链路复刻（种子类 → `TypeReflector` IR → 真实 TS/Python backend）；重复生成整树逐字节一致；runtime public member 名单（反射派生）双向出现在 .d.ts/.pyi；phantom 负样本 |
| 6 | `bench/query/fixtures/test_scripts/datamap-query.js` | DataMap | 行为（命中/缺失/空值/类型转换/只读） | runServer `/nekojs test` | 15 断言全绿（§5.2） |
| 7 | `bench/query/fixtures/test_scripts/entityselectors-query.js` | EntitySelectors | 行为（factory/builder/query/作用域/距离/limit/order/非法输入） | runServer `/nekojs test` | 38 断言全绿（§5.3），期望集合按世界状态自校准 |
| 8 | `bench/query/fixtures/negative/entityselectors-illegal-location.js` | EntitySelectors | AC3 源位置负向探针 | runServer `/nekojs test`（预期失败） | 异常消息=域+入口（可捕获路径断言）；未捕获路径的三元组（§5.4） |
| 9 | `bench/query/diagnostics/q25-diag{,2,3,4,5}.js` | 两域 | 实读探针（诊断用，非验收 fixture） | runServer `/nekojs test` | 定位 entity-ticking 前置、`includesEntities` 门闸、`typeTag` 静默 no-op、`null` 适配语义（§5.1） |

裸 JUnit 里 builder 校验错误（limit/distance/gamemode/type/level）与 selector 执行被 `EntitySelector`
的 `<clinit>`（要求 FML Loader）阻断，全部收敛到 #7 游戏内断言。

## 4. AC1-AC10 逐条判定

| AC | 判定 | 证据指针 |
|---|---|---|
| 1. DataMap 命中返回既有平台快照；未命中/缺失按公开语义；不暴露可变 registry view | **满足** | `evidence/nekojs-test-green.log`：`hit: coal burnTime…`(1600)、`hit: apple compostable chance…`(0.65)、`miss: stone … -> null`×2、`readonly: …`×4；`DataMapQueryBindingTest.querySurfaceIsReadOnlyAndPortable`（public 方法集 {compostable, furnaceFuel}、返回 Integer/Float、`RegistryView.dataMapValue` 返回 `String`） |
| 2. EntitySelectors factory/builder/query 在 server/test side 生成并执行预期 selector，返回可验证实体结果 | **满足**（附世界状态前置，§5.3） | `nekojs-test-green.log`：`allEntities() + tag + find: hits every tagged entity`、`create(b => tag + type(cow)) + find: hits exactly the tagged cow`、`tag + type(pig, inverse) -> everything tagged except the pig`、`distanceAbove(8) hits the far tagged bee`、`limit(2) caps…`、`orderNearest().limit(1) returns exactly one`、`orderNearest result is a materialized entity` |
| 3. 非法 selector/level/缺失输入得到普通错误，含域+调用入口+源位置，不嵌修复提示 | **满足**（2 处 observation，§7） | `nekojs-test-green.log`：`create(null)/limit(0)/negative distance/min>max distance/…/unknown entity type/unknown entity type tag/unknown gamemode/null level/null selector -> labeled error`（11 条 `Test.assertThrows` 全过）；`evidence/nekojs-test-negative-probe.log`：`PASS error message carries domain + call entry` + 三元组 `环境: TEST / 位置: …illegal-location.js:29:17 / 原因: selector must not be null`；DataMap 侧 `invalid: number/plain object/unregistered item id -> error` |
| 4. 两域均无新增事件、无事件包装器、无无生命周期 Point | **满足** | `QueryToolContractClassificationTest`（插件不实现 `EventsPoint`/`ClientEventsPoint`；无 Point/事件包装器）；本票源码改动只加校验与作用域位，`git diff` 无任何事件注册 |
| 5. source contract 明确既有 tier 与能力，不因现有 binding 收录自动升级 managed stable | **满足** | `QueryToolContractClassificationTest`（观察面在 `legacySurface`，占位 void 签名；managed contract 不含 `global:DataMap`/`global:EntitySelectors`）+ §2.1 表 |
| 6. TS/Python declaration、Probe 输出与 runtime member/signature 一致 | **满足** | `QueryToolDeclarationParityTest`（2 用例：整树字节稳定 + runtime public member 双向出现 + phantom 负样本）；`DataMapQueryBindingTest`/`EntitySelectorsQueryBindingTest` 的 `BindingDeclarationGenerator` golden；`npm run test:probe-types` 通过（§5.5） |
| 7. NeoForge/Fabric/1.21.1 capability 按 source trace 与真实 smoke/fixture 记录 supported/partial/unavailable | **满足（neoforge 有 runtime 证据，fabric 只到 source trace）** | §2.2 表 + 两套 capability golden；neoforge runtime 由 §5.2/§5.3 支撑；fabric 无 runtime（§7 not-verified，owner loader-port） |
| 8. MC-facing 类型与平台差异由平台/版本 Adapter 持有；共享契约与查询 binding 不引入 MC/loader 依赖 | **满足（口径见下）** | 共享契约（`common`）零 MC/Loader import：`RegistryQueryService`/`RegistryView` 为纯 Java，MC-facing 实现在 `NeoForgeRegistryQueryService`（根 src，neoforge-only）；`DataMapJS` 整文件 neoforge 守卫；`EntitySelector` 的 26.1/26.2 包名与构造差异由 `EntitySelectorFactory` 反射桥接、1.21.1 走镜像文件；`Identifier` 改名由 stonecutter 统一。**口径**：`EntitySelectorsJS`/`EntitySelectorBuilderJS` 是 loader-tree binding（`src/`，直接 import MC），不是 common 契约——AC8 的「共享契约」指 `common` 的 portable contract，其零 MC 依赖由 guardLint 按包前缀强制 |
| 9. 每个域的替代查询面、旧路径消费者和删除条件可追踪；公开删除仍需维护者确认 | **满足（追踪表）** | §6 全表：current path → target owner → Adapter source trace → 替代覆盖 → 删除条件；消费者证据：仓库内除本票 fixture/diagnostics 外**无** `DataMap.`/`EntitySelectors.` 脚本调用者（`bench/` 之外 0 命中） |
| 10. 旧旁路只在替代 behavior/declaration/trace 通过且无调用者后移除；公开查询功能不删除，清理不推迟 final release | **未勾（维护者确认门禁）** | 本票**未删除**任何公开查询功能或旁路（§6 表末列「当前不满足，故不删」）；查询域内的旁路候选只有仓库级 legacy 观察面（属 managed-surface 工作，非本票路径）；替代 behavior/declaration/trace 已通过，消费者为 0，但按工单 Human input note 需维护者 sign-off 才允许删除 |

## 5. 游戏内验证（runServer，端口 25971 / RCON 25972）

节点：26.1.2（NeoForge，active）。证据目录：`docs/architecture-refactor/baseline/2026-09-12-query-tools/evidence/`。

### 5.1 实读发现与修复（Phase 1 收口时新增，commit 6）

三条都是**公开文档示例拿不到结果 / 静默 no-op**，不修则 AC1-AC3 无可验证证据。全部先取证、后改码。

| # | 发现（实读） | 证据 | 处置 |
|---|---|---|---|
| F1 | `DataMap` 以 **class binding** 注册时，脚本侧 `DataMap.furnaceFuel` 报 `Unknown identifier: furnaceFuel`（`typeof DataMap === 'function'`），即 ticket 25 前 `DataMap` 快捷查询在游戏内**完全不可用** | 发现由前一阶段会话的 `run/logs/nekojs/test.log` 取证（`typeof DataMap = function` / `DataMap.furnaceFuel typeof = undefined` / `Unknown identifier: furnaceFuel`），该 run 目录被 gitignore 且已被日志轮转覆盖——**未保留原始日志**，现存证据为：① 上一阶段留在代码里的同源注记（`NekoJSCorePlugin` 注册处注释「class binding（StaticClass）不暴露 instance 方法，运行时 DataMap.furnaceFuel 报 Unknown identifier（游戏内 fixture 实证）」）；② 修复后的正向证据 `runserver-25971-prefix-extract.log` 10:28:35 起 DataMap 命中/缺失/类型转换断言全绿；③ declaration golden 的 class→instance 形态 diff | `NekoJSCorePlugin` 改为 `registry.register("DataMap", new DataMapJS())`（instance binding；`DataMapJS` 无状态，单例实例即查询面）+ declaration golden 从 `typeof $DataMapJS` 改 `$DataMapJS` |
| F2 | `builder()`/`create(cfg)` 的 `includesEntities` 默认 `false`（= 只走玩家列表），而 `type(...)` 只在**玩家类型**上调整该位 → `type('minecraft:cow')` 过滤器恒不生效，`create(b => b.type('minecraft:cow'))`（`EntitySelectorsJS`/wiki 的文档示例）命中 **0**；`typeTag(...)` 同类 | `runserver-25971-prefix-extract.log`（修前，10:31:36）：`allEntities().limit(16).create() -> 16`、`allEntities().tag(q25f).limit(16).create() -> 4`，但 `builder().type(cow).limit(16).create() -> 0`、`create(b => b.type(cow).limit(16)) -> 0`（同一世界 45-67 实体、4 个带 tag）；修后 `runserver-25971-postfix-extract.log`（10:37:59）：`builder().type(cow)… -> 3`、`typeTag(beehive_inhabitors) -> 1` | 26.x 与 1.21.1 镜像同步修：**显式实体类型过滤切作用域**——`type(非玩家类型)`、`type(..., inverse=true)`、`typeTag(...)` 置 `includesEntities=true`；正选玩家类型保持 `false`。修后 `create(b => b.type(cow)) -> 3`、`typeTag(beehive_inhabitors) -> 1` |
| F3 | `resolveEntityTypeTag` 对未知 tag 只构造空 `TagKey` → 过滤恒假、查询恒空（silent no-op，spec 04 禁止），与 `type(...)`（未知实体类型直接报错）不一致 | `runserver-25971-postfix-extract.log`（10:37:59，校验补丁前）：`builder().typeTag('nekojs:no_such_tag') -> com.tkisor.nekojs.util.selector.EntitySelectorBuilderJS@6b86e929`（无异常）；`typeTag('minecraft:flowers') -> 0`（无此实体类型 tag，也无异常） | `resolveEntityTypeTag` 增加「标签已在当前数据包声明」校验（26.x `getTags()` / 1.21.1 `getTagNames()`，stonecutter 守卫），未声明抛 `EntitySelectors.typeTag: unknown entity type tag: …`；修后 fixture 的 `assertThrows` 通过（§5.3） |
| F4（不改码，只记录语义） | JS `null`/`undefined` 经 `ItemStackAdapter.apply` 映射为 `ItemStack.EMPTY`（`test()` 显式接受 `isNull`），因此 `DataMap.furnaceFuel(null)` 走「缺失」返回 `null` 而**不是**错误；Java 侧 null 守卫面向直接 Java 调用者 | `runserver-25971-prefix-extract.log` 10:30:29 段（`furnaceFuel(null) -> null`、`furnaceFuel(undefined) -> null`、`furnaceFuel(ItemStack.EMPTY) -> null`）、`nekojs-test-green.log`（`empty: JS null -> ItemStack.EMPTY -> null`） | 保留 Java 守卫（单元层断言其域+入口）；fixture 按**实读语义**钉死（`assertThrows` 只用于真正会抛的输入） |

`includesEntities` 是 vanilla 的既有门闸（`EntitySelector.findEntities` 在 `includesEntities=false` 时走玩家列表）：实读 `EntitySelectorParser`（`/tmp/nf26` 反编译源）该字段**无初值**，由 `@e/@r`→true、`@a/@p`→false 显式设置。builder 无前缀，作用域由基座预设定：`builder()`/`create(cfg)`=玩家集合，`allEntities()`/`nearestEntity()`/`randomEntity()`=实体集合。因此纯计分板 tag/team/体积框过滤**不**切作用域（两者都合法），沿用基座——fixture 对此有显式 pin（§5.3）。

### 5.2 DataMap fixture（`datamap-query.js`，15 断言）

```
[10:44:01] [PASS] hit: coal burnTime from the vanilla furnace_fuels data map
[10:44:01] [PASS] hit: apple compostable chance is a snapshot number in (0,1], got: 0.6499999761581421
[10:44:01] [PASS] miss: stone is not fuel -> null            (×2 non-fuel / non-compostable)
[10:44:01] [PASS] coercion: string item id -> ItemStack -> same burnTime
[10:44:01] [PASS] invalid: number is not an ItemStack -> error
[10:44:01] [PASS] invalid: plain object is not an ItemStack -> error
[10:44:01] [PASS] invalid: unregistered item id -> error
[10:44:01] [PASS] empty: JS null -> ItemStack.EMPTY -> null   (null / undefined / null)
[10:44:01] [PASS] readonly: furnaceFuel|compostable is a query function
[10:44:01] [PASS] readonly: repeated query … same snapshot     (×2)
[10:44:01] [INFO] [NekoJS Test][SUMMARY] 15 passed, 0 failed
```

未注册 id 的错误全文（`DataMap.furnaceFuel('minecraft:not_an_item')`）：
`[NekoJS] Cannot convert Identifier to net.minecraft.world.item.ItemStack (expected registered item id): Item not found: minecraft:not_an_item`（`ValueConversionException`，含入口与目标类型，无修复提示）。
数值/对象的错误全文带入口与目标类型：`invokeMember (furnaceFuel) on com.tkisor.nekojs.bindings.static_access.DataMapJS failed due to: Cannot convert '42'… to Java type 'net.minecraft.world.item.ItemStack': Unsupported target type.`

### 5.3 EntitySelectors fixture（`entityselectors-query.js`，38 断言）

**世界状态前置（必须先做，否则查询恒空）**——`/nekojs test` 在服务器线程同步跑完，期间世界不 tick；无玩家的专用服务器也不会 entity-ticking 载入区块，未 entity-ticking 区块里召唤的实体会停在 `pendingEntities`：

```bash
# 1) 让锚点区块 entity-ticking（无玩家时必须有）
python bench/query/rcon.py 25972 <pw> 60 "forceload add -16 -16 16 16"
# 2) 锚点附近召唤带 tag 的实体（NoAI 固定位置，避免游荡改变距离断言）
python bench/query/rcon.py 25972 <pw> 60 \
  'summon minecraft:cow 0 -60 0 {Tags:["q25f","q25f_c1"],NoAI:1b}' \
  'summon minecraft:cow 3 -60 0 {Tags:["q25f","q25f_c2"],NoAI:1b}' \
  'summon minecraft:cow 6 -60 0 {Tags:["q25f","q25f_c3"],NoAI:1b}' \
  'summon minecraft:pig 1.5 -60 1.5 {Tags:["q25f","q25f_p1"],NoAI:1b}' \
  'summon minecraft:bee 8 -60 8 {Tags:["q25bee"],NoAI:1b}'
# 3) 再跑测试（召唤发生在更早的 tick，实体已刷入区块）
python bench/query/rcon.py 25972 <pw> 90 "nekojs test"
```

排查链（`bench/query/diagnostics/q25-diag2.js`、`q25-diag4.js`）：`hasChunkAt=false`、`entityTicking=false`、`getAllEntities()=0` → `forceload` 后 `allEntities=45`，但**同一 tick 内**召唤的实体仍不可见；`/nekojs test` 无法自建实体（它阻塞服务器线程），故前置放 RCON。

```
[10:44:01] [PASS] prerequisite: forceload + tagged summon done first (q25f=4 cows=3 q25f_c1=1 q25f_p1=1)
[10:44:01] [PASS] allEntities() + tag + find: hits every tagged entity
[10:44:01] [PASS] create(b => tag + type(cow)) + find: hits exactly the tagged cow
[10:44:01] [PASS] builder() + explicit type(cow) is entity-scoped and excludes the tagged pig
[10:44:01] [PASS] builder() + type(cow) + tag narrows to the single tagged cow
[10:44:01] [PASS] scope: a tag-only builder() stays on the player base (0 players online)
[10:44:01] [PASS] scope: allPlayers() preset stays player-only -> empty snapshot
[10:44:01] [PASS] type(cow) on create()/builder() sees non-player entities (includesEntities fix)
[10:44:01] [PASS] tag + type(cow) -> the tagged cows
[10:44:01] [PASS] tag + type(pig) -> the tagged pig
[10:44:01] [PASS] tag + type(pig, inverse) -> everything tagged except the pig
[10:44:01] [PASS] tag + inverse type(cow) excludes the cow -> 0
[10:44:01] [PASS] type(player) stays player-scoped -> 0 players online
[10:44:01] [PASS] typeTag(entity_type tag) hits the tagged bee
[10:44:01] [PASS] typeTag alone switches to the entity base (includesEntities fix)
[10:44:01] [PASS] distanceBelow(8) keeps the tagged set near the anchor
[10:44:01] [PASS] distanceAbove(8) misses the near tagged set
[10:44:01] [PASS] distanceAbove(8) hits the far tagged bee
[10:44:01] [PASS] distanceBelow(8) misses the far tagged bee
[10:44:01] [PASS] limit(2) caps the tagged set
[10:44:01] [PASS] orderNearest().limit(1) returns exactly one
[10:44:01] [PASS] orderNearest result is a materialized entity
[10:44:01] [PASS] create(null)|limit(0)|distance(-1,5)|distance(5,1)|distanceAbove(-1)|level(5,1)
                        |type(nekojs:no_such_type)|typeTag(nekojs:no_such_tag)|gamemode(hacker)
                        |find(null,sel,0,0,0)|find(level,null,0,0,0) -> labeled error   (11 条)
[10:44:01] [PASS] repeated query is side-effect free
[10:44:01] [INFO] [NekoJS Test][SUMMARY] 53 passed, 0 failed      (两脚本累计)
```

RCON 回执：`NekoJS test scripts completed. - no errors.`（`evidence/rcon-25972-nekojs-test-green.log`），完整日志 `evidence/nekojs-test-green.log`。
期望集合按世界状态自校准（脚本现算 tag/类型计数），重跑不漂移；`NoAI` 保证距离断言稳定。

### 5.4 AC3 源位置负向探针（`negative/entityselectors-illegal-location.js`，预期失败）

```
[PASS] error message carries domain + call entry                ← 消息 == "EntitySelectors.find: selector must not be null"
[ERROR] 脚本执行失败: nekojs:test/entityselectors-illegal-location.js
环境: TEST
位置: test_scripts/entityselectors-illegal-location.js:29:17
原因: selector must not be null

EntitySelectors.find(level, null, 0, 0, 0)
```

即：域+入口在异常消息里，源位置由统一错误管线补（`ScriptError.getConciseDetailText`）。两处 observation 见 §7（concise 截断、行号 +2）。

### 5.5 验证命令与结果（Phase 7）

| 命令 | 结果 | 证据 |
|---|---|---|
| `./gradlew :common:check :26.1.2:check`（含 15 个查询域用例） | `BUILD SUCCESSFUL`；`bindings.query` 五类 15 用例 0 失败（DataMap 3 / EntitySelectors 4 / CapabilityMatrix 1 / Classification 5 / Parity 2） | `evidence/gradle-check-common-26.1.2.log`、`evidence/gradle-check-after-fix.log`；`versions/26.1.2/build/test-results/test/TEST-com.tkisor.nekojs.bindings.query.*.xml` |
| `./gradlew :1.21.1:compileJava`（改了 1.21.1 镜像） | `BUILD SUCCESSFUL` | `evidence/gradle-check-after-fix.log` |
| `npm run test:probe-types` | 通过（`tsc --noEmit` 无输出） | 无声明基线变更，无需 regenerate（§2.3） |
| `./gradlew :26.1.2:runServer` + `/nekojs test` | `no errors`（§5.2-§5.4） | `evidence/runserver-25971-*-extract.log`、`evidence/rcon-25972-*.log`、`evidence/nekojs-test-green.log` |

证据文件说明：`runserver-25971-prefix-extract.log` = 修复前会话（10:27 启动）控制台日志去掉 DEBUG 行；`runserver-25971-postfix-extract.log` = F2/F3 修复后会话（10:37 启动）；`runserver-25971-green-extract.log` = 干净取证会话（10:42 启动）；原始 4.4MB 级 DEBUG 日志未入库（可按 §5.3 配方重放）。

**未跑**：`runGameTestServer`（本域行为需要真实 ServerLevel + 数据包，`/nekojs test` 已覆盖；GameTest 无额外信号）、fabric 节点 runtime（§7）。

## 6. capability/source trace/删除条件表（Phase 6）

口径提醒：**「当前入口存在」≠ managed stable**——两域的 `global:` 观察面在 `legacySurface`（占位 void 签名），
`managedApis`/portable-core contract 里**没有**它们；只有 DataMap 的 *替代面* `RegistryView.dataMap*` 是 managed。

| 域 | current path（观察到的入口） | target owner（该能力的目标归属） | Adapter source trace | 替代覆盖（behavior/declaration/trace 通过情况） | 旧路径删除条件 | 当前处置 |
|---|---|---|---|---|---|---|
| DataMap 快捷查询 | `NekoJSCorePlugin.registerBinding` → `registry.register("DataMap", new DataMapJS())`（instance binding，全 ScriptType；`DataMapJS` 整文件 `//? if neoforge`） | **Registry Runtime 的 portable 查询面**：`Registry.get(id).dataMapIds()/dataMapValue(type,id)`（`global:Registry` + `member:RegistryView.dataMap*`，managed contract 符号） | `RegistryQueryService`（common SPI，零 MC import）← `NeoForgeRegistryQueryService`（根 src，neoforge-only，data map 与 JSON 序列化在此）← `NeoForgePlatform.registryQueryService()`；fabric：`IPlatform` 默认 `null` → `EMPTY_REGISTRY_SERVICE` | **behavior**：`datamap-query.js` 15/15（neoforge）；**declaration**：`DataMapQueryBindingTest` golden + `QueryToolDeclarationParityTest`；**trace**：`QueryToolCapabilityMatrixTest` 两套 golden。替代面**未覆盖**快捷值语义（`furnaceFuel` 只是 `FURNACE_FUELS` 的便捷读取；等价查询=`registry(id).dataMapValue('neoforge:furnace_fuels', 'minecraft:coal')` 需脚本自行解析 JSON） | ① 替代面能表达 `furnaceFuel`/`compostable` 的等价语义且返回值语义可接受（当前是 JSON 字符串，需评测脚本可用性）；② 两域的 behavior/declaration/trace 全绿（**已满足**）；③ 仓库内外**无调用者**（**已满足**：`bench/` 之外 0 命中）；④ **维护者 sign-off** | **不删**（按工单 Human input note 与 AC10）。`DataMap` 是 wiki/公开 API 的一部分（`CoreBindingsGlobalsTest` 把它列为 SERVER 绑定集成员），删除需维护者确认 |
| DataMap portable 替代面（fabric） | `Registry.get(...).dataMap*` 在 fabric 上返回空/null | 同上（fabric 侧由 loader-port 补齐） | `IPlatform.registryQueryService()` 默认 `null`；`FabricPlatform` **未覆写**（其 javadoc 自陈「默认方法保持未实现语义，脚本侧会拿到空结果」） | 无 fabric behavior（§7 not-verified） | 补 `FabricPlatform` 实现或让能力矩阵把该项从 `UNAVAILABLE` 改判（须先改行为） | **deviation 记录**，owner = loader-port/W6；本票不实现 fabric data map 体系（NeoForge data map 无 fabric 对应物） |
| EntitySelectors factory/builder/query | `EntitySelectorsPlugin`（SERVER/TEST）→ `registry.register("EntitySelectors", new EntitySelectorsJS())`；fabric 经 `FabricPluginLoader.BUILTIN_PLUGINS` | **该 binding 自身即目标 owner**（无 managed 替代面：它是本域唯一的公开查询面） | 共享树 `EntitySelectorsJS`/`EntitySelectorBuilderJS`（loader 树，直接 import MC）；版本差异由 `EntitySelectorFactory`（26.1/26.2 `MinMaxBounds` 包名 + 13 参构造反射）与 `versions/1.21.1/**` 镜像承载 | **behavior**：`entityselectors-query.js` 38/38（neoforge，§5.3）；**declaration**：`EntitySelectorsQueryBindingTest` golden + `QueryToolDeclarationParityTest`；**trace**：capability golden 两 loader | **无可删路径**：本域没有旧生成物/影子观察面，binding 就是公开功能（AC10「公开查询功能不删除」）。若将来要重命名/内聚，前置=behavior+declaration+trace 全绿（**已满足**）+ 无调用者（**已满足**）+ 维护者 sign-off | **保留**（不删） |
| 仓库级 legacy 观察面（`legacySurface` → legacy probe declaration） | `NekoScriptCatalogSnapshot.legacySurface`（`LegacySurfaceAdapter` 观察 kind） | managed surface 工作（09） | `LegacySurfaceAdapter` + `common/src/test/resources/nekojs/probe/legacy-*.expected.d.ts` | 覆盖**全部** binding（非查询域专属） | 属 09 的收缩 gate，**不是本票路径**；本票只提供「查询域无遗留旁路」的结论 | **不动**（owner=managed-surface） |

消费者追踪证据（`git grep` 全仓，`bench/` 之外）：

```
DataMapJS / "DataMap"        → 仅 NekoJSCorePlugin 注册 + 测试断言（CoreBindingsGlobalsTest、本票测试）
EntitySelectors*            → 仅 EntitySelectorsPlugin 注册 + FabricPluginLoader 清单 + 本票测试
DataMap. / EntitySelectors. 脚本调用（.js/.ts/.py/.json/.snbt）→ 仓库内除 bench/query/** 外 0 命中
文档引用                     → wiki/全局绑定.md（EntitySelectors 章节；DataMap 无章节）
```

## 7. not-verified + owner

| # | 未验证项 | 原因 | owner |
|---|---|---|---|
| N1 | fabric 节点 EntitySelectors runtime query smoke（生成 + 执行 + 命中） | 本票只跑 neoforge 26.1.2 专用服务器；fabric 侧 capability 只到「注册路径/类存在性」source trace。CI 有 `fabric-runtime-smoke` job（`versions/26.1.2-fabric/src/test/resources/fabric-runtime-smoke/**`）可挂 fixture | loader-port / 维护者排 CI 时 |
| N2 | fabric `Registry.get(...).dataMap*` 静默空值 | NeoForge data map 无 fabric 对应物；`FabricPlatform` 未覆写 `registryQueryService()` → 回落 `EMPTY_REGISTRY_SERVICE`（显式 deviation，非本票改 fabric 运行时行为） | loader-port / W6 |
| N3 | 1.21.1 / 26.2.0 节点的 runtime fixture | 本票只在 26.1.2 跑 `/nekojs test`；1.21.1 镜像的同步改动只到 `compileJava` 层；26.2.0 未跑 | 本票 owner（后续 smoke 排期）/ 维护者 |
| N4 | `EntitySelector` 未覆盖的过滤面（scores / advancements / NBT / loot predicate，类 javadoc 的 v1 后续项） | 既有缺口，非本票范围；fixture 只覆盖已公开成员 | 后续票 |
| N5 | wiki 与代码矛盾（`wiki/全局绑定.md`：EntitySelectors「仅 NeoForge」，但 Fabric 内置清单注册了它且类无守卫） | `wiki/` 非本票写集 | 文档 owner |
| N6 | 查询域内「全部 ScriptType 的 CLIENT 行」只在 source trace 层判定（DataMap CLIENT=SUPPORTED 无 runtime 探针） | 裸 JVM 无法初始化 client 分支的 MC client 类（同 `VanillaRegistryProbe` 事实） | 本票 owner（已在 golden evidence 列申明） |

**observation（非 not-verified，但应留痕）**：

- O1 `ScriptError.getConciseDetailText`（`conciseScriptErrorLogs=true` 默认）把 `原因` 取消息里**最后一个** `": "` 之后的部分，于是 `EntitySelectors.find: selector must not be null` 在日志里只显示 `selector must not be null`——域+入口在**异常消息**里完整存在（fixture 的 `e.message` 断言证），只是 concise 显示被截断。要看全文需 `conciseScriptErrorLogs=false`。这是全域显示策略，非查询域特有；AC3 按「错误携带域+入口+源位置」判定满足。
- O2 统一错误管线报出的**行号比真实行号大 2、列号正确**：`negative/entityselectors-illegal-location.js` 的真实非法调用在第 27 行（列 17 = `EntitySelectors` 的 `E`），管线报 `29:17` 且 snippet 内容取自第 27 行。复现：把该文件放进 `run/nekojs/test_scripts/` 跑 `/nekojs test`。疑似脚本受理前的行偏移（ESM 受理/包装），需 diagnostics 侧确认；非查询域特有。

## 8. 偏离与问题

1. **修了 3 处行为（F1/F2/F3），不是纯 fixture 票**：都属「既有公开入口拿不到结果 / 静默 no-op」，属 spec 04 明令禁止的形态，且不修则 AC1-AC3 无证据。F1 只改注册形态（class→instance，签名/声明面随 golden 对齐）；F2/F3 只影响「显式类型过滤」与「未知 type tag」两条路径，玩家的既有查询语义（`allPlayers()`/`gamemode`/`level`/玩家类型）逐条 pin 住未变（§5.3）。1.21.1 镜像已同步（头注释要求），并过 `compileJava`。
2. **与 09 机制集成的摩擦**：查询域的 declaration/golden 基线在**根测试树**（`BindingDeclarationGenerator` + 手写小 golden），与 09 的 managed-surface golden（`common/src/test/resources/nekojs/**`、`probe-ts`）不重叠——本票按 09 的「只读 golden + 显式变更留痕」纪律执行（两处 golden 变更已在 §5.1 与下方记录），但**没有**走 `:common:regenerateGoldens`（机制不适用）。若维护者希望根测试树的查询 golden 也纳入 09 的 regenerate 流程，需要在 09 侧扩展机制——本票不改 09 领地。
3. **golden 变更留痕（09 REGENERATE §3 格式）**：
   - `src/test/resources/golden/query/datamap-binding.d.txt`：`let DataMap: typeof $DataMapJS;` → `let DataMap: $DataMapJS;`。**原因**：F1 把 DataMap 从 class binding 改为 instance binding（F1 的证据链见 §5.1）。**影响**：declaration 文本形态变化，符号名/成员集合不变；对脚本作者无破坏（`DataMap.furnaceFuel(...)` 调用形态一致，且修前该调用**根本不可用**）。**审阅**：本票 owner 自查，维护者在验收时复核。
   - `capability-matrix-fabric.txt` 首行注释：`QueryToolCapabilityMatrixFabricTest` → `QueryToolCapabilityMatrixTest`（陈旧类名；fabric/neoforge golden 由同一个测试按运行时 loader 选择）。**原因**：注释与实际守护测试不一致。**影响**：仅注释。
   - `neoforge` capability golden 未变；`entityselectors-binding.d.txt` 未变（方法签名未动）。
4. **`/nekojs test` 无法自建实体的硬约束**（§5.3）：世界状态前置必须由 RCON 预先完成。这使本票的 entityselector fixture **不是单命令自包含**的；已在脚本头写入前置配方并要求脚本自校准期望值。若后续希望单命令可跑，需要 `/nekojs test` 支持「tick 后断言」（测试运行器目前 `synchronized` + `Thread.sleep` 阻塞服务器线程，`flushTestTimers` 只刷 Node 定时器不推进世界 tick）。
5. **测试计数是累计值**：`TestJS` 的 passed/failed 计数在 TEST 环境内跨 `/nekojs test` 调用累计（同一 binding 实例），SUMMARY 会包含历史运行；证据以**逐条 PASS/ERROR 行**为准（§5.2/§5.3 的 clean run 是服务器重启后的首次运行，SUMMARY 干净）。
6. `bench/query/diagnostics/**` 保留为实读探针（定位 N2/F2/F3/entity-ticking 前置）；它们是诊断脚本、恒真断言，不进默认 fixture 集。
