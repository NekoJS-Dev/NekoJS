# 24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径

**What to build:** 脚本作者继续使用既有 Block、Item、Level、Player、Command、Capability、Goal 和 Entity 事件族完成订阅、修改、取消和清理；其中 Item/Block modification 的 candidate/snapshot 语义由票 39 负责，本票负责事件族完整盘点与公开面一致性。事件公开名、payload、side、priority/cancel、平台差异和 TS/Python declaration 由 managed catalog/contract 固定，不新增事件或万能 Event Module。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 盘点现有 BlockEvents、ItemEvents、LevelEvents、PlayerEvents、CommandEvents、CapabilityEvents、GoalEvents、EntityEvents 及 entity/living wrapper 的公开成员，形成真实 catalog snapshot 差异，不逐 symbol 开票。
- 为 block、item、level、player、command、capability、goal、实体生命周期/伤害/死亡/掉落/生成行为各选 representative caller path，验证注册、payload、修改、取消、优先级和 side。
- 把 wrapper 公开名与 payload 纳入 managed contract/golden，原生 NeoForge/Fabric callback和 mixin留在平台 Adapter 并记录 source trace。
- 验证并发/多次 reload 后 listener 清理、无重复 dispatch、取消结果和线程/时机语义。
- 补 TS/Python declaration parity、错误阶段与逐节点 capability matrix/source trace/smoke。
- 收缩 gate：capability/goal/entity 旧 wrapper、binding 或声明旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；公开事件功能不删除，清理随本票完成而不是 final release 统一处理。

## Acceptance criteria

- [ ] Block、Item、Level、Player、Command、Capability、Goal、Entity 的公开事件族在真实 catalog snapshot 中没有遗漏家族；新增/删除公开成员会产生 contract diff 而不是静默 drift。
- [ ] 每个事件族至少有一条 caller-to-result fixture；`CommandEvents.register` 与 `CommandEvents.command` 不与管理命令票混Owner，Item/Block modification 只验证事件面接线，事务与 snapshot 语义以票 39 证据为准。
- [ ] 每个 representative path 从脚本 listener 注册到平台 callback、wrapper payload、执行或取消结果可追踪。
- [ ] priority、cancel、返回值修改和多次订阅行为符合既有语义，并发 stress 后无重复 dispatch或半清理状态。
- [ ] server/client side 过滤、mixed side 与 loader/version capability 显式记录；不可用能力不静默 no-op。
- [ ] 实体加入/离开、伤害、死亡、掉落、finalize spawn 等行为至少各有一条 caller-to-result fixture，覆盖当前公开家族代表。
- [ ] capability/goal 事件不与启动 registry、交易或客户端实现 owner混淆；本票只冻结事件面和 Adapter 交界。
- [ ] TS/Python declaration 与 runtime member/payload一致，普通测试不更新 golden。
- [ ] NeoForge/Fabric source trace 和 runtime smoke 按支持等级记录，不能用反射清单替代。
- [ ] 不新增平行事件、Extension Point或第二注册路径。
- [ ] 旧 wrapper/binding/声明旁路只有在替代 behavior、declaration、trace 通过且无调用者后移除；公开 capability/goal/entity 事件功能不删除，清理不推迟 final release。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): 全部 gameplay/platform wrapper 需要复用同一事件 contract、bus 清理和 declaration 链；直接逐 symbol 补测试会留下第二套事件规范。
- [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md): Item/Block modification 的 candidate、live mutation、snapshot 与恢复语义必须先由专项票收口；本票不得用 catalog 清单冒充该行为验收。

## Scope and coordination

**Rationale:** 按用户补充，把 capability/goal/entity 行为放入一个足够窄的既有事件域票，用家族 representative path 加 catalog diff 防漏，不为每个 symbol开票，也不造新事件。

**Coordination:**

- RUNTIME_ROOT/RELOAD_COMMIT: listener generation、失败保留和清理语义与 runtime 组共同验证。
- REGISTRY_STARTUP: capability registry/type 事实源如涉及启动注册，由 registry 组负责；本票不迁移 registry mutation。
- client 域拥有者: render/client-only 实体或 UI 相关平台实现不由本票重写，只保留事件公开面边界。
- BUILD_BASELINE: 当前 catalog 与事件 wrapper 行为先作 characterization，再冻结有意承诺。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
