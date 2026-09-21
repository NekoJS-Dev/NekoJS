# Villager Trades 迁移材料（ticket 22）

> 面向脚本作者与 Java 侧消费者：Villager Trades 从「静态 binding + 静态 pending 队列 + reload
> 末端直接反射改 registry」收口到「既有 ServerEvents 数据/reload 子事件 → root 持有的 domain
> collector → 合法 commit 点由平台/版本 Adapter 执行 registry mutation → generation/stale 绑定的
> 只读 query」。
>
> **两条路径目前并存**：新路径（本票交付）与旧路径（保持原样，等待维护者 sign-off 后删除）。
> 本文只描述现状与迁移方向，不宣称旧路径已删除。

## 1. 入口与能力（当前快照）

| 能力 | 写法 | 说明 |
|---|---|---|
| 交易声明（新，推荐） | `ServerEvents.tradeDeclaration(event => event.add('<tradeSet>', { ... }))` | server 脚本；事件在「服务器启动收集点」与 `SERVER` reload 的 `DOMAIN_PLAN` 阶段被 post；收集期只产出 inert 计划，注册表 mutation 只在合法 commit 点由 Adapter 执行 |
| reload 子事件（新） | `ServerEvents.tradeReload(event => event.declareObsolete('<tradeSet>'))` | 显式释放某个 trade set（commit 点恢复 NekoJS 基线）；**普通 reload 不物理删除**只是不再声明的交易，它们进 `unrestoredListingKeys` |
| 只读查询（新） | `VillagerTrades.query()` | 返回只读快照：`status`(`ACTIVE`/`STALE`)、`generation`、`adapterId`、`total`、`tradeSetIds`、`countOf(id)`、`unrestoredListingKeys`、`retiredListingKeys`、`statusReason`、`describe()`；绑定调用方 generation，旧 generation 得到确定性空值 |
| 旧静态 add | `VillagerTrades.add('<tradeSet>', { cost, costB?, result, maxUses?, xp?, priceMultiplier? })` | **保持原样**：暂存进 `VillagerTradeManager` 的进程级 `PENDING`，reload 收尾时反射改注册表；删除需维护者 sign-off（AC10） |
| 旧计数 | `VillagerTrades.pendingCount()` | **保持原样**（旧暂存队列的计数）；同属 AC10 的删除项 |
| 第一版**不提供** | — | `remove` / `replace` / `modify` 不在公开面、golden、declaration、示例或能力承诺中；也不定义 server/client 同步协议 |

配置形状（新路径，与旧路径逐字一致，便于迁移）：`{ cost: '1x minecraft:emerald', costB: '2x minecraft:apple'(可选), result: '5x minecraft:apple', maxUses: 12, xp: 2, priceMultiplier: 0.05 }`；
`maxUses > 0`、`xp >= 0`、`priceMultiplier` 在 `0..1`，`cost`/`result` 必填。

trade set id 形状（节点差异，旧新路径一致）：

| 节点 | id 形状 | 例 |
|---|---|---|
| 26.1.2 / 26.2.0（NeoForge；两个 fabric 26.x 无适配器） | `VILLAGER_TRADE` / `TRADE_SET` 可重载注册表的 key | `minecraft:farmer/level_1`、`minecraft:wandering_trader/buying` |
| 1.21.1 | 经典交易池 `<profession>/level_<n>` 或 `wandering_trader/<pool>` | `minecraft:farmer/level_1`、`minecraft:wandering_trader/level_2` |

## 2. 旧静态路径 → 新路径对照

| 环节 | 旧路径（仍在） | 新路径（本票） |
|---|---|---|
| 声明入口 | `VillagerTradesJS#add`（`bindings/static_access`，静态 binding） | `ServerEvents.tradeDeclaration`（既有事件组的子事件；payload = `VillagerTradeDeclarationEventJS`，SERVER、posted-object、非 cancel/非 dispatch） |
| 收集时机 | 脚本执行期即时入队（`VillagerTradeManager.stageAdd`） | 候选 `DOMAIN_PLAN`（`CandidateDomainCollector#collect`）/ 服务器启动收集点；收集期**零 live registry 副作用** |
| 暂存状态 | `VillagerTradeManager` 的 `private static PENDING` + `HOLDER_SNAPSHOTS` / `ORIGINALS` / `PREVIOUSLY_REGISTERED` / `snapshotEpoch` | `VillagerTradeCandidatePlan`（generation-scoped，inert，挂 `CandidateStatePlan` 联合边界）+ owner 实例字段（root 生命周期持有） |
| 失败语义 | 逐条跳过（`warn` + `skipped`），部分应用可观察 | 整批失败：收集期异常 → `DOMAIN_PLAN` 失败；Adapter 拒绝/未知 trade set → `STATE_PLAN` 联合预检失败；旧 active 保留，无部分 mutation |
| registry mutation | `VillagerTradeManager#apply`（静态方法，reload 末端由 listener / 命令直接调用；反射 unfreeze + 内部 map surgery） | `VillagerTradeDomainOwner#apply`（平台/版本 Adapter，只在 commit 点由联合边界调用）；26.x 与 1.21.1 成对实现 |
| 移除语义 | 上一轮注入的项先删、touched TradeSet 从 originals 恢复；脚本不再声明 = 静默消失 | 同一 commit 恢复 NekoJS 基线 → 应用完整新计划；不再声明进 `unrestored` 记录，显式 `declareObsolete` 才 `retired` 并恢复基线 |
| 查询面 | 只有 `pendingCount()`（暂存计数） | `VillagerTrades.query()`：只读快照 + generation/stale 校验 + unrestored/retired 记录；不暴露 live registry view、不返回可写对象 |
| Fabric | 无实现（默认无此 binding；无错误 no-op 风险由「binding 不存在」承担） | 显式 unavailable：事件面存在、collector 存在，整批在 preflight 以 `VillagerTradeUnavailableException` 拒绝；`query()` 返回 `STALE` + `unavailable:...` |

## 3. 旧 route 消费者清单（截至 `d0974573`+本票工作区）

`VillagerTrades.add` / `VillagerTrades.pendingCount` / `VillagerTradeManager` 的调用点（`grep` 实测，排除 `build/`）：

| 消费者 | 位置 | 用途 |
|---|---|---|
| `VillagerTradesJS#add` / `#pendingCount` | `src/main/java/.../bindings/static_access/VillagerTradesJS.java`、`versions/1.21.1/.../VillagerTradesJS.java` | 脚本面入口本体 |
| `VillagerTradesPlugin` | `src/main/java/com/tkisor/nekojs/villager/VillagerTradesPlugin.java` | 注册 `VillagerTrades` binding 与 type doc（26.x / 文件带 `//? if neoforge`） |
| `ServerEventListener`（26.x 与 1.21.1 成对） | `src/main/java/.../listener/ServerEventListener.java`、`versions/1.21.1/.../listener/ServerEventListener.java` | `beginReload()`（reload 前清暂存）、`apply(server)`（about-to-start 与 TagsUpdated 刷出）、`reset()`（server stopped） |
| `NekoJSCommands`（26.x 与 1.21.1 成对） | `src/main/java/.../command/NekoJSCommands.java`、`versions/1.21.1/.../command/NekoJSCommands.java` | `/nekojs reload server` 前 `beginReload()`、reload 后按 `pendingCount()` 刷出 |
| 脚本（用户侧） | 不在仓库内；`wiki/全局绑定.md` 有 `VillagerTrades.add(...)` 的用法说明 | 现有整合包脚本 |

仓库内**没有测试**断言旧静态路径（`src/test`、`common/src/test` 内无 `VillagerTrades` 命中），
也没有其它域引用其类型；旧路径的删除面因此是「5 个 Java 文件 + 1 个 wiki 段落 + 用户脚本」。

## 4. 两条路径并存与混用边界（**同一 trade set 不可混用**）

新路径按「NekoJS 持有基线 → 恢复基线 → 应用完整新计划」重写它触碰的 trade set；旧路径按自己的
`HOLDER_SNAPSHOTS`/`ORIGINALS`（vanilla 基线，首次触碰时捕获）重写它触碰的 trade set。两者对
**同一个 trade set** 同时使用时没有合并政策，结果取决于执行顺序（last writer wins），因此：

- 同一个 trade set 只走一条路径；迁移期推荐整体切到 `ServerEvents.tradeDeclaration`；
- 两条路径各自记账，互不感知（新的 `unrestored`/`retired` 记录也不包含旧路径注入的条目）；
- 本票**不引入**合并/去重政策，也不把旧路径改写成新路径的兼容 shim（那会让「不保留长期兼容
  双路径」这条验收项失去意义）。

## 5. 删除条件（维护者 sign-off 门禁，AC10 不勾选）

删除旧公开路径需要同时满足（本票已备 1–4，5 待维护者）：

1. **替代路径 parity**：`ServerEvents.tradeDeclaration` / `tradeReload` / `VillagerTrades.query()` 已交付；
   26.x 与 1.21.1 的 Adapter 由节点测试真跑（见 REPORT §3/§6 与 `command-output/`）。
2. **失败保留**：整批失败保留旧 active、无部分 mutation；未使用该域的 reload 不受影响
   （见 REPORT §5 的行为修复）。
3. **迁移表**：本文（§1–§4）。
4. **旧 route 无消费者证据**：§3 的清单——仓库内唯一的消费者是旧 binding 自身、两个 listener、
   两个 command 入口与一个 wiki 段落；无测试、无其它域依赖。
5. **维护者确认**：删除是公开面 breaking（`VillagerTrades.add` / `pendingCount` 与
   `VillagerTradeManager` 全部静态符号），需要维护者知情/追认。**本票不执行删除**，也不勾选 AC10。

删除时应一并移除：`villager/VillagerTradeManager.java`（26.x + 1.21.1 成对）、`VillagerTradesPlugin`
的 binding 注册与 type doc、`ServerEventListener`/`NekoJSCommands` 的 5 处调用、`VillagerTradesJS`
的 `add`/`pendingCount` 与随之无用的 `ItemSpec` 解析（`query`/`describe` 保留）、以及
`wiki/全局绑定.md` 的旧用法段落。删除后**不保留**任何长期兼容 shim。

## 6. 接口/声明追溯

| 面 | 来源 | tier |
|---|---|---|
| `ServerEvents.tradeDeclaration` / `tradeReload`（组名、事件名、payload 类、side、cancel/dispatch） | `ServerEvents` 事件组声明 → `NekoScriptCatalog.events` | managed（`Ticket22VillagerTradeEventSurfaceTest` 固定） |
| payload 脚本面（`add` / `getAddedCount` / `declareObsolete` / `getTotal` / `getTradeSets` / `countOf`） | `@Doc`/`@Param`/`@Return` 注解 + payload 类方法 | managed 派生（`Ticket22VillagerTradeEventSurfaceTest` 固定，含「不含 remove/replace/modify」） |
| `VillagerTrades.query()` 只读面 | binding 方法 + `VillagerTradeQuerySurface` | managed 派生（只读成员断言 + `Ticket22VillagerTradeDomainTest`） |
| 旧 `add`/`pendingCount`/`VillagerTradeManager` | 未改（仅 javadoc 增加迁移提示） | 待删除（AC10 门禁） |

## 7. 已知边界（不在本票解决）

- **不定义 server/client 同步协议**：交易的多人同步语义不在本票（AC8）；本票不改同步路径。
- **不补 Fabric parity**：fabric 节点是显式 unavailable（AC9），不自动补交易注册表 mutation。
- **26.2.0（NeoForge）未单独跑测试**：本票在 26.1.2 上真跑 26.x 面；26.2.0 与 26.1.2 共用同一份
  compat 之外的共享源码，但**未在 26.2.0 节点上执行**（见 REPORT §6 未验证项）。
- **vanilla 注册表未就绪时不猜测**：Adapter 在没有绑定 server 时报告 `UNAVAILABLE`，不做 no-op。
