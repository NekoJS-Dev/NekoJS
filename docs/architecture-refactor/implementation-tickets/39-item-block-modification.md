# 39: Runtime Item/Block modification 候选计划与 snapshot ownership

**What to build:** 保留现有 Item/Block modification 脚本入口，但把直接修改 live 对象与进程级静态 snapshot 的旧路径收口到既有事件/runtime/Adapter owner 之下：candidate 阶段只收集、校验和规范化 Item/Block 属性变化，合法 commit 点才由平台/版本 Adapter 应用 active 修改；失败保留旧 active。成功 reload 的目标是先恢复 NekoJS 持有的字段基线，再应用新的完整修改计划；平台无法证明可恢复时阻止该批提交并保持旧 active。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)、[06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)、[10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中要求的维护者删除确认是发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- 盘点 `ItemEvents.MODIFICATION`、`BlockEvents.MODIFICATION`、对应 wrapper、静态 `SNAPSHOTS`、restore/reapply 调用点、服务器启动与 `/nekojs reload server` 触发路径，形成旧行为 characterization。
- 盘点既有多个脚本、多个声明和注册/事件顺序下的同 Item/Block 同属性修改行为，固定可观察顺序与最终结果，不凭空引入新合并政策。
- 把修改声明改为 generation-scoped candidate plan；snapshot/restore 状态由 `NekoRuntimeRoot` 或其授权的既有 domain owner 持有，不新增公开 Modification Runtime 或第二框架，也不再用无 owner 的进程级静态 Map（包括 private static）。
- 在合法 commit 点由平台/版本 Adapter 应用新 active 修改计划；candidate 失败、watchdog、close 抢占或 Adapter 拒绝时保留旧 active 并清理候选资源。
- 测试脚本声明移除后的行为：成功 reload 先恢复 NekoJS 持有基线，再应用新完整计划；平台无法证明某字段可恢复时阻止该批提交并保持旧 active，不得把静默 stale 视为成功。
- 保证 `item.setMaxStackSize(16)` 与 `item.maxStackSize = 16`、Block 对应 setter/property 写入走同一 setter、校验、规范化、fingerprint 和修改计划路径。
- 与 10 联合预检同一 candidate 的 global/shared 顶层写集与修改计划，保证联合成功或失败。
- 验证客户端可见性：item/block 属性变化后的同步、chunk resync、relog 或明确不可同步边界不得靠隐藏漂移实现。
- 按 26.1.2、26.2.0、1.21.1 与 Fabric 节点差异放置 Adapter；common Java 模块禁止 MC/loader import，但不因此禁止既有 managed/raw/高级 Java script payload 暴露 MC 类型。
- 补 contract/golden、TS/Python declaration、capability/source-trace、runtime smoke、最小可运行示例、迁移表和旧静态 snapshot 删除条件。

## Acceptance criteria

- [x] `ItemEvents.modification` 与 `BlockEvents.modification` 的公开事件名、payload、side、priority/cancel 和 dispatch 时机进入 catalog/golden；本票不新增第二事件 bus 或重复 wrapper。
- [x] candidate 阶段只生成 inert modification plan，不修改 live Item/Block/BlockState、默认组件或平台集合；候选计划对生产路由、其他脚本 generation 和外部节点不可见。
- [x] commit 前 active generation 继续执行旧修改；commit 后新 active 修改恰好应用一次，旧 generation 计划和候选资源按所有权释放，无双重 restore/reapply。
- [x] candidate 收集、payload 校验、规范化、Adapter 拒绝、reload 失败、watchdog 终止或 close 抢占时整批失败并保留旧 active；无部分修改、混合 generation 或残留 pending listener。恢复/apply 失败只承诺 NekoJS 拥有且 Adapter 已证明可恢复的字段，不承诺回滚任意 Java 对象内部状态、其他 mod、世界、网络或文件副作用。
- [x] snapshot/restore/reapply 状态有唯一 owner 和生命周期归类；root close 清理 root-owned 状态，进程级例外可重复测试，独立测试 root 不互相污染，旧无 owner 的 static `SNAPSHOTS` 旁路按删除条件移除。
- [x] 先 characterization 既有多个脚本、多个声明和注册/事件顺序下的同 Item/Block 同属性修改行为；重构后按同一可观察顺序确定性重放并固定结果，不凭空引入 Dynamic Registry 式同 key 拒绝，也不新增未裁定的 last-write-wins 政策。
- [x] 脚本不再声明某项修改时，成功 reload 后 NekoJS 持有基线先恢复，再应用新的完整修改计划；平台无法证明可恢复的字段不得提交该批，旧 active 保持可用。诊断能区分 active、blocked/recovery-failed 和 restored，不得把静默 stale 当成成功。
- [x] 显式 setter 与 JavaBean-style property assignment 调用同一 setter，并进入同一校验、规范化、definition fingerprint 和候选修改计划；不得用同名 public field 绕过校验，GraalJS runtime contract test 固定两种写法等价。
- [x] 同一 candidate 的修改计划与 global/shared 顶层写集完成联合预检，并联合成功或失败；不得出现领域计划失败而状态半提交，或状态提交而修改计划失败。
- [x] Item/Block 修改后的客户端可见性有真实 fixture：自动同步、显式 resync、需要 relog 或明确 unsupported 均由节点 capability 与错误/提示表达，不出现服务端与客户端长期隐藏不一致。
- [x] 26.x 与 1.21.1 的 item default components、block/state 属性、注册时机和同步差异只存在平台/版本 Adapter；五节点 capability/source-trace 与 smoke 记录实际结果，不自动补 Fabric parity。
- [x] 调用者 Interface、既有 Registry/Event/Adapter owner 契约、runtime member、TS/Python declaration、contract/golden 和迁移表互相追溯；不新增公开 Modification Runtime、第二 registry path 或第二事件框架，测试从脚本事件贯穿到平台 Adapter 可观察结果，不断言私有静态 Map。
- [x] 随实现交付 Item/Block modification、setter/property parity、声明移除后的恢复/阻止提交和不可同步边界的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的能力。
- [ ] 旧 direct live mutation、restore-all 后整体重放、无 owner static snapshot 和不受测 server-only 旁路只能在替代路径 parity、失败保留、迁移表、无消费者证据和维护者确认后删除；不保留长期兼容双路径。【不勾选：维护者删除确认是门禁（Human input note）；替代路径 parity（parity/failure-retention/迁移表）+ 旧 route 无消费者证据 + 结构 guard 已交付，见 baseline REPORT §11/§12；待 sign-off】

## 维护者 sign-off 项（AC14 门禁，待确认）

代码层的旧路径已在 `d2c49f2a` 删除（AC3/AC5 要求：无 owner 的 static 状态与 restore-all 重放必须收口），
但删除的是**公开面符号**，构成 breaking，需维护者知情/追认后才可勾选 AC14 的删除项、才可宣布
「不保留长期兼容双路径」。逐项清单与替代路径见
`baseline/2026-09-16-item-block-modification/MIGRATION.md` §2.1 与同目录 `REPORT.md` §11：

- `ItemModificationEventJS#fire(MinecraftServer)`（static）与其 `ItemModificationEventJS(MinecraftServer)` 构造器
- `BlockModificationEventJS#fire()`（static）与其隐式无参构造器
- `ItemModificationEventJS.SNAPSHOTS` / `BlockModificationEventJS.SNAPSHOTS`（进程级 static 快照 Map）
- `ItemModificationJS#applyTo(DataComponentMap.Builder, DataComponentMap, MinecraftServer)`（包私有引擎接缝）

替代路径：`ModificationDomainOwner#applyInitialPlan(server)`（初始收集点）+ SERVER 事务 reload 的
`ReloadPhase.DOMAIN_PLAN`；无消费者证据：全仓无上述符号调用点（唯一 `fire(` 文本命中为本票清理前的
javadoc）；结构 guard：`Ticket39ModificationOwnershipTest`（真跑）。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [global 共享状态与候选写入规格](../specs/10-shared-global-candidate-writes.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): Item/Block modification wrapper、成员目录、dispatch、side filter 和 declaration 必须复用同一事件面，不得第二 bus。
- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md): snapshot/restore 状态、生命周期入口和 static ownership 总账必须先有唯一 root 归属，避免新增第二 runtime owner。
- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md): 修改计划必须作为 candidate generation 资源收集、失败丢弃并成功 commit，不能提前修改 active live 对象。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md): reload 重入、watchdog 终止和 close 抢占决定候选计划清理、旧 active 保留与显式恢复语义。
- [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md): 同一 candidate 的修改计划必须与 global/shared 顶层写集联合预检、联合成功或联合失败，不能出现领域计划回滚而状态半提交。

## Scope and coordination

**Rationale:** 修改既有 Item/Block 与 Dynamic Registry 注册新定义的事务、snapshot、恢复和客户端可见性语义不同，需要一张窄票垂直穿透事件、candidate、Adapter 和迁移，不能被通用事件 catalog 票或 Dynamic Registry 票隐式覆盖。

**Coordination:**

- [24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md): 票 24 盘点并冻结事件族公开面，并依赖本票提供 modification 行为验收。
- [15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md) 与 [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md): 只共享 setter/property parity 和规范化原则；启动注册、动态新定义、既有对象修改三条生命周期不合并。
- [21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md): 本票不引入多人 Dynamic Registry 同步；若未来 Item/Block modification 需要跨节点同步，必须另开决策与票据。
- [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md): global owner 提供联合预检/成败边界，本票提供领域计划集成 fixture，不把 Item/Block 修改实现塞进 global owner。
- [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md) 与 [36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md): P4 与真实试做必须消费本票能力、恢复和迁移证据。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
