# 07: 同类型串行、close 优先与 watchdog 隔离恢复

**What to build:** 在 candidate/active 交接之上实现同一 ScriptType 的 owner-thread 生命周期调度：evaluate、reload、close 串行，reload 不重入，close 优先，非 owner 请求排队，回调内 reload 明确排队或拒绝；watchdog 终止 candidate 时保留 active，终止 active 时进入隔离失败并等待显式 reload，不自动创建第二个 active。

**Blocked by:** [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 为 SERVER、CLIENT、STARTUP/TEST 复用既有 owner 入口并建立窄调度面，不把 Graal Context 暴露给任意外部线程。, 定义同类型请求队列、回调内 reload 的排队/拒绝结果、close 抢占规则和取消点。, 隔离 candidate watchdog 与 active watchdog 的状态转移：candidate failure retain、active failed awaiting explicit reload。, 确保 guest-created thread 只能通过显式调度入口访问 managed runtime，同时不收紧既有高级 Java/thread 能力。, 用并发 fixture 与 watchdog fixture 验证锁顺序、无自等待死锁、无双重 callback 和清理幂等。

## Acceptance criteria

- [ ] 同一 ScriptType 的 evaluate、reload、close 请求按单一序列执行；并发 reload 只有一个 candidate，另一个排队或得到明确拒绝结果。
- [ ] 回调内部请求 reload 不会递归进入自身队列或同步等待自身完成，外部可观察结果是排队成功或明确拒绝。
- [ ] close 对尚未开始的 reload 优先，对在途 candidate 先取消并关闭候选，再关闭 active session 与 root 资源。
- [ ] 非 owner 线程请求 managed lifecycle 时进入对应 owner 队列，不直接触碰 Context、binding、listener 或 timer。
- [ ] watchdog 终止 candidate 时 active 事件、timer、binding 和 state 不变；再次显式 reload 可创建新 candidate。
- [ ] watchdog 终止 active 后进入隔离失败状态，停止向被杀 Context 分发，不自动创建第二 active；显式 reload 是唯一恢复入口。
- [ ] guest-created thread 的高级 Java 能力不被收紧，但访问 managed lifecycle 必须走显式调度入口。
- [ ] 并发/重入/watchdog fixture 覆盖 SERVER 与 CLIENT owner，至少一个非 owner 线程和 close 抢占路径；旧 Context 私有锁路线在通过后删除。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md): 串行化和 close/watchdog 状态机围绕 generation commit 点定义；先做调度会保护错误的先清 active 行为。

## Scope and coordination

- **Rationale:** 线程与 watchdog 是 reload commit 的独立可验状态机，单独成票避免把 W1/W4 合成巨块，也不需要语言管线或 feature 域输入。
- **Coordination:**
  - PERF_BASELINE 可并行采样调度耗时；本票只承诺正确性，不设置性能阈值。
  - 网络 receiver 与 DynamicRegistry 平台 Adapter 复用调度入口，不改出第二队列。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
