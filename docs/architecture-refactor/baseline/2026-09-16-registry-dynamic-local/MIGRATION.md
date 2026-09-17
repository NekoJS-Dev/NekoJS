# 运行期动态注册（Dynamic Registry）迁移材料（ticket 16 / 1.2.0 clean cutover）

> 面向脚本作者的「服务器运行期动态注册 typed Builder + inert 候选计划」使用与迁移要点。
> **本票只交付本地 inert 计划**：事件收集、fingerprint、preflight、同 key 冲突、stale 记录
> 与 inert Adapter 请求。公开激活、多人 prepare/ack/commit、真实 registry mutation 由
> 事务/同步 gate（票 21）裁决；本表不承诺动态热更新已完成。

## 1. 命名决策（记录义务，票面 AC2）

| 项 | 值 | 理由 |
|---|---|---|
| 事件组 | **`DynamicRegistryEvents`**（独立组） | spec 08 给的工作名是 `ServerEvents.dynamicRegistry`（原文标注「工作名，非最终 API」）。`ServerEvents` 住在 NeoForge 专属树、Fabric 侧另有一份同 FQCN 文件；把运行期 facade 挂进 ServerEvents 会把独立生命周期焊死在 loader 专属组上，common 层也无法承载/测试该面。独立组与启动期 `RegistryEvents`（同样独立组）对称表达「两个生命周期」 |
| 成员名 | **`dynamicRegistry`**（唯一总线，SERVER side） | 保持 spec 08 的工作成员名 |
| 类型直达入口 | **`event.item(...)` / `event.soundEvent(...)` / `event.mobEffect(...)`** | 票面冻结的默认命名，未偏离 |
| 进入义务 | contract/golden（`common/src/test/resources/nekojs/dynamic/dynamic-registry-events.expected.d.ts`）、declaration（同一 golden + `DynamicRegistryDeclarationParityTest`）、迁移表（本文件） | 票面 AC2 要求，已随本票交付 |

## 2. 入口与能力（当前快照，仅本地计划）

| 能力 | 写法 | 说明 |
|---|---|---|
| 类型直达入口 | `DynamicRegistryEvents.dynamicRegistry(event => { event.item('mymod:ruby', b => { ... }) })` | 只有 item/soundEvent/mobEffect 三个入口；没有通用 type catalog |
| property 写入 | `b.maxStackSize = 16` | 与显式 setter 同一个 `Method`（同一校验/规范化/fingerprint 路径） |
| 显式 setter | `b.setMaxStackSize(16).setRarity('rare')` | 链式返回同一 builder surface；两种写法读数与 fingerprint 相同 |
| 注册 mode | `b.mode = 'reloadable'` / `b.setMode('reloadable')` | `world`（默认）/ `reloadable`；运行期 `global` 拒绝并指回启动期 `RegistryEvents` |
| 回调可省略 | `event.soundEvent('mymod:ping')` | 全默认值（如 `fixedRange = null` 表示由声音定义决定） |
| 计划可观察面 | `store.exposedEntry / staleIds / retiredKeys / claimOf / lastCandidateCollection` | Registry Runtime 侧账本查询（claim/stale/mode/exposed），测试与诊断经此面断言 |

## 3. 迁移要点（1.2.0）

1. **旧静态 binding `DynamicRegistry` 保留**：`ServerEvents.started(() => DynamicRegistry.item(...))`
   的旧写法本票**不改动、不双写**；新 facade 与旧直注路径各自独立记账。删除旧路径需要替代
   parity + 旧 route 无消费者 + 维护者 sign-off（票面 AC11，见 REPORT §6 消费者清单）。
2. **两种写法只影响声明位置，不影响定义语义**：新 facade 的 `event.item(id, b => ...)` 与旧
   `DynamicRegistry.item(id, b => ...)` 的配置项形状一致（stack size / rarity / fire resistant /
   mode），但新面走**候选计划**（前缀 `event.`，注册发生在 commit 点，不在脚本线程即时
   registry mutation），旧面仍是即时注册。迁移时按「声明式候选计划」语义重写。
3. **property 写法的语义收紧**：`b.maxStackSize = 200` 在写入点即抛带成员名的错误
   （旧面允许留到 MC 注册冻结期）；`b.rarity = 'EPIC'` 与 `'epic'` 归一化相同。
4. **同 key 定义变化＝整批冲突失败（第一版）**：把已声明 id 的定义改成不同内容会让整批
   候选失败（`STATE_PLAN` / `dynamic-registry-conflict`），旧 active 定义继续服务。
   **`remove`、`replace`、`modify` 不在第一版公开面**，也不出现在 Interface/golden/declaration/
   本表中；不要依赖静默覆盖。
5. **脚本不再声明＝stale/retired**：普通 reload 不物理删除；账目与 fingerprint 保留，对
   stale 项的「定义变化」同样按冲突拒绝（不因 stale 放开 replace）。
6. **未开放类型不是「暂时不可用」**：只有 item/soundEvent/mobEffect 进入候选范围；
   `entity_type`、`fluid`、`block` 等没有入口（成员解析期拒绝），记 `not verified` 并阻塞开放
   （见 REPORT 能力表），不以 no-op 或静默降级出现。
7. **本票不承诺热更新/多人同步**：计划只产出 inert Adapter 请求；真实注册、客户端同步与
   多人门禁归票 21。需要立刻可见的内容继续用启动期 `RegistryEvents.register`。
8. **声明面**：TS 声明（probe `@registry-builders` 面 + 事件声明）与本表同源；Builder 声明的
   头注是共享渲染器（ticket 15）的固定文本，动态条目的派生输入是 `DynamicBuilderContract`。

## 4. 明确不做 / 不是承诺

- 不激活多人同步、不做 prepare/ack 跨进程原子性声明、不宣称动态热更新已完成。
- 不提供 `remove`/`replace`/`modify`、同 key 覆盖或自动连带 Block/Fluid 注册。
- 不在候选期修改 live registry、不挂生产 callback、不提前发布对外 binding。
- 旧 `DynamicRegistry` 静态入口本票不删除、不双写（删除门禁见 REPORT §6）。
