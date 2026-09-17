# Item/Block 修改迁移材料（ticket 39）

> 面向脚本作者与 Java 侧消费者：`ItemEvents.modification` / `BlockEvents.modification` 在
> `1.2.0` 的写法、语义变化与边界。事件面（组名、事件名、payload、side、priority/cancel、
> dispatch 时机）**不变**——变的是「声明如何被收集、何时被应用到 live 对象、失败时保留什么」。

## 1. 入口与能力（当前快照）

| 能力 | 写法 | 说明 |
|---|---|---|
| item 修改声明 | `ItemEvents.modification(event => event.modify('minecraft:diamond', item => { ... }))` | server 脚本；事件在「服务器启动收集点」与 `SERVER` reload 的 `DOMAIN_PLAN` 阶段被 post |
| block 修改声明 | `BlockEvents.modification(event => event.modify('minecraft:stone', block => { ... }))` | **26.x 面**（26.1.2 / 26.2.0 / 两个 fabric 26.x）；1.21.1 无此总线 |
| setter / property parity | `item.maxStackSize = 16` ≡ `item.setMaxStackSize(16)`；`block.hardness = 2` ≡ `block.setHardness(2)` | 同一 setter、同一校验/规范化/声明/fingerprint（`ModificationViewSurface`） |
| item 属性面 | `maxStackSize`(1..99)、`maxDamage`(>=0)、`rarity`('common'/'uncommon'/'rare'/'epic')、`fireResistant` | 两侧共有；`maxStackSize>1` 与 `maxDamage>0` 不可共存（预检按「基线 + 声明」求值） |
| item 复合组件（26.x） | `food = { nutrition, saturation, canAlwaysEat, eatSeconds }`、`tool = { miningSpeed, damagePerBlock, canDestroyBlocksInCreative }`、`attackDamage`、`attackSpeed` | 1.21.1 只有上面四个基础属性（能力表见 REPORT 五节点差异表） |
| block 属性面（26.x） | `hardness`、`resistance`、`lightLevel`(0..15)、`requiresTool`、`friction`(0..1)、`jumpFactor` | commit 点写 `Properties` 声明源 + `Block` 副本 + 每个 `BlockState` 副本 |
| 移除请求 | `item.food = null`、`item.tool = null` | 记录「移除该组件」而不是「未设置」（规范化声明里值为 `null`） |
| 声明移除的恢复 | 脚本不再声明某项修改 → 成功 reload 后回到 NekoJS 基线 | 诊断 `RESTORED`；**不再**像旧 item 路径那样 stale |
| 被阻止的批次 | 非法值 / 未知目标 / 不可恢复字段 → 整批不提交，旧 active 保持 | 诊断 `BLOCKED` / `RECOVERY_FAILED`；reload 失败结果带 phase 与 domain |

## 2. 迁移要点（1.2.0）

1. **property 写法继续有效，而且现在真的生效**：旧 Web/GraalJS 路径下宿主视图的
   property 写被静默丢弃（`item.maxStackSize = 16` 既不落字段也不落 setter，只有
   `item.setMaxStackSize(16)` 生效）。现在两种写法命中同一 setter——原本「写了但没生效」
   的脚本会**开始生效**。这是修复，但可能让此前静默失效的写法暴露出越界/类型错误
   （错误在写入期抛出，带成员名与可写成员目录）。
2. **item 的「声明移除」不再 stale**：旧实现只在再次 modify 同一目标时恢复快照，脚本删掉
   声明后服务端保持上一轮修改；现在成功 reload 统一「先恢复 NekoJS 基线，再应用新的完整
   计划」，item 与 block 语义一致（block 旧行为本就 restore-all-first）。
3. **错误时机更早、更整批**：未知目标 id 或回调抛出 → 候选收集失败（DOMAIN_PLAN，整批
   不提交）；值域/组件不变量不满足 → 联合预检失败（STATE_PLAN，与 global/shared 写集联合
   失败）。两者都保留旧 active，**没有**部分修改、混合 generation 或残留挂起监听器。
4. **不再有进程级静态 snapshot 与 `fire()` 重放入口**：旧 `static SNAPSHOTS` 与
   `fire()`（restore-all 后整体重放）已删除；snapshot/restore 状态由 root 授权的 domain
   owner 实例持有，root close 时恢复并清空。Java 侧消费者若要触发初始收集，用
   `ModificationDomainOwner#applyInitialPlan(server)`（平台入口已接线）；测试/独立 root
   直接实例化 owner 并注册进 root，不共享进程级状态。
5. **同目标多声明仍是「整体替换」**：同一次重放内对同一目标的后一条声明从基线叠加，
   前一条声明的其它属性不残留（旧可观察语义保留）。本票**不**引入 Dynamic Registry 式
   同 key 拒绝，也**不**新增 last-write-wins 合并政策。
6. **Java 侧回调参数仍是函数式接口**：`modify(id, Consumer<ItemModificationJS>)` /
   `modify(id, Consumer<BlockModificationJS>)`（与改造前签名一致，Java 侧照旧传 lambda）；
   脚本侧的 Graal 函数由沙盒 `HostAccess`（`allowAllImplementations`）实现该接口。
7. **恢复承诺的边界**：只承诺 NekoJS 拥有且 Adapter 已证明可恢复的字段——item 默认组件
   映射、block 六属性的三处副本。不承诺回滚任意 Java 对象内部状态、其他 mod、世界、网络
   或文件副作用（spec 09 外部副作用边界）。
8. **1.21.1 没有 block 半边**：`BlockEvents.modification` 在 1.21.1 不存在，脚本得到明确的
   「无此成员」错误（不是静默 no-op）；item 半边可用，组件发布走反射写 `Item#components`，
   `fireResistant` 是 `FIRE_RESISTANT` 组件（无需 server 绑定）。

## 3. 声明面（tier 归属）

| 面 | 来源 | tier |
|---|---|---|
| `ItemEvents.modification` / `BlockEvents.modification`（组/事件名、payload 类、side、cancel/dispatch） | `ItemEvents` / `BlockEvents` 事件组声明 → `NekoScriptCatalog.events` → TS/Python 声明 | managed（事件面 catalog 派生，`Ticket39ModificationEventSurfaceTest` 固定） |
| `BlockEvents` 脚本可见成员集合（含 `modification`） | `EventApiSurfaceGoldenTest` + 节点 golden `golden/block-events-api.txt` | golden（26.x 多一条 `modification`） |
| payload 脚本面（`modify` / `getModifiedCount` / 视图属性目录） | `@Doc`/`@Param` 注解 + `ModificationViewSurface` 成员目录（反射派生） | managed 派生（`ModificationSetterPropertyParityTest` 固定 parity） |
| 旧静态 `SNAPSHOTS` / `fire()` restore-all 入口 | 已删除（无消费者，见 REPORT AC14 的无消费者证据） | 删除完成（公开路径的正式删除仍待维护者 sign-off 记录） |

## 4. 已知边界（不在本票解决）

- **客户端可见性**：服务端写入立即生效（所有 `BlockState` 副本一起更新），纯视觉结果
  （`lightLevel` 等）不自动同步给已连接客户端——需要 relog 或区块 resync。本票不改同步
  路径，也不引入隐藏漂移；能力记录见 REPORT 的 capability/source-trace 表。
- **`maxStackSize` 上限 99**：26.x 的 `Item#ABSOLUTE_MAX_STACK_SIZE`；1.21.1 取同值常量。
- **fireResistant 的 server 绑定**：26.x 需要 damage type registry（server 未绑定时预检拒绝
  整批）；1.21.1 无此依赖。
- **NBT/组件深拷贝语义**：恢复用的是「首次修改时捕获的原始组件映射」；Vanilla 数据包
  reload 期间 item 默认组件的变更不由本票跟踪（与旧路径同一假设：注册期冻结）。
- **不自动补 Fabric parity**：两个 fabric 26.x 节点复用同一 Adapter（同一份
  `ModificationDomainOwner`），差异记录见 REPORT 五节点差异表；Fabric 特有同步面不在本票。
