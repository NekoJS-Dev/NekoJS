# Ticket 25 实施报告：DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径

> 工单：`docs/architecture-refactor/implementation-tickets/25-query-tools.md`（已认领 in-progress，Assignee=zcode-agent）。
> 权威 spec：`docs/architecture-refactor/specs/04-public-contract-and-plugin-model.md`（contract/tier/capability 口径）、
> `08-ported-features-event-surface.md`（EntitySelectors 保持 factory/query binding，不事件化）、`07-validation-and-migration.md`。
> 依赖消费：09 号票交付的 managed surface 机制（`NormativeApiContract`、`CapabilityStatus` 三态、`LegacySurfaceAdapter`
> 观察面、deterministic fixture 模式、`REGENERATE.md` 纪律）。
> 基线 commit：`98478559`（本票认领提交）。工作分支 `ticket-25-query-tools`。
>
> **本报告已按 2026-09-12 双轴代码审查裁定修订**（见 §0 第 9 项提交、§5.7 回退说明、§8）：
> F1 保留（AC1 的必要前提）；F2/F3 **回退**（越权改公开选择器/错误语义）；AC2/AC7 判定据实；
> 补 F1 的可复现对照证据；追查并解释 `postfix-extract` 里 `2 failed`；`bench/query` 补齐
> README + 一键运行器。

## 0. Commit 清单

| # | Commit | 内容 |
|---|---|---|
| 1 | `4d2bc744` docs(baseline): ticket 25 query tools inventory | Phase 1 现状盘点（§1） |
| 2 | `ef938958` test(api): ticket 25 query binding contract classification + capability matrix | Phase 2 source contract 归类 + capability 三态（§2） |
| 3 | `62fceb99` feat+test(query): ticket 25 DataMap query binding fixture | Phase 3（§3 #3） |
| 4 | `c2492524` feat+test(query): ticket 25 EntitySelectors query binding fixture | Phase 4（§3 #4） |
| 5 | `a9ba2010` test(query): ticket 25 TS/Python declaration parity via shared probe IR | Phase 5（§3 #5） |
| 6 | `bac13881` fix(query): ticket 25 让查询 binding 真正可命中 | Phase 6 源码改动（**F2/F3 已在第 9 项回退**，F1 保留） |
| 7 | `cf8d24ef` test(query): ticket 25 fixture 对齐游戏内实读语义 | fixture 重写 + declaration golden 对齐 + capability golden 注释纠错 |
| 8 | `e14bda1f` docs(baseline): ticket 25 capability/trace/删除条件 + AC 判定 + runServer 证据 | 本报告 §1-§8 + `evidence/` |
| 9 | 本提交（`ticket-25-query-tools` 分支 HEAD，subject = `fix(query): ticket 25 审查整改——回退 F2/F3 越权语义变更、补对照证据、bench/query 运行器`；这一行**故意不写 hash**，因为它描述的正是包含本报告的这次提交） | 审查整改（§5.6-§5.9、§7、§8） |

写作顺序：先 inventory/归类/fixture 骨架（1-5），再用 `runServer` 实读把「脚本可见语义」钉死（6-7），
再收口（8），最后按双轴审查裁定做**回退与据实修订**（9）。

## 1. 现状盘点（Phase 1，不改代码）

### 1.1 DataMap 域

| 观察物 | 现状 |
|---|---|
| binding 类 | `src/main/java/com/tkisor/nekojs/bindings/static_access/DataMapJS.java`（整文件 `//? if neoforge` 守卫，1.21.1/26.x NeoForge 共享同一文件；无版本内守卫） |
| runtime member | `furnaceFuel(ItemStack) -> Integer`、`compostable(ItemStack) -> Float`；命中返回值，未命中返回 `null`（javadoc 明示 `??` 兜底语义） |
| 注册入口 | `NekoJSCorePlugin.registerBinding`（:164 起）`registry.register("DataMap", ...)`——**票前是 class binding**（`DataMapJS.class`），全部 ScriptType 可见；`NekoJSCorePlugin` 整文件 neoforge 守卫 |
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
| 作用域现状 | `includesEntities` 由**基座**预设：`builder()`/`create(cfg)` = 玩家集合（等价 `@a`），`allEntities()`/`nearestEntity()`/`randomEntity()` = 实体集合；`type(...)`/`typeTag(...)` 只在玩家类型上调整该位（**本票回退后保持这一现状**，见 §5.7） |

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
- **推论（本票 characterization 的落点）**：`type(...)`/`typeTag(...)`/`allPlayers()` 都要碰 `BuiltInRegistries.ENTITY_TYPE`，所以「类型过滤是否切作用域」只能在游戏内钉；裸 JUnit 只能钉**不依赖注册表的基座作用域位**（`builder()`/`allEntities()`/`nearestEntity()`/`randomEntity()`），见 §3 #4。

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

**口径（审查后收紧，写进两套 golden 头注释）**：`SUPPORTED` 行必须有**真实探针**（真实注册进真实 registry / 真实类可加载性）；只有 source trace、未经运行时探测的行一律记 `PARTIAL` 并在 evidence 写明「仅 source trace、未实测」。禁止把「同一无条件路径」当作已实测能力。

| 域 | neoforge 1.21.1 / 26.x | fabric 26.x | 依据 |
|---|---|---|---|
| DataMap binding（4 ScriptType） | SERVER/STARTUP/TEST `SUPPORTED`（实注册探针）；**CLIENT `PARTIAL`**（仅 source trace，裸 JVM 无法初始化 client 绑定分支的 MC client 类，且无 client runtime smoke —— 本 commit 从硬编码 `SUPPORTED` 改判） | `UNAVAILABLE`（`NekoJSCorePlugin`/`DataMapJS` 不存在；preflight 未定义标识符=显式失败，非静默 no-op） | 真实注册探针 + 类存在性探针 |
| DataMap portable 替代面（`Registry.get(...).dataMap*`） | `SUPPORTED`（`NeoForgeRegistryQueryService` 在位；contract 符号 `member:RegistryView.dataMap*`） | `UNAVAILABLE`（`IPlatform.registryQueryService()` 默认 `null` → `CoreManagedApiBootstrap` 回落 `EMPTY_REGISTRY_SERVICE` → **运行时静默空值/null**，已记录 deviation，owner loader-port） | 类存在性 + FabricPlatform 覆写探针 |
| EntitySelectors binding | `SUPPORTED` SERVER/TEST（实注册探针）；STARTUP/CLIENT `UNAVAILABLE`（契约性不注册：查询需 ServerLevel） | `SUPPORTED` SERVER/TEST（`FabricPluginLoader` 内置清单成员探针 + 实注册探针）；STARTUP/CLIENT `UNAVAILABLE` | 注册路径探针（两 loader 各自的真实收集路径） |
| EntitySelectors runtime 执行 | `SUPPORTED`（runServer `/nekojs test` fixture，§5） | not-verified（§7 N1；`FabricPluginLoader` 注册面已由 `:26.1.2-fabric:test` 实跑证明，但**真实服务器执行选择器**未在 fabric 跑） | — |
| DataMap runtime 执行（命中/缺失/转换） | `SUPPORTED`（runServer `/nekojs test` fixture，§5） | `UNAVAILABLE`（binding 不注册） | — |

执行记录（审查后补）：neoforge 侧 `:26.1.2:test`，fabric 侧 **`:26.1.2-fabric:test` 实跑**（`evidence/gradle-node-tests-review.log`）——fabric 分支的 `QueryToolCapabilityMatrixTest`（1 用例，按 fabric golden 派生）、`QueryToolContractClassificationTest`（5）、`QueryToolDeclarationParityTest`（2）、`EntitySelectorsQueryBindingTest`（6）全部 0 失败；`DataMapQueryBindingTest` 在 fabric 节点不存在（整文件 `//? if neoforge` 守卫），与 capability 行一致。

### 2.3 与 09 机制集成的摩擦（实现注记）

- **stonecutter active 节点直接编译原始共享文件**（`nekojs.neoforge-node.gradle.kts` 的 `stonecutterProcessed` 注释：「active 节点返回原文件」）。因此共享 `src/test/java` 中的测试文件必须在 26.1.2（active，neoforge）上按原文可编译——**fabric 专属测试不能整文件守卫放在共享测试树**（守卫在 active 节点不生效，javac 会直接编译它）。本票的处理：capability 测试单文件化，loader 轴与 loader 专属类全部走运行时反射探针（`Class.forName(name, false, cl)`，且**不初始化**——26.x 裸 JVM 初始化 `NeoForgeDataMaps`/注册表类会抛 `There is no current FML Loader`，与 `VanillaRegistryProbe` 同款事实）。
- golden 派生：capability 矩阵每行由探针派生后与只读 golden 对比（根测试树的既有 golden 纪律，`EventApiSurfaceGoldenTest` 模式）；无 regen 开关，变更须改探针+更新 golden+REPORT 留痕。
- **09 的 `REGENERATE.md` 在查询域用不上**：查询域的 declaration 基线来自根测试树的既有 fixture 机制（`BindingDeclarationGenerator` + 手写小 golden），不需要走 `:common:regenerateGoldens`；managed surface 的 golden 与查询域无交集（本票不改 `common/src/test/resources/nekojs/**` 与 `common/src/test/probe-ts/**`，`npm run test:probe-types` 原样通过）。这是「09 机制已消费但机制面不重叠」的实情，不是摩擦。**但本域基线确实不在 09 的 §1 清单里**——登记建议与 owner 见 §7 N9。

## 3. fixture 矩阵（Phase 3/4/5）

测试落点：`src/test/java/com/tkisor/nekojs/bindings/query/`（共享测试树，五节点按守卫/探针裁剪）；
golden：`src/test/resources/golden/query/`（只读）。行为级（注册表/数据包/ServerLevel 依赖）fixture
走 `runServer` + `/nekojs test`（§5），不在裸 JUnit 冒充（`VanillaRegistryProbe` 事实）。
脚本 fixture 与诊断脚本落点：`bench/query/fixtures/**`（`negative/` 只做负向证据，`diagnostics/` 只做实读探针，均不进默认集）。
**复现入口**：`bench/query/README.md` + `bench/query/run-query.ps1`（单命令 `all` 跑完 deploy→启动→前置→测试→判定→停服，端口 25971/RCON 25972）。

| # | fixture | 域 | 层级 | 载体 | 覆盖 |
|---|---|---|---|---|---|
| 1 | `QueryToolContractClassificationTest` | 两域 | source contract 归类 | 裸 JUnit（5 用例） | 观察面 `global:` kind + 占位签名；managed contract 不含 `global:DataMap`/`global:EntitySelectors`、含 `member:RegistryView.dataMap*`；插件无事件 owner；SERVER/TEST 可见性；runtime member 锚点 |
| 2 | `QueryToolCapabilityMatrixTest` | 两域 | capability 三态 | 裸 JUnit + golden（1 用例） | 真实注册探针/类存在性探针派生 `CapabilityStatus` 矩阵（neoforge + fabric 两套 golden，**两套都已实跑**）；fabric `DataMap` 显式 UNAVAILABLE；Registry 替代面 fabric 静默空值 deviation 记录；DataMap CLIENT `PARTIAL`（仅 source trace） |
| 3 | `DataMapQueryBindingTest` | DataMap | 只读面 + 非法输入 + declaration | 裸 JUnit + golden（3 用例） | 公开方法集合恰为 {compostable, furnaceFuel}、Integer/Float 快照值、`RegistryView.dataMapValue` 返回 portable JSON 字符串；null 栈错误带域+入口无修复提示（**Java 调用者路径**，脚本侧不可达）；生产 `BindingDeclarationGenerator` TS declaration 重复生成逐字节稳定 + golden |
| 4 | `EntitySelectorsQueryBindingTest` | EntitySelectors | 非法输入 + 声明面 + **characterization** | 裸 JUnit + golden（7 用例） | null level / null config 错误带域+入口；工厂预设按 runtime 反射锚定（declaringClass=代码本类 + 返回 builder + 零参 + public，取代原恒真断言）；TS declaration golden；**`scopeBitIsOwnedByBasePresetsOnly`**（作用域位只由基座预设决定）；**`unknownEntityTypeTagIsSilentlyAccepted`**（未知 tag 不报错、仍 materialize 成 TagKey）；**`typeTagNullErrorCarriesDomainAndEntry`**（null tag → 带域+入口的 IAE，不是裸 NPE） |
| 5 | `QueryToolDeclarationParityTest` | 两域 | declaration/Probe parity | 裸 JUnit（TempDir，2 用例） | 生产链路复刻（种子类 → `TypeReflector` IR → 真实 TS/Python backend）；重复生成整树逐字节一致；runtime public member 名单（反射派生）双向出现在 .d.ts/.pyi；phantom 负样本 |
| 6 | `bench/query/fixtures/test_scripts/datamap-query.js` | DataMap | 行为（命中/缺失/空值/类型转换/只读） | runServer `/nekojs test` | 15 断言全绿（§5.2） |
| 7 | `bench/query/fixtures/test_scripts/entityselectors-query.js` | EntitySelectors | 行为（factory/builder/query/作用域/距离/limit/order/非法输入）+ **回退后语义的 characterization** | runServer `/nekojs test` | 44 断言全绿（§5.3），期望集合按世界状态自校准 |
| 8 | `bench/query/fixtures/negative/entityselectors-illegal-location.js` | EntitySelectors | AC3 源位置负向探针 | runServer `/nekojs test`（预期失败，`-IncludeNegative` 部署） | 异常消息=域+入口（可捕获路径断言）；未捕获路径的三元组（§5.4） |
| 9 | `bench/query/diagnostics/q25-diag{,2,3,4,5}.js` | 两域 | 实读探针（诊断用，非验收 fixture） | runServer `/nekojs test`（`-IncludeDiagnostics` 部署） | 定位 entity-ticking 前置、`includesEntities` 门闸、`typeTag` 静默 no-op、`null` 适配语义（§5.1） |
| 10 | `bench/query/diagnostics/q25-datamap-binding-control.js` | DataMap | **F1 的 A/B 对照探针** | runServer `/nekojs test`（`-IncludeDiagnostics` 部署） | class binding vs instance binding 的 `typeof DataMap` / `typeof DataMap.furnaceFuel` / 调用结果（§5.6） |

裸 JUnit 里 builder 校验错误（limit/distance/gamemode/type/level）与 selector 执行被注册表/`EntitySelector` 的类初始化链阻断，全部收敛到 #7 游戏内断言。

## 4. AC1-AC10 逐条判定

| AC | 判定 | 证据指针 |
|---|---|---|
| 1. DataMap 命中返回既有平台快照；未命中/缺失按公开语义；不暴露可变 registry view | **满足** | `evidence/runserver-25971-review-green-extract.log`：`hit: coal burnTime…`(1600)、`hit: apple compostable chance…`(0.65)、`miss: stone … -> null`×2、`empty: JS null -> ItemStack.EMPTY -> null`×3、`readonly: …`×4；`DataMapQueryBindingTest.querySurfaceIsReadOnlyAndPortable`（public 方法集 {compostable, furnaceFuel}、返回 Integer/Float、`RegistryView.dataMapValue` 返回 `String`）；F1 的「修前不可用」由 §5.6 的 A/B 对照复现 |
| 2. EntitySelectors factory/builder/query 在 server/test side 生成并执行预期 selector，返回可验证实体结果 | **满足（附口径，见下）** | `runserver-25971-review-green-extract.log`：factory 预设 `allEntities()` + tag/type/inverse/typeTag + `find` 命中精确集合；**factory 入口 `create(cfg)`** 经 `type('minecraft:player', true)`（既有语义：反选玩家=实体集合）执行并命中 46 个非玩家实体，且与等价 preset+builder 构造的 selector 结果**逐值相等**；`nearestEntity()`/`randomEntity()` 各返回恰好 1 个已物化实体；`orderNearest`/`limit`/`distance*` 各自有断言。**口径**：`create(cfg)`/`builder()` 的基座是玩家集合，正选非玩家类型**不切作用域**（回退后现状），所以文档示例风格 `create(b => b.type('minecraft:cow'))` 命中 0——这是 AC 之外、已记录的缺口（§7 N7）。**若维护者把 AC2 读作「文档示例必须命中实体」，则本 AC 为部分满足** |
| 3. 非法 selector/level/缺失输入得到普通错误，含域+调用入口+源位置，不嵌修复提示 | **满足**（2 处 observation，§7 O1/O2） | `runserver-25971-review-green-extract.log`：`create(null)/limit(0)/negative distance/min>max distance/…/unknown entity type/unknown gamemode/null level/null selector -> labeled error`（10 条 `Test.assertThrows` 全过）；`runserver-25971-review-negative-extract.log`：`PASS error message carries domain + call entry` + 三元组 `环境: TEST / 位置: …illegal-location.js:29:17 / 原因: selector must not be null`；DataMap 侧 `invalid: number/plain object/unregistered item id -> error` |
| 4. 两域均无新增事件、无事件包装器、无无生命周期 Point | **满足** | `QueryToolContractClassificationTest`（插件不实现 `EventsPoint`/`ClientEventsPoint`；无 Point/事件包装器）；本票源码改动只加校验、错误消息域前缀与作用域 characterization，`git diff` 无任何事件注册 |
| 5. source contract 明确既有 tier 与能力，不因现有 binding 收录自动升级 managed stable | **满足** | `QueryToolContractClassificationTest`（观察面在 `legacySurface`，占位 void 签名；managed contract 不含 `global:DataMap`/`global:EntitySelectors`）+ §2.1 表 |
| 6. TS/Python declaration、Probe 输出与 runtime member/signature 一致 | **满足** | `QueryToolDeclarationParityTest`（2 用例：整树字节稳定 + runtime public member 双向出现 + phantom 负样本）；`DataMapQueryBindingTest`/`EntitySelectorsQueryBindingTest` 的 `BindingDeclarationGenerator` golden；`npm run test:probe-types` 通过（§5.5） |
| 7. NeoForge/Fabric/1.21.1 capability 按 source trace 与真实 smoke/fixture 记录 supported/partial/unavailable | **部分满足** | neoforge 侧有 runtime 证据（§5.2/§5.3）；**fabric 侧本 commit 补齐 `:26.1.2-fabric:test` 实跑**（capability golden fabric 分支 + query 域 4 类 15 用例，`evidence/gradle-node-tests-review.log`），但 fabric 的 **runtime query smoke**（真实服务器执行选择器并命中）仍未跑 → 该项不能记 SUPPORTED，owner loader-port/维护者（§7 N1）。DataMap binding CLIENT 行已从硬编码 SUPPORTED 改判 `PARTIAL`（仅 source trace） |
| 8. MC-facing 类型与平台差异由平台/版本 Adapter 持有；共享契约与查询 binding 不引入 MC/loader 依赖 | **满足（口径见下）** | 共享契约（`common`）零 MC/Loader import：`RegistryQueryService`/`RegistryView` 为纯 Java，MC-facing 实现在 `NeoForgeRegistryQueryService`（根 src，neoforge-only）；`DataMapJS` 整文件 neoforge 守卫；`EntitySelector` 的 26.1/26.2 包名与构造差异由 `EntitySelectorFactory` 反射桥接、1.21.1 走镜像文件；`Identifier` 改名由 stonecutter 统一。**口径**：`EntitySelectorsJS`/`EntitySelectorBuilderJS` 是 loader-tree binding（`src/`，直接 import MC），不是 common 契约——AC8 的「共享契约」指 `common` 的 portable contract，其零 MC 依赖由 guardLint 按包前缀强制 |
| 9. 每个域的替代查询面、旧路径消费者和删除条件可追踪；公开删除仍需维护者确认 | **满足（追踪表）** | §6 全表：current path → target owner → Adapter source trace → 替代覆盖 → 删除条件；消费者证据：仓库内除本票 fixture/diagnostics 外**无** `DataMap.`/`EntitySelectors.` 脚本调用者（`bench/` 之外 0 命中） |
| 10. 旧旁路只在替代 behavior/declaration/trace 通过且无调用者后移除；公开查询功能不删除，清理不推迟 final release | **未勾（维护者确认门禁）** | 本票**未删除**任何公开查询功能或旁路（§6 表末列「当前不满足，故不删」）；查询域内的旁路候选只有仓库级 legacy 观察面（属 managed-surface 工作，非本票路径）；替代 behavior/declaration/trace 已通过，消费者为 0，但按工单 Human input note 需维护者 sign-off 才允许删除 |

## 5. 游戏内验证（runServer，端口 25971 / RCON 25972）

节点：26.1.2（NeoForge，active）。证据目录：`docs/architecture-refactor/baseline/2026-09-12-query-tools/evidence/`。
本 commit 的会话一律经 `bench/query/run-query.ps1` 一键跑（§5.10），证据 `evidence/bench-query-runner-review.log`。

### 5.1 实读发现与处置（Phase 1 收口时新增，commit 6；**双轴审查后按裁定修正**）

| # | 发现（实读） | 证据 | 处置 |
|---|---|---|---|
| F1 | `DataMap` 以 **class binding** 注册时，脚本侧 `DataMap.furnaceFuel` 报 `Unknown identifier: furnaceFuel`（`typeof DataMap === 'function'`），即 ticket 25 前 `DataMap` 快捷查询在游戏内**完全不可用** | **可复现 A/B 对照**（§5.6，`evidence/datamap-binding-control-{class,instance}-binding.log`）：class binding → `typeof DataMap.furnaceFuel = undefined` + `Unknown identifier: furnaceFuel`；instance binding → `typeof DataMap.furnaceFuel = function` + 返回 1600 | **保留**（审查裁定 F1 是 AC1 的必要前提，且只改注册形态：`NekoJSCorePlugin` → `registry.register("DataMap", new DataMapJS())`；`DataMapJS` 无状态，单例实例即查询面）+ declaration golden 从 `typeof $DataMapJS` 改 `$DataMapJS`。类比重：票 01 为产出基线而修 fabric 构建 |
| F2 | `builder()`/`create(cfg)` 的 `includesEntities` 默认 `false`（= 只走玩家列表），而 `type(...)` 只在**玩家类型**上调整该位 → `type('minecraft:cow')` 过滤器恒不生效，`create(b => b.type('minecraft:cow'))`（`EntitySelectorsJS`/wiki 的文档示例）命中 **0**；`typeTag(...)` 同类 | `runserver-25971-prefix-extract.log`（修前，10:31:36）：`allEntities().tag(q25f)… -> 4`，但 `builder().type(cow)… -> 0`、`create(b => b.type(cow)…) -> 0` | **回退（审查裁定：越权改公开语义）**。曾改为「显式实体类型过滤切作用域」（非玩家正选/任意反选/typeTag 置 `includesEntities=true`）——这把选择器的**作用域语义**从「只查玩家列表」改成「查全部实体」，而工单只授权「建立 fixture」，AC2 又存在不改语义的取证路径。26.x 与 1.21.1 镜像同步回退成 `if (isPlayerType(resolved)) { … }` 原逻辑、`typeTag` 不再动该位；**保留**所有错误消息域前缀改动（`EntitySelectors.distance: …` 等，AC3 合规改进）。现状写成 characterization + 缺口（§7 N7） |
| F3 | `resolveEntityTypeTag` 对未知 tag 只构造空 `TagKey` → 过滤恒假、查询恒空（silent no-op，spec 04 禁止），与 `type(...)`（未知实体类型直接报错）不一致 | `runserver-25971-postfix-extract.log`（10:37:59，校验补丁前）：`builder().typeTag('nekojs:no_such_tag') -> EntitySelectorBuilderJS@6b86e929`（无异常） | **回退（审查裁定：越权改公开语义）**。曾增加「tag 已在当前数据包声明」校验、未声明抛 `EntitySelectors.typeTag: unknown entity type tag: …`——这是**公开语义变更**（静默空结果 → 报错），超出 fixture 授权。回退为「静默构造 `TagKey`」原行为、删除仅此处使用的 `entityTypeTagDeclared` helper（它与 `NeoForgeCatalogPlatformProvider.tagIds` 的 stonecutter 分支重复）。现状写成 characterization（裸 JUnit + 游戏内各一条）+ 缺口（§7 N8） |
| F4（不改码，只记录语义） | JS `null`/`undefined` 经 `ItemStackAdapter.apply` 映射为 `ItemStack.EMPTY`（`test()` 显式接受 `isNull`），因此 `DataMap.furnaceFuel(null)` 走「缺失」返回 `null` 而**不是**错误；Java 侧 null 守卫面向直接 Java 调用者 | `runserver-25971-prefix-extract.log` 10:30:29 段（`furnaceFuel(null) -> null`、`furnaceFuel(undefined) -> null`） | 保留 Java 守卫但**如实标注可达性**：`DataMapJS` javadoc 与 `itemHolder` 注释写明「脚本侧不可达、只为 Java 调用方挡裸 NPE」；fixture 按**实读语义**钉死（`assertThrows` 只用于真正会抛的输入） |

### 5.2 DataMap fixture（`datamap-query.js`，15 断言，全绿）

```
[12:19:59] [INFO] [NekoJS Test][PASS] hit: coal burnTime from the vanilla furnace_fuels data map
[12:19:59] [INFO] [NekoJS Test][PASS] hit: apple compostable chance is a snapshot number in (0,1], got: 0.6499999761581421
[12:19:59] [INFO] [NekoJS Test][PASS] miss: stone is not fuel -> null
[12:19:59] [INFO] [NekoJS Test][PASS] miss: stone is not compostable -> null
[12:19:59] [INFO] [NekoJS Test][PASS] coercion: string item id -> ItemStack -> same burnTime
[12:19:59] [INFO] [NekoJS Test][PASS] invalid: number is not an ItemStack -> error
[12:19:59] [INFO] [NekoJS Test][PASS] invalid: plain object is not an ItemStack -> error
[12:19:59] [INFO] [NekoJS Test][PASS] invalid: unregistered item id -> error
[12:19:59] [INFO] [NekoJS Test][PASS] empty: JS null -> ItemStack.EMPTY -> null
[12:19:59] [INFO] [NekoJS Test][PASS] empty: undefined -> ItemStack.EMPTY -> null
[12:19:59] [INFO] [NekoJS Test][PASS] empty: JS null -> ItemStack.EMPTY -> null
[12:19:59] [INFO] [NekoJS Test][PASS] readonly: furnaceFuel is a query function
[12:19:59] [INFO] [NekoJS Test][PASS] readonly: compostable is a query function
[12:19:59] [INFO] [NekoJS Test][PASS] readonly: repeated query of the same entry returns the same snapshot
[12:19:59] [INFO] [NekoJS Test][PASS] readonly: repeated query of the same entry returns the same snapshot (compostable)
[12:19:59] [INFO] [NekoJS Test][SUMMARY] 15 passed, 0 failed
```

未注册 id 的错误全文（`DataMap.furnaceFuel('minecraft:not_an_item')`）：
`[NekoJS] Cannot convert Identifier to net.minecraft.world.item.ItemStack (expected registered item id): Item not found: minecraft:not_an_item`（`ValueConversionException`，含入口与目标类型，无修复提示）。
数值/对象的错误全文带入口与目标类型：`invokeMember (furnaceFuel) on com.tkisor.nekojs.bindings.static_access.DataMapJS failed due to: Cannot convert '42'… to Java type 'net.minecraft.world.item.ItemStack': Unsupported target type.`

### 5.3 EntitySelectors fixture（`entityselectors-query.js`，44 断言，全绿）

**世界状态前置（由运行器 `setup` 步骤完成，不可省）**——`/nekojs test` 在服务器线程同步跑完，期间世界不 tick；无玩家的专用服务器也不会 entity-ticking 载入区块，未 entity-ticking 区块里召唤的实体会停在 `pendingEntities`：

```bash
python bench/query/rcon.py 25972 ticket25 60 "forceload add -16 -16 16 16"
# 相对坐标 ~ = 服务器 command source 原点（fixture 的 anchor 同源），故与种子/世界类型无关
python bench/query/rcon.py 25972 ticket25 60 \
  'summon minecraft:cow ~ ~ ~ {Tags:["q25f","q25f_c1"],NoAI:1b,NoGravity:1b}' \
  'summon minecraft:cow ~3 ~ ~ {Tags:["q25f","q25f_c2"],NoAI:1b,NoGravity:1b}' \
  'summon minecraft:cow ~6 ~ ~ {Tags:["q25f","q25f_c3"],NoAI:1b,NoGravity:1b}' \
  'summon minecraft:pig ~1.5 ~ ~1.5 {Tags:["q25f","q25f_p1"],NoAI:1b,NoGravity:1b}' \
  'summon minecraft:bee ~8 ~ ~8 {Tags:["q25bee"],NoAI:1b,NoGravity:1b}'
```

断言分组（`evidence/runserver-25971-review-green-extract.log`，本次会话 q25f=28 cows=21 q25f_p1=7 q25bee=7，脚本现算）：

```
[PASS] dedicated server is running / overworld ServerLevel obtained
[PASS] prerequisite: forceload + tagged summon done first (q25f=28 cows=21 q25f_c1=7 q25f_p1=7)
[PASS] prerequisite: tagged bee present (q25bee=7)
---- AC2(a) 实体基座（预设）+ builder + query 真实命中 ----
[PASS] allEntities() + tag + find: hits every tagged entity (factory preset + builder + query)
[PASS] allEntities() + tag + type(cow): hits exactly the tagged cows
[PASS] allEntities() + tag + type(pig): hits the tagged pig
[PASS] allEntities() + inverse type(pig): everything tagged except the pig
[PASS] allEntities() + typeTag(declared entity type tag): hits the tagged bee
---- AC2(b) create(cfg) / builder() 的实体命中（既有语义路径，未改语义）----
[PASS] create(cfg) + type(player, inverse) runs the selector and hits every non-player entity, got: 46 (tagged=28)
[PASS] create(cfg) and builder() build the same selector shape -> identical results
[PASS] builder() + inverse type(player) + tag: the tagged entities (builder entry hits real entities)
[PASS] nearestEntity() preset returns exactly one materialized entity
[PASS] randomEntity() preset returns exactly one materialized entity
[PASS] orderNearest result is a materialized entity
---- AC2(c) 玩家基座语义（无玩家时为空，语义正确）----
[PASS] scope: a tag-only builder() stays on the player base (0 players online) -> empty
[PASS] scope: create(cfg) base is the player scope (0 players online) -> empty
[PASS] scope: allPlayers() preset stays player-only -> empty snapshot
[PASS] scope: type(player) stays player-scoped -> 0 players online
---- characterization：非玩家类型过滤不切作用域（回退后现状 = 缺口 N7）----
[PASS] GAP: create(b => b.type(cow)) does NOT switch scope -> 0 (documented-example style, see REPORT §7)
[PASS] GAP: builder().type(cow) keeps the player base -> 0
[PASS] GAP: inverse of a non-player type also keeps the player base -> 0
[PASS] GAP: typeTag() alone keeps the player base -> 0 (entity base needed for hits)
---- characterization：未知 type tag 静默接受（缺口 N8）----
[PASS] GAP: unknown entity type tag is silently accepted (no error, silent no-op, see REPORT §7)
[PASS] GAP: an unknown tag keeps the player base -> 0 (indistinguishable from a real empty filter)
[PASS] GAP: an unknown tag on the entity base filters everything out -> 0 (silent, no error)
---- 距离 / limit / 顺序 ----
[PASS] distanceBelow(8) keeps the tagged set near the anchor
[PASS] distanceAbove(8) misses the near tagged set
[PASS] distanceAbove(8) hits the far tagged bee
[PASS] distanceBelow(8) misses the far tagged bee
[PASS] limit(2) caps the tagged set
[PASS] orderNearest().limit(1) returns exactly one
---- 非法输入（10 条）----
[PASS] create(null)…/limit(0)…/negative distance…/min>max distance…/negative distanceBelow…/min>max level…
       /unknown entity type -> labeled error (contrast with the silent unknown tag above)/unknown gamemode…/null level…/null selector -> labeled error
[PASS] repeated query is side-effect free
[INFO] [NekoJS Test][SUMMARY] 58 passed, 0 failed      （本会话累计：DataMap 15 + EntitySelectors 43）
```

RCON 回执：`NekoJS test scripts completed. - no errors.`（`evidence/rcon-25971-nekojs-test-review-green.log`）。
`assertThrows` 只用于真的会抛的输入；`typeTag('nekojs:no_such_tag')` 已从「预期报错」改判为 characterization（不抛 + 恒空）。

### 5.4 AC3 源位置负向探针（`negative/entityselectors-illegal-location.js`，预期失败）

```
[PASS] error message carries domain + call entry                ← 消息 == "EntitySelectors.find: selector must not be null"
[ERROR] 脚本执行失败: nekojs:test/entityselectors-illegal-location.js
环境: TEST
位置: test_scripts/entityselectors-illegal-location.js:29:17
原因: selector must not be null

EntitySelectors.find(level, null, 0, 0, 0)
堆栈: at anonymous (test_scripts/entityselectors-illegal-location.js:29)
```

即：域+入口在异常消息里，源位置由统一错误管线补（`ScriptError.getConciseDetailText`）。两处 observation 见 §7（concise 截断、行号 +2）。
本 commit 用运行器 `-IncludeNegative` 重跑复现（`evidence/runserver-25971-review-negative-extract.log`；RCON 回执 `NekoJS test scripts completed. (1 error(s) remain)`，`evidence/rcon-25971-nekojs-test-review-negative.log`），且 `verify` 对这次运行**按预期失败**
（`VERIFY-FAILED: 1 script-level failure(s)`）——负面证据与判定不是「人眼看着像失败」，而是运行器能识别的失败。

### 5.5 验证命令与结果（Phase 7）

| 命令 | 结果 | 证据 |
|---|---|---|
| `./gradlew :common:check :26.1.2:check :26.1.2-fabric:test :1.21.1:compileJava --console=plain` | `BUILD SUCCESSFUL`；`common` tests=1403 skipped=4 failures=0 errors=0；`26.1.2` tests=173 skipped=34 failures=0；**`26.1.2-fabric` tests=73 skipped=6 failures=0**（两节点 test 任务另用 `--rerun-tasks` 真跑取证） | `evidence/gradle-check-review-common-26.1.2-fabric-1.21.1.log`、`evidence/gradle-node-tests-review.log` |
| 查询域用例计数（本 commit 前后） | `bindings.query` 五类 **15 → 18** 用例 0 失败：DataMap 3（不变）/ EntitySelectors **4 → 7**（新增两条 characterization + null tag 守卫）/ Capability 1 / Classification 5 / Parity 2。neoforge 侧 18、fabric 侧 15（`DataMapQueryBindingTest` 仅 neoforge 存在） | `versions/{26.1.2,26.1.2-fabric}/build/test-results/test/TEST-com.tkisor.nekojs.bindings.query.*.xml` |
| `./gradlew :1.21.1:compileJava`（改了 1.21.1 镜像：F2 回退同步） | `BUILD SUCCESSFUL` | `evidence/gradle-check-review-*.log` |
| `npm run test:probe-types` | 通过（`tsc --noEmit` 无输出） | 无声明基线变更（§2.3） |
| `powershell -File bench/query/run-query.ps1 all -Session q25final` | `QUERY-HARNESS PASSED (summary=2, pass=58, fail=0)` + RCON `no errors`；干净会话（restart 后首次运行，SUMMARY 不跨会话累计） | `evidence/bench-query-runner-review.log`、`evidence/runserver-25971-review-green-extract.log` |

**未跑**：`runGameTestServer`（本域行为需要真实 ServerLevel + 数据包，`/nekojs test` 已覆盖；GameTest 无额外信号）、fabric runtime query smoke（§7 N1）。

### 5.6 F1 的可复现对照记录（原「修前不可用」只有作者自述，属循环证据）

审查指出：F1 的修前证据来自上一阶段会话的 `run/logs/nekojs/test.log`，该 run 目录被 gitignore 且已被日志轮转覆盖，
现存只有「代码里的同源注记」——即作者自述 → 循环证据。本 commit 用**同一个 worktree、同一条探针脚本**补了对照：

`bench/query/diagnostics/q25-datamap-binding-control.js`（`-IncludeDiagnostics` 部署），两次只差 `NekoJSCorePlugin` 的注册行：

| 组 | NekoJSCorePlugin 注册行 | 游戏内输出（探针原文） |
|---|---|---|
| 对照（class binding） | `registry.register("DataMap", DataMapJS.class)` | `typeof DataMap = function` / `typeof DataMap.furnaceFuel = undefined` / `DataMap.furnaceFuel(coal) THREW: invokeMember (furnaceFuel) on com.tkisor.nekojs.bindings.static_access.DataMapJS failed due to: Unknown identifier: furnaceFuel`；且默认 fixture `datamap-query.js` 整脚本执行失败（`原因: furnaceFuel`），RCON `(5 error(s) remain)` |
| 实验（instance binding，本票方案） | `registry.register("DataMap", new DataMapJS())` | `typeof DataMap = object` / `typeof DataMap.furnaceFuel = function` / `DataMap.furnaceFuel(coal) = 1600`；默认 fixture 全绿，RCON `(4 error(s) remain)`（余 4 条全部来自 diagnostics 脚本的既有探针失败） |

归档：`evidence/datamap-binding-control-class-binding.log`、`evidence/datamap-binding-control-instance-binding.log`。
复现配方写在 `bench/query/README.md`（对照组探针小节）。**证据局限**：两次会话共用一个累加了实体的世界，
但探针与断言都是自校准/纯查询，不受影响；且本对照只证明「class binding 下脚本侧拿不到 instance 方法」，
不覆盖其他注册形态。

### 5.7 F2/F3 回退后的语义与取证口径（本次审查的核心裁定）

**回退后的现状（= 本票之前的行为，逐条钉住）**：

| 语义 | 值 | 钉在哪 |
|---|---|---|
| `builder()` / `create(cfg)` 基座 | 玩家集合（`includesEntities=false`，等价 `@a`） | 裸 JUnit `scopeBitIsOwnedByBasePresetsOnly` + 游戏内 `scope:` 断言组 |
| `allEntities()` / `nearestEntity()` / `randomEntity()` 基座 | 实体集合（`includesEntities=true`） | 同上 |
| `type('minecraft:cow')`（非玩家正选） | **不切作用域** → 玩家基座上命中 0 | 游戏内 `GAP:` 断言组 |
| `type(非玩家, inverse)` | **不切作用域** | 同上 |
| `typeTag(...)` | **不切作用域**（已知 tag 在基座集合上正常过滤，未知 tag 静默恒假） | 同上 |
| `type('minecraft:player', inverse)` | 切到实体集合（**原实现即如此**，因为 `isPlayerType(player)` 为真） | 游戏内 `create(cfg) + type(player, inverse)` 断言——AC2 的取证就建立在它上面 |
| 未知实体类型 tag | 不报错；materialize 成空 `TagKey`，过滤恒假 | 裸 JUnit `unknownEntityTypeTagIsSilentlyAccepted` + 游戏内 `GAP:` 三条 |
| 未知实体类型（`type`） | 报 `EntitySelectors.type: unknown entity type: …` | 游戏内非法输入组 |
| `typeTag(null)` | 报 `EntitySelectors.typeTag: tag must not be null`（**保留**的入参校验，见下） | 裸 JUnit `typeTagNullErrorCarriesDomainAndEntry` |

**为什么回退**（引用工单与项目纪律）：
1. 工单授权是「为 EntitySelectors builder/query 建立 fixture」+「非法 selector/level 错误 fixture」，
   **没有**授权修改选择器的公开作用域语义，也没有授权把「静默空结果」改成「报错」；
2. 项目纪律：公开语义变更先回决策（维护者裁决），agent 不在窄票里单方面改公开行为；
3. AC2 存在**不改语义**的取证路径（上表 `type('minecraft:player', inverse)` 与实体基座预设），
   所以 F2/F3 并非 AC 的必要前提（与 F1 不同：F1 不改则 `DataMap.furnaceFuel` 在脚本侧根本不存在，AC1 无从取证）。

**保留的部分**：所有错误消息的**域+入口前缀**（`EntitySelectors.distance: …`、`limit must be at least 1` → `EntitySelectors.limit: …` 等）、
`find` 的 `level/selector` 校验，以及 `resolveEntityTypeTag` **仅针对 `null` 入参**的守卫——审查明确要求保留 AC3「错误含域+调用入口」的合规改进，
`null` 本来就是错误路径（原来让 `Identifier.parse(null)` 抛裸 NPE），保留它不改变任何既有可观察语义，只把错误类型与可读性对齐 AC3；
「未声明 tag」的校验才是被回退的语义变更（静默空结果 → 报错）。这一保留项由
`EntitySelectorsQueryBindingTest.typeTagNullErrorCarriesDomainAndEntry` 守护；若维护者认为连 `null` 守卫也应回退，改动是一行删除 + 删该用例。

### 5.8 fabric capability 执行记录（AC7 补证）

`./gradlew :26.1.2-fabric:test` 实跑通过（`evidence/gradle-node-tests-review.log`，fabric tests=73 failures=0；neoforge tests=173 failures=0），其中查询域：

```
EntitySelectorsQueryBindingTest         tests=7  failures=0
QueryToolCapabilityMatrixTest           tests=1  failures=0   ← 按 fabric golden 派生（VineFabricPluginLoader 清单 + 实注册探针）
QueryToolContractClassificationTest     tests=5  failures=0
QueryToolDeclarationParityTest          tests=2  failures=0
```

即 `capability-matrix-fabric.txt` 的每一行**不再只是 source trace，而是 fabric 节点实跑派生**（该 golden 头注释已同步写明）。
仍未闭合的是 fabric 的 **runtime** 面（真实服务器上生成+执行 selector 并命中）——owner loader-port（§7 N1），AC7 因此记「部分满足」。

### 5.9 未解释失败的追查：`runserver-25971-postfix-extract.log` 的 `2 failed`

`postfix-extract` 里 `SUMMARY` 有 `2 failed` 而摘录中看不到 `FAIL`/`ERROR` 行——本 commit 追查结论如下（**不留未解释样本**）。

1. **SUMMARY 是会话内累计值**，所以一行 SUMMARY 不能单独说明某次调用。该会话的 SUMMARY 依次是
   `19 passed, 0 failed`（diag5）→ `50 passed, 2 failed`（diag 组）→ `91 passed, 2 failed`（diag3）→ `110 passed, 2 failed`（diag4）。
   2 个失败在**第一次出现后一直累计**，不是每次调用都新增失败。
2. **这 2 个失败是真实断言失败，不是累计假象**，且全部在**当时的中间版 fixture**（部署在 `test_scripts/_hold/`）里：
   - `_hold/datamap-query.js:33` —— `Expected function to throw`：`Test.assertThrows(() => DataMap.furnaceFuel(null), …)` 没有抛。
     真因是 F4 的实读语义（JS `null` → `ItemStack.EMPTY` → 走「缺失」返回 `null`）。fixture 期望写错，**不是产品缺陷**。
   - `_hold/entityselectors-query.js:41` —— `Expected 1 but got 0`：`sizeOf(b => b.tag('nekojs_q25_c1').limit(16))` 期望 1。
     真因是「纯计分板 tag 过滤沿用基座」——`builder()`/`create(cfg)` 基座是玩家集合，无玩家时 0。fixture 期望写错。
3. 两者都在随后的 `cf8d24ef`（fixture 对齐游戏内实读语义）里被修掉：`_hold/` 中间版被现版 fixture 取代，
   断言改成按自校准集合与真实返回语义。干净会话 `runserver-25971-green-extract.log`（10:42 启动）与本次
   `runserver-25971-review-green-extract.log`（12:19 启动）里**每个 SUMMARY 都是 `0 failed`**，且逐条无 `FAIL` 行。
4. 顺带查清两类**不计入** `failed` 计数器的输出（容易误读成「失败被藏起来」）：
   - `[ERROR] 脚本执行失败: …`（脚本级未捕获异常 / preflight 拒绝）是独立错误块，**不**进 `TestJS` 的 passed/failed 计数；
   - `Script binding-preflight … exception` 同理。`postfix-extract` 里同时存在这两类（诊断脚本刻意触发），
     但计数器只记 `Test.assertEquals/assertTrue/assertThrows` 的失败——所以「2 failed」与「看到几处 ERROR」数量不等。
5. **运行器侧的对应加固**（本 commit）：`bench/query/run-query.ps1` 的 `verify` 不只查 SUMMARY，
   还要求逐条 `[FAIL]` 为零、脚本级失败（`脚本执行失败` 或 ASCII 兜底 `nekojs:test/<path>.js` / `Script binding-preflight`）为零、
   preflight 拒绝（`Unknown identifier`）为零。自审时发现两个**让检查静默失效**的实现缺陷并已修复：
   ① 初版失败判定只匹配中文 marker，而 stdout 的非 ASCII 会被 JVM/gradle 管道吞掉 → 改成以 mod 自己写的
   `run/logs/nekojs/test.log`（平台 ANSI，中文完整）为主源 + ASCII 兜底；
   ② `Select-String` 默认大小写不敏感，`BUILD ` 命中了断言消息里的 "build the same selector shape"，
   把一条 PASS 行重复抽进 extract → 加 `-CaseSensitive` 并收紧成 `BUILD SUCCESSFUL|BUILD FAILED`。

### 5.10 bench/query 一键复现（本 commit 新增）

`bench/query/` 原先只有 fixtures/diagnostics + 本报告里的手工配方，按 `bench/{perf,datafix,smoke}` 的既有约定补齐
`README.md` + `run-query.ps1`（固定端口 25971/RCON 25972、RCON 停服、停服后等 `world/session.lock`、
UTF-8 BOM、`fixtures/nekojs/**` 布局、命令与停服一律 RCON、`robocopy /E` 部署、删净重试）：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File bench/query/run-query.ps1 all -Session q1
# deploy → start → wait-done → setup（forceload+summon 前置）→ test（RCON `nekojs test`）
# → extract（run/logs/nekojs/test.log + 控制台 ASCII marker → out/<Session>-extract.log）
# → verify（SUMMARY 全 0 failed + 无 [FAIL] + 无脚本级失败 + 无 preflight 拒绝）→ stop
```

单命令等价完成了原报告 §5.2/§5.3/§5.4 的全部手工步骤；负向探针与诊断脚本用 `-IncludeNegative` / `-IncludeDiagnostics` 部署，
**不进默认集**。证据：`evidence/bench-query-runner-review.log`。

## 6. capability/source trace/删除条件表（Phase 6）

口径提醒：**「当前入口存在」≠ managed stable**——两域的 `global:` 观察面在 `legacySurface`（占位 void 签名），
`managedApis`/portable-core contract 里**没有**它们；只有 DataMap 的 *替代面* `RegistryView.dataMap*` 是 managed。

| 域 | current path（观察到的入口） | target owner（该能力的目标归属） | Adapter source trace | 替代覆盖（behavior/declaration/trace 通过情况） | 旧路径删除条件 | 当前处置 |
|---|---|---|---|---|---|---|
| DataMap 快捷查询 | `NekoJSCorePlugin.registerBinding` → `registry.register("DataMap", new DataMapJS())`（instance binding，全 ScriptType；`DataMapJS` 整文件 `//? if neoforge`） | **Registry Runtime 的 portable 查询面**：`Registry.get(id).dataMapIds()/dataMapValue(type,id)`（`global:Registry` + `member:RegistryView.dataMap*`，managed contract 符号） | `RegistryQueryService`（common SPI，零 MC import）← `NeoForgeRegistryQueryService`（根 src，neoforge-only，data map 与 JSON 序列化在此）← `NeoForgePlatform.registryQueryService()`；fabric：`IPlatform` 默认 `null` → `EMPTY_REGISTRY_SERVICE` | **behavior**：`datamap-query.js` 15/15（neoforge）；**declaration**：`DataMapQueryBindingTest` golden + `QueryToolDeclarationParityTest`；**trace**：`QueryToolCapabilityMatrixTest` 两套 golden（neoforge CLIENT 行为 PARTIAL）。替代面**未覆盖**快捷值语义（`furnaceFuel` 只是 `FURNACE_FUELS` 的便捷读取；等价查询=`registry(id).dataMapValue('neoforge:furnace_fuels', 'minecraft:coal')` 需脚本自行解析 JSON） | ① 替代面能表达 `furnaceFuel`/`compostable` 的等价语义且返回值语义可接受（当前是 JSON 字符串，需评测脚本可用性）；② 两域的 behavior/declaration/trace 全绿（**已满足**）；③ 仓库内外**无调用者**（**已满足**：`bench/` 之外 0 命中）；④ **维护者 sign-off** | **不删**（按工单 Human input note 与 AC10）。`DataMap` 是 wiki/公开 API 的一部分（`CoreBindingsGlobalsTest` 把它列为 SERVER 绑定集成员），删除需维护者确认 |
| DataMap portable 替代面（fabric） | `Registry.get(...).dataMap*` 在 fabric 上返回空/null | 同上（fabric 侧由 loader-port 补齐） | `IPlatform.registryQueryService()` 默认 `null`；`FabricPlatform` **未覆写**（其 javadoc 自陈「默认方法保持未实现语义，脚本侧会拿到空结果」） | 无 fabric behavior（§7 N1） | 补 `FabricPlatform` 实现或让能力矩阵把该项从 `UNAVAILABLE` 改判（须先改行为） | **deviation 记录**，owner = loader-port/W6；本票不实现 fabric data map 体系（NeoForge data map 无 fabric 对应物） |
| EntitySelectors factory/builder/query | `EntitySelectorsPlugin`（SERVER/TEST）→ `registry.register("EntitySelectors", new EntitySelectorsJS())`；fabric 经 `FabricPluginLoader.BUILTIN_PLUGINS` | **该 binding 自身即目标 owner**（无 managed 替代面：它是本域唯一的公开查询面） | 共享树 `EntitySelectorsJS`/`EntitySelectorBuilderJS`（loader 树，直接 import MC）；版本差异由 `EntitySelectorFactory`（26.1/26.2 `MinMaxBounds` 包名 + 13 参构造反射）与 `versions/1.21.1/**` 镜像承载 | **behavior**：`entityselectors-query.js` 44/44（neoforge，§5.3）；**declaration**：`EntitySelectorsQueryBindingTest` golden + `QueryToolDeclarationParityTest`；**trace**：capability golden 两 loader（fabric 侧本 commit 实跑） | **无可删路径**：本域没有旧生成物/影子观察面，binding 就是公开功能（AC10「公开查询功能不删除」）。若将来要重命名/内聚，前置=behavior+declaration+trace 全绿（**已满足**）+ 无调用者（**已满足**）+ 维护者 sign-off | **保留**（不删） |
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
| N1 | fabric 节点 EntitySelectors **runtime query smoke**（真实服务器上生成 + 执行 + 命中） | 本票只在 neoforge 26.1.2 跑专用服务器。fabric 侧的**注册面**已由 `:26.1.2-fabric:test` 实跑证明（§5.8），但「真实 ServerLevel 上执行选择器」未在 fabric 跑。CI 有 `fabric-runtime-smoke` job（`versions/26.1.2-fabric/src/test/resources/fabric-runtime-smoke/**`）可挂本票 fixture | loader-port / 维护者排 CI 时 |
| N2 | fabric `Registry.get(...).dataMap*` 静默空值 | NeoForge data map 无 fabric 对应物；`FabricPlatform` 未覆写 `registryQueryService()` → 回落 `EMPTY_REGISTRY_SERVICE`（显式 deviation，非本票改 fabric 运行时行为） | loader-port / W6 |
| N3 | 1.21.1 / 26.2.0 节点的 runtime fixture | 本票只在 26.1.2 跑 `/nekojs test`；1.21.1 镜像的同步改动（含本次 F2 回退）只到 `compileJava` 层；26.2.0 未跑 | 本票 owner（后续 smoke 排期）/ 维护者 |
| N4 | `EntitySelector` 未覆盖的过滤面（scores / advancements / NBT / loot predicate，类 javadoc 的 v1 后续项） | 既有缺口，非本票范围；fixture 只覆盖已公开成员 | 后续票 |
| N5 | wiki 与代码矛盾（`wiki/全局绑定.md`：EntitySelectors「仅 NeoForge」，但 Fabric 内置清单注册了它且类无守卫） | `wiki/` 非本票写集 | 文档 owner |
| N6 | DataMap binding **CLIENT** 只有 source trace（golden 已从 `SUPPORTED` 改判 `PARTIAL`，注明「未实测」） | 裸 JVM 无法初始化 client 绑定分支的 MC client 类（同 `VanillaRegistryProbe` 事实），也没有跑 client runtime smoke | 本票 owner（需 client 侧 smoke 才能升级；当前不得记 SUPPORTED） |
| N7 | **作用域语义缺口**：`type('非玩家类型')` / `type(非玩家, inverse)` / `typeTag(...)` **不切作用域**，所以 `EntitySelectorsJS` 类 javadoc 与 wiki 风格的文档示例 `create(b => b.type('minecraft:cow'))` 在无玩家服务器上命中 **0**；要查实体必须用实体基座预设（`allEntities()` 等）或 `type('minecraft:player', true)` | 这是**公开选择器作用域语义变更**（「只查玩家列表」→「查全部实体」），超出本票 fixture 授权；修法已实现过并被审查判为越权（§5.7）。本票已把现状钉在裸 JUnit + 游戏内 fixture 的 `GAP:` 断言组，并修好类 javadoc 的示例（不再展示会命中 0 的写法） | **维护者裁决**（是否改语义 / 是否单开 domain 票）；若判「应切作用域」，前置=本票 fixture 的 `GAP:` 组改期望 + 1.21.1 镜像同步 + REPORT 留痕 |
| N8 | **未知实体类型 tag 静默 no-op**：`typeTag('nekojs:no_such_tag')` 不报错，过滤恒假、查询恒空；与 `type(...)` 未知类型直接报错**不一致** | spec 04 明确禁止静默 no-op，与现状冲突。修法（未声明即抛带域+入口的普通错误）已实现过一次并经游戏内实证，但属**公开语义变更**（静默空 → 报错），超出本票授权，故回退为现状并记录。**保留**的是 `typeTag(null)` 的域+入口守卫（AC3 合规改进，见 §5.7） | **维护者裁决 / domain 票**（同时决定 `type` 与 `typeTag` 的错误语义是否对齐） |
| N9 | 查询域 declaration golden **不在 09 的 `REGENERATE.md` §1 基线清单**里：`src/test/resources/golden/query/*.d.txt` 由**同一个** `BindingDeclarationGenerator`（生产类）派生、却住在根测试树，09 的清单只列 `common/src/test/resources/nekojs/**` 与 `common/src/test/probe-ts/**` | 09 的 regenerate 流程（`-Dnekojs.golden.regenerate=true`）覆盖不到根测试树的查询 golden；本票的 golden 变更只能靠「手工比对 + REPORT §8.3 留痕」执行 09 纪律，**没有** regenerate 开关兜底 | **managed-surface（09）owner**：建议在 `docs/architecture-refactor/baseline/2026-09-12-managed-surface/REGENERATE.md` §1 增一行「查询域 declaration golden：`src/test/resources/golden/query/*.d.txt`，守护测试 `DataMapQueryBindingTest`/`EntitySelectorsQueryBindingTest`/`QueryToolCapabilityMatrixTest`，派生输入 `BindingDeclarationGenerator` + capability 探针」，并声明该域暂不支持 regenerate 开关（只读 + 显式留痕）。**登记动作属 09 领地，本票不改 09 文件** |

**observation（非 not-verified，但应留痕）**：

- O1 `ScriptError.getConciseDetailText`（`conciseScriptErrorLogs=true` 默认）把 `原因` 取消息里**最后一个** `": "` 之后的部分，于是 `EntitySelectors.find: selector must not be null` 在日志里只显示 `selector must not be null`——域+入口在**异常消息**里完整存在（fixture 的 `e.message` 断言证），只是 concise 显示被截断。要看全文需 `conciseScriptErrorLogs=false`。这是全域显示策略，非查询域特有；AC3 按「错误携带域+入口+源位置」判定满足。
- O2 统一错误管线报出的**行号比真实行号大 2、列号正确**：`negative/entityselectors-illegal-location.js` 的真实非法调用在第 27 行（列 17 = `EntitySelectors` 的 `E`），管线报 `29:17` 且 snippet 内容取自第 27 行。复现：把该文件放进 `run/nekojs/test_scripts/` 跑 `/nekojs test`（`evidence/runserver-25971-review-negative-extract.log`）。疑似脚本受理前的行偏移（ESM 受理/包装），需 diagnostics 侧确认；非查询域特有。

## 8. 偏离与问题

1. **改动的准确计数是「4 处行为/形态」而不是「3 处」**（原自述漏掉了 `DataMapJS.itemHolder` 的 null 守卫，`62fceb99` 自陈「改为带域+入口」）：F1（保留：DataMap class→instance binding）、F2（**回退**：作用域位）、F3（**回退**：未知 tag 校验）、F4 相关的 `itemHolder` null 守卫（**保留但如实标注**：脚本侧不可达，只为 Java 调用方挡裸 NPE——引擎把 JS `null` 适配成 `ItemStack.EMPTY`，这一点由 §5.2 的 `empty:` 断言与 `runserver-25971-prefix-extract.log` 的 `furnaceFuel(null) -> null` 双向证明）。所以最终留在 diff 里的公开改动只有 **1 处**（F1 的注册形态）+ 错误消息域前缀 + 注释/javadoc。
2. **审查裁定后的状态**：F2/F3 回退使本票回到「fixture 票」的授权范围内，唯一必需的前提是 F1（类比票 01 为产出基线而修 fabric 构建）。回退理由与保留项逐条见 §5.7。
3. **golden 变更留痕（09 REGENERATE §3 格式）**：
   - `src/test/resources/golden/query/datamap-binding.d.txt`：`let DataMap: typeof $DataMapJS;` → `let DataMap: $DataMapJS;`。**原因**：F1（class→instance binding，§5.6）。**影响**：declaration 文本形态变化，符号名/成员集合不变；对脚本作者无破坏（`DataMap.furnaceFuel(...)` 调用形态一致，且修前该调用**根本不可用**）。**审阅**：本票 owner 自查 —— **仍缺维护者审阅记录**（见下方第 8 条）。
   - `src/test/resources/golden/query/capability-matrix-fabric.txt` 首行注释：陈旧类名 `QueryToolCapabilityMatrixFabricTest` → `QueryToolCapabilityMatrixTest`；并补「本文件由 `:26.1.2-fabric:test` 实跑派生」与 SUPPORTED/PARTIAL 口径头注释。**影响**：仅注释。
   - `src/test/resources/golden/query/capability-matrix-neoforge.txt`：`DataMap binding[CLIENT]` **`SUPPORTED` → `PARTIAL`**，evidence 改为「source trace only…not runtime-verified」（原为硬编码 SUPPORTED，与「每行由真实探针派生」的金口径冲突）。**原因**：审查裁定 N6。**影响**：capability 判定收紧，无行为变化。同文件补 SUPPORTED/PARTIAL 口径头注释。
   - `entityselectors-binding.d.txt` 未变（方法签名未动）。查询域 golden **不走** `:common:regenerateGoldens`（机制不适用，见 §2.3 与 N9）。
4. **09 基线清单缺登记**：`src/test/resources/golden/query/datamap-binding.d.txt` 与同目录其余声明基线由生产 `BindingDeclarationGenerator` 派生却在 09 inventory 之外。登记建议与 owner 见 §7 N9（**不改 09 文件**）。
5. **golden 审阅纪律未闭合**：上述三处 golden 变更目前只有「owner 自查」，缺维护者审阅记录（09 §3 第 2 条要求「谁、何时、结论」）。owner = 维护者；在拿到审阅记录前，本报告不宣称这些基线变更已完成 §3 全流程。
6. **`/nekojs test` 无法自建实体的硬约束**（§5.3）：世界状态前置必须由 RCON 预先完成（测试运行器 `synchronized` + 阻塞服务器线程，期间世界不 tick，同 tick 召唤的实体对选择器不可见）。本 commit 把该前置内建为运行器的 `setup` 步骤并改用**相对坐标**，使一条命令即可复现，不再依赖「世界种子恰好让 spawn 落在 (0,-60,0)」这一实验环境巧合。
7. **测试计数是累计值**：`TestJS` 的 passed/failed 计数在 TEST 环境内跨 `/nekojs test` 调用累计（同一 binding 实例）。证据以**逐条 PASS/FAIL 行**为准；运行器的 `verify` 对每个 SUMMARY 行都要求 `0 failed` 并对逐条失败行另外设门，历史累计不会掩盖失败（§5.9）。
8. **日志编码坑（新踩）**：mod 自己写的 `run/logs/nekojs/test.log` 是**平台 ANSI**（本机 zh-CN = GBK；已归档的旧证据 `nekojs-test-green.log` 同款字节），而会话 stdout 里的非 ASCII 会被 JVM/gradle 管道**吞成 ASCII**（cmd 重定向 / `chcp 65001` / `Start-Process -RedirectOutput` 三种写法实测都改不掉，daemon 编码在启动时固定）。因此运行器的 `extract` 以 `test.log` 为主源、stdout 只取 ASCII marker——这条已写进 `bench/query/README.md`，避免后来者把「控制台里看不到中文失败行」误当作「没有失败」。
9. `bench/query/diagnostics/**` 保留为实读探针（定位 N2/F2/F3/entity-ticking 前置 + F1 的 A/B 对照）；它们是诊断脚本、含恒真断言或刻意失败，不进默认 fixture 集。
10. **自审发现并修复的两处运行器缺陷**（细节见 §5.9 第 5 条）：中文 marker 依赖会因编码而静默失效；`Select-String` 大小写不敏感导致 `BUILD ` 误命中 PASS 消息。前者会让「负向探针跑完 verify 仍返回 0」（实测复现），后者会制造假重复行——两者都属于「检查看似存在、实际不检查」的形态，已随本 commit 修掉并留下 `-IncludeNegative` 的失败判定证据。
