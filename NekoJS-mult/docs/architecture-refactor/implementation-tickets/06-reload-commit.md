# 06: 候选环境、阶段结果与 owner-thread commit 点

**What to build:** 在现有部分候选事务基础上补齐 generation-owned 交接的不完整隔离与清理风险：candidate 的 Context、session、binding、listener、timer 收集和计划在 preparation、execution、binding、事件计划及现有域 preflight 全部通过后，才经明确 commit 点切换；失败关闭 candidate 全部 generation 资源并保留 active。本票只负责 generation-owned reload 交接与阶段结果，不承担 GLOBAL_STATE 的 root map 联合事务，也不承担 RUNTIME_THREADS 的完整队列、重入和 watchdog 调度；完整 reload 契约须三票结合验收。

**Blocked by:** [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 把 candidate 的 Context、module session、binding、listener、timer 和计划放入独立 generation，不再提前写入 active 共享 bus。, 保持 generation-owned 资源与阶段结果边界：不提前发布生产 callback、对外 binding 或 live mutation，也不把 root 级 global/shared 事务或完整线程调度并入本票。, 在 owner thread 建立单一 commit 点：先切换生产路由，再让旧 generation 停止接收新 callback，最后按所有权释放旧 session。, candidate 的 timer 注册只进入候选资源收集，commit 前不作为生产 timer 分发；显式测试 callback 可在测试 harness 中执行，但不承诺 pending timer 自动完成。, 保留 Plugin Runtime、Point、Contributor、frozen result 和 Extension Handle 的进程级身份；只让 session object 按 generation 校验。, 为 STARTUP 的不可逆平台注册保留显式 restart/unsupported 边界，不让本票偷偷重做 registry bootstrap。

## Acceptance criteria

- [ ] SERVER、CLIENT、TEST 的成功 reload 都先构建 candidate generation；candidate 执行期间生产 callback、timer、对外 binding 和 live mutation 仍由 active 执行。
- [ ] preparation、execution、binding 或现有域 preflight 失败时，candidate 的 Context、timer、listener、binding 和临时计划全部关闭，active 仍能接收事件并读旧 state。
- [ ] 失败结果外部可见地包含 generation、phase、source location 和 owner/domain，不带修复指引，也不把内部锁或私有对象当契约。
- [ ] commit 后新 generation 是唯一新 callback 接收者，同一事件不出现旧新双重执行；旧 generation 按 timer、listener、Context 所有权顺序释放。
- [ ] candidate-only 测试 callback 可在测试 harness 中执行，pending timer 只被收集并随 generation 提交或关闭；二者 commit 前都不进入生产路由。被 watchdog 或语句上限终止时 candidate 丢弃、active 不变。
- [ ] STARTUP 不可逆平台注册未被域 Adapter 证明可回滚时，入口显式要求 loader restart 或返回不支持阶段，不执行 reset 后宣称事务成功。
- [ ] 本票通过只代表 generation-owned 交接绿；GLOBAL_STATE 的联合写集与 RUNTIME_THREADS 的完整调度仍需各自通过，三者结合后才构成完整 reload 契约。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md): candidate 提交点必须挂在唯一 root 生命周期入口上；若先改旧 static/root 旁路会形成第二实现。

## Scope and coordination

- **Rationale:** 这是与全局状态、插件有效性和网络 stale packet 直接共享的核心窄路径；不包含线程调度、global 写集或任何 feature 域，但足以用现有脚本和资源外部观察独立闭合。
- **Coordination:**
  - registry/events/client feature 域只能作为 candidate plan 消费者接入，不反向阻塞本票。
  - DynamicRegistry 多人协议若需要 candidate 状态输入，只协调真实协议字段，不让本票依赖整个 feature。
  - 生命周期诊断只输出阶段与错误结果；独立 telemetry、workspace 和 dashboard 归 language-surface 组。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
