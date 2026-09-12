# Ticket 25 实施报告：DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径

> 工单：`docs/architecture-refactor/implementation-tickets/25-query-tools.md`（已认领 in-progress，Assignee=zcode-agent）。
> 权威 spec：`docs/architecture-refactor/specs/04-public-contract-and-plugin-model.md`（contract/tier/capability 口径）、`08-ported-features-event-surface.md`（EntitySelectors 保持 factory/query binding，不事件化）。
> 依赖消费：09 号票交付的 managed surface 机制（`NormativeApiContract`、`CapabilityStatus` 三态、`LegacySurfaceAdapter` 观察面、deterministic fixture 模式、`REGENERATE.md` 纪律）。
> 基线 commit：`98478559`（本票认领提交）。工作分支 `ticket-25-query-tools`。

## 0. Commit 清单

| Commit | 内容 |
|---|---|
| （Phase 1 本提交） | `docs(baseline): ticket 25 query tools inventory` |
| （Phase 2） | source contract 归类 + capability 三态 fixture |
| （Phase 3） | DataMap query binding fixture |
| （Phase 4） | EntitySelectors query binding fixture |
| （Phase 5） | declaration/Probe parity fixture |
| （Phase 6） | capability/trace + 删除条件表 |
| （Phase 8） | REPORT 收尾 |

## 1. 现状盘点（Phase 0，不改代码）

### 1.1 DataMap 域

| 观察物 | 现状 |
|---|---|
| binding 类 | `src/main/java/com/tkisor/nekojs/bindings/static_access/DataMapJS.java`（整文件 `//? if neoforge` 守卫，1.21.1/26.x NeoForge 共享同一文件；无版本内守卫） |
| runtime member | `furnaceFuel(ItemStack) -> Integer`、`compostable(ItemStack) -> Float`；命中返回值，未命中返回 `null`（javadoc 明示 `??` 兜底语义） |
| 注册入口 | `NekoJSCorePlugin.registerBinding`（:165）`registry.register("DataMap", DataMapJS.class)`——class binding（静态访问形态），全部 ScriptType 可见；`NekoJSCorePlugin` 整文件 neoforge 守卫 |
| MC-facing 类型持有 | `NeoForgeDataMaps.FURNACE_FUELS` / `COMPOSTABLES`（`net.neoforged.neoforge.registries.datamaps.builtin`）；holder 经 `BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())`——全部在 binding 类内，共享层不含 MC 类型（DataMapJS 本身住共享树但有 loader 守卫） |
| 替代查询面（managed） | `Registry` 全局（common `RegistryFacade`/`RegistryView`）已有 `dataMapIds()` / `dataMapValue(typeId, id)`，portable-core contract 反射符号（`global:Registry`、`member:RegistryView.*`），实现 SPI `RegistryQueryService`（common，零 MC import）+ `NeoForgeRegistryQueryService`（根 src，neoforge-only，文件头 `TODO(loader-port): deferred to the LoaderBridge fabric port`） |
| 测试覆盖（本票前） | 仅 `CoreBindingsGlobalsTest` 断言 `DataMap` 名字注册进 SERVER 绑定集（preflight 未定义标识符防护）；无行为/声明/capability fixture |
| 文档 | `wiki/全局绑定.md` 无 DataMap 章节 |
| 已知静默行为 | fabric 节点上：binding 未注册（preflight 报未定义标识符，属显式失败）；但 `Registry.get(...).dataMapIds()/dataMapValue(...)` 走 `RegistryQueryService` 默认方法**静默返回空列表/null**——spec 04 禁止的静默 no-op 形态，本票记录为显式 deviation（owner：loader-port/W6），不在本票改 fabric 运行时行为 |

### 1.2 EntitySelectors 域

| 观察物 | 现状 |
|---|---|
| binding 类 | `src/main/java/com/tkisor/nekojs/util/selector/EntitySelectorsJS.java`（工厂 + 查询入口，无守卫=全节点编译）、`EntitySelectorBuilderJS.java`（26.x 形态；1.21.1 镜像在 `versions/1.21.1/src/main/java/com/tkisor/nekojs/util/selector/EntitySelectorBuilderJS.java`，头注释要求两侧同步） |
| runtime member | factory：`create(Consumer<Builder>)`、`builder()`、6 个预设（`allPlayers/allEntities/nearestPlayer/nearestEntity/randomPlayer/randomEntity`）；query：`find(ServerLevel, EntitySelector[, x,y,z])`。builder：`type/typeTag/isAlive/x/y/z/dx/dy/dz/distance/distanceBelow/distanceAbove/name/gamemode/team/tag/limit/order*/create` |
| 注册入口 | `EntitySelectorsPlugin`（`@RegisterNekoJSPlugin`，实现 `NekoJSPlugin` + `BindingsPoint.Contributor`）——**仅 SERVER 与 TEST** ScriptType 注册 `EntitySelectors`；NeoForge 走注解扫描发现，Fabric 走 `FabricPluginLoader` 内置清单（`versions/26.1.2-fabric/.../FabricPluginLoader.java:37`） |
| 版本 Adapter | `EntitySelectorFactory`（`//? if >=26`）：26.1/26.2 的 `MinMaxBounds` 包名差异（criterion/predicates）经反射按 FQN 解析 `Doubles` 工厂与 13 参 `EntitySelector` 构造器；1.21.1 镜像不走工厂（FQN 唯一，直接静态构建）。构建的 selector `usesSelector=false`，不触发原版 selector 权限校验 |
| 测试覆盖（本票前） | 无 |
| 文档 | `wiki/全局绑定.md` 有章节；**但标注「仅 NeoForge」与代码矛盾**（Fabric 内置清单注册了该插件且类无守卫）——capability 以代码为准，wiki 差异记入 §6 not-verified/差异表（wiki 非本票写集，不改） |
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
| `Identifier`/`ResourceLocation` | stonecutter `!mc_ids` 改名共享树统一写 26.x 形态 | 同左 | 同左 |
| `EntitySelector` 执行 | `selector.findEntities(source)`，`CommandSourceStack` 语义一致 | 同左 | 同左 |

### 1.5 裸 JUnit 约束（fixture 设计输入）

- 26.x 节点裸 JUnit 无 FML Loader：`BuiltInRegistries` 类初始化链抛错（`src/test/java/com/tkisor/nekojs/testfixture/VanillaRegistryProbe.java` 实测固化）→ **依赖真实注册表/数据包的命中值断言无法在 `check` 内执行**，走 `runServer` + `/nekojs test` 脚本 fixture（§5）。
- 不依赖注册表的输入校验与反射面断言可在裸 JUnit 执行（builder 校验、null 守卫、binding 注册、probe 派生）。

## 2. source contract 归类与 capability（Phase 2）

（Phase 2 完成后填写）

## 3. fixture 矩阵（Phase 3/4/5）

（Phase 3/4/5 完成后填写）

## 4. AC1-AC10 逐条判定

（Phase 8 填写）

## 5. 游戏内验证（runServer）

（Phase 7 完成后填写）

## 6. capability/source trace/删除条件表

（Phase 6 完成后填写）

## 7. not-verified + owner

（Phase 8 填写）

## 8. 偏离与问题

（Phase 8 填写）
