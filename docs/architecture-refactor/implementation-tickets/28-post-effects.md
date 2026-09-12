# 28: PostEffects 声明事件与运行 binding 分离

**What to build:** 客户端脚本通过现有 ClientEvents 的资源/reload 子事件声明 PostEffects register/unregister，候选定义在 preflight 与资源生成完成后按 generation 提交；set、clear、toggle、current 继续作为运行时 binding/Adapter。资源 reload、失败保留、旧 generation 清理、声明 parity 与平台能力从调用者 Interface 到节点 Adapter 可验证。

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

- [ ] register/unregister 只通过现有 ClientEvents 客户端资源/reload 子事件贡献；调用者 Interface、事件成员、payload 和 side filter 在 catalog/golden 中唯一，不新增第二个 PostEffects 事件。
- [ ] candidate 阶段只生成定义、JSON/Shader 资源、generation 标记和 Adapter 请求，不提前挂载生产 listener、激活 post chain 或修改当前客户端画面。
- [ ] 合法 commit 后新 generation 的 PostEffects 定义可回读并生效；旧 generation 的注册、listener 和资源不再接收 callback，释放顺序可观察且不产生双重渲染或半更新。
- [ ] 候选 JSON/Shader 无效、资源生成失败、reload 中断、客户端不可用或 commit 取消时旧 active 资源保持可用，候选资源与临时注册全部清理。
- [ ] set、clear、toggle、current 仍是运行时 binding/Adapter，并保持现有调用者可见行为；这些成员不被声明事件替代、删除或误标为 reload 事务操作。
- [ ] 26.x 与 1.21.1 的 PostChain/Shader JSON 形状、资源路径和 mixin/Adapter 时机有 fixture 与节点 smoke 证明；平台差异不进入 common 作者契约。
- [ ] TS/Python declaration、runtime member、contract/golden 与实际事件/binding 成员一致；EntitySelectors、Assets 和已事件化 render 域不被并入本票或重复声明。
- [ ] Fabric 或旧版本不可用时 capability/source-trace/smoke 显式记录 unavailable 或 partial 并给出确定失败；不用静默 no-op、空画面或无错误返回冒充支持。
- [ ] 旧 PostEffectsJS 静态 register/unregister 直连路径只有在事件资源生命周期、运行 binding parity、迁移表、旧 route 无消费者和维护者确认后删除；运行时操作仍保留在最终 binding 面。
- [ ] 随实现交付声明事件与 set/clear/toggle/current 分工的最小可运行示例和必要迁移材料；示例只使用已通过 gate 的客户端能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ClientEvents 资源/reload 子事件 wrapper、成员目录、dispatch 语义和 catalog/golden 必须消费既有事件面基础，不能新增第二事件。

## Scope and coordination

**Rationale:** PostEffects 的核心风险是把声明生命周期与运行时动作混在一起；单独收口能明确证明 register/unregister 事务化而 set/clear/toggle/current 仍是 binding。

**Coordination:**

- 与 CLIENT_GUI_RENDER owner 并行协调 ClientEvents 与 client Adapter 生命周期；共享事件面不是串行 blocker。
- 与 EVENT_SURFACE owner 确认资源/reload 子事件的 catalog/golden 唯一性。
- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 candidate 资源、commit 与清理顺序。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
