# 22: Villager Trades 声明事件与稳定查询

**What to build:** 服务器脚本通过现有 ServerEvents 的数据/reload 子事件声明村民交易；第一版只提供 add 贡献和绑定 generation/stale 的稳定只读 query。事件面负责收集与预验证，实际 registry mutation 只由 26.x/1.21.1 平台/版本 Adapter 在合法 commit 点执行；失败保留旧交易，Fabric unavailable 显式可见。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W7

## Acceptance criteria

- [ ] 调用者只通过现有 ServerEvents 数据/reload 子事件贡献 trade；事件、payload、成员和 side 过滤在 catalog/golden 中恰好出现一次，不新增第二事件 bus。
- [ ] 第一版公开面只有 add 和稳定 query；remove、replace、modify 不出现在调用者 Interface、golden、TS/Python declaration、示例或能力承诺中。
- [ ] query 返回只读快照并绑定 generation/stale 校验，不暴露 live registry view、可变 Manager 状态或旧 generation 可写对象；成功 reload 后新 generation 可读，缺失声明给出确定 stale/retired 结果。
- [ ] 事件收集阶段只形成候选 overlay 和 Adapter 请求；未知 trade set、无效配置、事件失败或 Adapter 拒绝时不发生部分 mutation，不留下 pending 脏数据，旧 active 交易仍可用。
- [ ] 合法 commit 点由平台/版本 Adapter 执行 registry epoch 与 mutation；26.x 与 1.21.1 的注册时机、trade set 映射和错误结果由真实节点测试证明，共享事件面不包含 loader surgery。
- [ ] reload 中断、close、candidate watchdog、事件收集失败或 Adapter commit 拒绝会清理候选 listener/计划并保留旧 active；旧 generation token 的后续查询与写入有明确失败结果，不产生双重提交。
- [ ] 脚本不再声明的既有 trade 不在普通 reload 中物理删除，只进入 stale/retired 记录；后续查询、诊断和迁移说明能看到该状态。
- [ ] 第一版 Villager Trades 不定义 server/client 同步协议，也不把多人 registry 同步语义混入本票；任何未来同步需求必须另开决策与票据。
- [ ] Fabric Villager Trades 的 unavailable 通过 capability/source-trace/smoke 显式验证为明确拒绝或不可用，不用无错误 no-op 冒充；NeoForge 节点 supported/partial 只按实际测试证据记录。
- [ ] 旧 VillagerTradesJS 静态 add/pendingCount、全局 Manager 暂存和直连 registry surgery 只能在事件+Adapter+query parity、迁移表、旧 route 无消费者和维护者确认后删除；删除后不保留长期兼容 shim。
- [ ] 随实现交付 add/query、generation/stale 查询与 Fabric 明确不可用的最小可运行示例和必要迁移材料；示例只使用已通过 gate 的节点能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ServerEvents 数据/reload 子事件 wrapper、成员目录、dispatch 语义和 catalog/golden 必须消费既有事件面基础，不能新增第二 bus。

## Scope and coordination

**Rationale:** Villager Trades 可以独立收敛为一个很窄的 add+query 垂直路径；把 remove/replace/modify 和通用 registry 事务塞入同一票会暴露尚无回滚语义的公开写操作。

**Coordination:**

- 与 EVENT_SURFACE owner 复用 ServerEvents 现有数据/reload 子事件与 bus/golden 规则；共享事件文件不构成本票阻塞。
- 与 RELOAD_COMMIT owner 对齐 server candidate preflight、commit 点和失败保留；与 GLOBAL_STATE owner 无直接依赖，脚本状态写集只作联合失败协调。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
