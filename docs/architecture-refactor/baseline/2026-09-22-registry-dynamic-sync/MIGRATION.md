# 运行期动态注册批事务迁移材料（ticket 21）

> 面向脚本作者与维护者的「Dynamic Registry 多人 prepare/ack/commit 门禁」迁移要点。
> 本票交付 **common 层批事务管线**（preflight → 同 key fingerprint 冲突 → 服务端
> prepare → 客户端 prepare/ack → 受控 commit，任一失败整批不提交、旧 active 继续
> 服务）与 Adapter/Transport 边界；**平台侧激活未接线**（见 §4 门禁现状）。

## 1. 与 ticket 16 的关系（声明面不变，激活受门禁）

| 层 | ticket 16（已交付） | ticket 21（本票） |
|---|---|---|
| 声明面 | `DynamicRegistryEvents.dynamicRegistry` + `event.item/soundEvent/mobEffect` | **不变**，零迁移 |
| 候选计划 | inert 计划：收集、fingerprint、preflight、同 key 冲突、stale | 不变（仍是唯一声明入口） |
| 账本 | `DynamicRegistryPlanStore`（claim/stale/exposed） | 不变（联合 commit 点仍先落账本） |
| 激活 | 无（inert local plan only） | **新增** `core.dynamic.txn` 批事务：账本 commit 后入队，经 Adapter/Transport 接缝走 prepare/ack/commit；失败整批 ABORT |
| 生产绑定 | — | **未绑定**：`DynamicRegistryFacadeRuntime.bindActivationEngine` 只有测试装配调用；未绑定时行为与 ticket 16 完全一致（`unboundEngineKeepsTheInertTicket16Surface` 回归用例） |

脚本作者面（事件名、成员、Builder、错误信息）零变化——本票不动 contract/golden 与
TS/Python declaration（只读约束遵守）。

## 2. 批事务语义（作者需要知道的部分）

1. **整批成败**：一批声明要么全部激活，要么全部不激活。收集错误、同 key 冲突、
   服务端 Adapter prepare 拒绝、任一客户端 prepare 拒绝、ack 超时、参与者断线、
   close 抢占——任何一条都让整批 ABORT：已 stage 的 prepare 在客户端丢弃，服务端
   不做 registry surgery，旧 active 定义继续服务。没有部分注册、半成功 ID 或混合代际。
2. **同 key 定义变化（第一版限制不变）**：已暴露 key 的 fingerprint 变化在账本
   preflight 即整批冲突（`dynamic-registry-conflict`），事务不会开始；要改定义换新 id。
   `remove/replace/modify` 仍不存在，不存在静默覆盖。
3. **stale 仍是账本语义**：脚本不再声明 ⇒ stale/retired 标记，普通 reload 不物理
   删除；带 engine 的 reload 里 stale 项继续留在 live 状态与同步全量里。
4. **prepare/ack 不是原子性证明**：它们只是协议阶段。成功断言观察的是所有被激活
   节点的最终一致可见性（服务端 live + 各客户端 watermark/账本），或明确的拒绝/
   降级结果——不是消息存在本身。
5. **客户端不完整同步不激活**：无匹配 prepare 的 COMMIT、代际不匹配的 COMMIT、
   重复 COMMIT、迟到 ack 都有确定结果（`commit-without-matching-prepare` /
   `already-activated` / `late-ack-outside-transaction` 等），不出现静默 no-op。
   断线丢弃 staged prepare，重连经 STATE_SYNC 追平到服务端已激活代际（绝不激活
   更新代际）。

## 3. 启动期注册 vs 动态注册（选型表）

| 需求 | 用哪个 | 理由 |
|---|---|---|
| 世界生成前必须存在（方块、实体、世界结构引用） | 启动期 `RegistryEvents.register` | boot 期注册，参与 registry freeze，数值 ID 在世界创建前稳定 |
| 服务器运行期声明、可随 reload 重声明（物品、音效、效果） | `DynamicRegistryEvents.dynamicRegistry` | 批事务裁决激活；失败保留旧 active；同定义跨 reload 幂等 |
| 多人客户端可见性 | 当前两者都**不**提供跨进程同步 | 平台网络阶段未接线（§4）；整合包作者现状是客户端本地跑相同脚本（旧口径不变） |

## 4. 门禁现状与未验证类型（不因缺测改写）

**没有候选类型通过「目标 Adapter + 事务 + 同步」三门**：Item / SoundEvent /
MobEffect 三类的批事务语义已由 JVM 双 Adapter 测试证明（`DynamicRegistryClusterConsistencyTest`
等 31 用例），但真实平台 Adapter（数值 ID / registry surgery / 网络 payload）未实现、
未接线。三类全部记 **not verified** 并阻塞公开激活；不写成 unavailable，也不展示为
可用能力。生产示例见 `examples/dynamic-registry-transaction.js`（头注明确标注门禁现状）。

平台接线被以下事项阻塞（所有者见 REPORT §6）：
- 新增同步 payload 需要扩展票 17 冻结的 wire 子集（`NetworkRegistrationSourceTraceTest`
  断言 fabric 恰 6 处注册调用 / 5 个 payload 类型）——更新该 gate 属网络 owner 决策，
  本票不越权；
- `DynamicSyncMessage` 的 `DynamicDefinition` 需要跨进程 codec（当前为 JVM 内传值）；
- reload commit 后的 pump 驱动点（服务端 tick / post-commit 事件）需要平台装配。

## 5. 旧路径迁移（不变更，仅记录）

- 旧静态 binding `DynamicRegistry`（`DynamicRegistry.item(...)` 直注）**保留**：与
  新声明面各自独立记账，删除需维护者 sign-off（票面 AC9，本票零删除、零双写）。
- 旧路径「多人客户端不同步、需本地跑相同脚本」的限制照旧记录在
  `DynamicRegistries` javadoc；批事务路径没有引入新的 server-only 多人暴露
  （未绑定即无暴露；绑定时由 prepare/ack 门禁裁决）。
