# 21: Dynamic Registry 多人 prepare/ack/commit 门禁

**What to build:** 在本地 inert 定义计划之上完成服务器与客户端的 Dynamic Registry 批事务：preflight、同 key 指纹冲突、服务端 prepare、客户端 prepare/ack、受控 commit、失败/取消保留旧 active、ID/sync/registry surgery 的 Adapter 边界与按节点声明能力的证据。prepare/ack 只是协议阶段，不宣称分布式原子提交；未通过同步与 reload gate 时不公开不安全热更新。

**Blocked by:** [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W6
- W7

## Acceptance criteria

- [ ] 一批动态注册按 preflight、同 key fingerprint/冲突检测、服务端 prepare、客户端 prepare/ack、commit 的顺序执行；每个阶段的外部结果、generation、owner/domain 和错误来源可观察。
- [ ] 任一阶段失败、候选脚本失败、watchdog 终止、close 抢占或同步无法完成时整批不提交；候选计划丢弃并清理，旧 active state 继续服务，无部分注册、半成功 ID 或混合代际。
- [ ] prepare/ack 消息在契约、测试和文档中只表示协议方向与事务阶段，不证明跨进程分布式原子性；成功断言必须观察所有被激活节点的最终一致可见性或明确的拒绝/降级结果。
- [ ] 同步未完成的客户端不激活新代际，不保留 server-only 多人暴露路径；断线、重连、迟到 ack、重复 ack 和客户端不可用都有确定结果，不出现静默 no-op。
- [ ] 数值 ID、网络 payload、registry surgery、claim、cleanup 和平台差异只由 Registry Runtime 与平台/版本 Adapter 执行；事件 facade 不做反射或直接修改 registry 内部结构。
- [ ] commit 前 candidate 对生产 callback、对外 binding、live registry 和其他节点不可见；commit 后旧 generation 不再接收新计划，新 generation 只执行一次，旧资源按所有权顺序释放。
- [ ] 同 key 冲突、缺失声明 stale/retired、普通 reload 不物理删除的行为与本地票集成后仍成立；未来 replace/update 未实现时不得静默覆盖旧 active。
- [ ] contract/golden、TS/Python declaration、transaction/reload/delete-cleanup fixture、capability/source-trace 和跨节点 runtime smoke 只对通过目标 Adapter、事务与同步 gate 的既有候选类型作出结论；未验证类型记录 not verified 并阻塞公开开放，不因缺测改写为 unavailable。
- [ ] 旧 unsafe live mutation、静态 DynamicRegistry 全局入口和不安全 server-only 路径只有在批事务、失败回滚、迁移表和旧 route 无消费者全部闭合并获维护者确认后才能删除。

- [ ] 本票只将已通过类型事务/同步 gate 的 16 号候选计划 fixture 转为生产最小示例与迁移材料；清楚说明启动期与动态注册的区别、失败保留和同 key changed definition 限制；16 的关闭不反向依赖这些激活验收。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [global 共享状态与候选写入规格](../specs/10-shared-global-candidate-writes.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md): 多人事务只能提交已经由 typed Builder、fingerprint、preflight、冲突和 stale 语义稳定生成的 generation-scoped Adapter 请求。
- [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md): 客户端 prepare/ack、连接生命周期、重试/断线和 payload 传输由 network owner 提供；该基础不得反向依赖 Dynamic Registry，避免形成依赖环。
- [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md): 一次脚本候选的动态注册失败必须与受管 global/shared 写集交接一致，验收依赖已闭合的联合写集行为。

## Scope and coordination

**Rationale:** 多人可见性、网络确认和 commit gate 是独立于 Builder 本地语义的风险最高的垂直切片；拆开后可分别用本地 Adapter 测试和跨节点事务 smoke 验收，避免把 prepare/ack 误当原子性证明。

**Coordination:**

- 与 NETWORK_SYNC owner 固定 payload 语义、连接生命周期与重试边界；network 基础只依赖 runtime root，不依赖 Dynamic Registry。
- 与 GLOBAL_STATE owner 协调同一 candidate 中注册计划与受管 global/shared 写集的联合成败；GLOBAL_STATE 是本票硬 blocker，协调仅覆盖接口细节和联调输入，不把 Dynamic Registry 实现转移给 global owner，也不允许绕过联合提交验收。
- 与 EVENT_SURFACE owner 确认动态事件在 catalog/golden 中只有一条 bus，且 side filter 不把 SERVER 事件泄漏给 CLIENT。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
