# 07: 同类型串行、close 优先与 watchdog 隔离恢复

**What to build:** 在 candidate/active 交接之上实现同一 ScriptType 的 owner-thread 生命周期调度：evaluate、reload、close 串行，reload 不重入，close 优先，非 owner 请求排队，回调内 reload 明确排队或拒绝；watchdog 终止 candidate 时保留 active，终止 active 时进入隔离失败并等待显式 reload，不自动创建第二个 active。

**Blocked by:** [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)

**Status:** closed

**Assignee:** zcode-agent

**Takeover note（2026-09-15）:** 曾由 opencode-agent 认领（其认领编辑未提交），静止约 29 小时、分支零提交；经维护者（用户）批准由 zcode-agent 接管。其未提交半成品已原样快照至分支 `wip/opencode-ticket07-snapshot-20260915`，可随时恢复或参考。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 为 SERVER、CLIENT、STARTUP/TEST 复用既有 owner 入口并建立窄调度面，不把 Graal Context 暴露给任意外部线程。, 定义同类型请求队列、回调内 reload 的排队/拒绝结果、close 抢占规则和取消点。, 隔离 candidate watchdog 与 active watchdog 的状态转移：candidate failure retain、active failed awaiting explicit reload。, 确保 guest-created thread 只能通过显式调度入口访问 managed runtime，同时不收紧既有高级 Java/thread 能力。, 用并发 fixture 与 watchdog fixture 验证锁顺序、无自等待死锁、无双重 callback 和清理幂等。

## Acceptance criteria

- [x] 同一 ScriptType 的 evaluate、reload、close 请求按单一序列执行；并发 reload 只有一个 candidate，另一个排队或得到明确拒绝结果。
- [x] 回调内部请求 reload 不会递归进入自身队列或同步等待自身完成，外部可观察结果是排队成功或明确拒绝。
- [x] close 对尚未开始的 reload 优先，对在途 candidate 先取消并关闭候选，再关闭 active session 与 root 资源。
- [x] 非 owner 线程请求 managed lifecycle 时进入对应 owner 队列，不直接触碰 Context、binding、listener 或 timer。【范围边界：common 层语义满足——scheduleOnOwner 显式入口 + 实例锁串行队列；既有生产入口的平台 owner 转投保持（ClientReloadExecutor/网络 receiver hop）；通用平台 dispatcher 注入未做，G2 记录在案待维护者裁决是否立票】
- [x] watchdog 终止 candidate 时 active 事件、timer、binding 和 state 不变；再次显式 reload 可创建新 candidate。
- [x] watchdog 终止 active 后进入隔离失败状态，停止向被杀 Context 分发，不自动创建第二 active；显式 reload 是唯一恢复入口。【审查后加固：旧 generation 残留闭包的 kill 上报不再误隔离健康 active（staleKillReport 回归测试 + 对照组）】
- [x] guest-created thread 的高级 Java 能力不被收紧，但访问 managed lifecycle 必须走显式调度入口。【边界如实：guest JS 真实创建/启动/join Java Thread 的 interop 能力由 fixture 锁定；平台事实——单线程 Context 下 guest 线程本就不能重入执行 JS 闭包（票 07 之前即如此，非本票收紧）】
- [x] 并发/重入/watchdog fixture 覆盖 SERVER 与 CLIENT owner，至少一个非 owner 线程和 close 抢占路径；旧 Context 私有锁路线在通过后删除。【分发点 synchronized(context) 已删（Graal 拒绝探针实证）；manager 侧 synchronized(ctx) 作为 flushReadyNodeTimers 与 teardown 的资源所有权互斥面保留（REPORT §2.4 论证）；残余角落 G3 记录】

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


## Closure record（2026-09-15）

- 执行者：zcode-agent（自停滞的 opencode-agent 认领接管，维护者批准；其未提交半成品原样保全于
  分支 wip/opencode-ticket07-snapshot-20260915，思路采纳、实现独立重写）。实施区间
  47f4a444..772ef466（分支 6 commit + 审查整改 2 commit + merge）。
- 交付物：ScriptLifecycleGate（同类型生命周期判决面：EXECUTED/REJECTED_REENTRANT/
  REJECTED_CLOSING/REJECTED_CLOSED、ThreadLocal 回调深度、锁前 close 标志、activeFailed 隔离）、
  SyncEvalWatchdog（求值段墙钟守卫 + Context.interrupt，scriptRunawayTimeoutSeconds 对
  while(true) 真实生效并转正票 06 时间窗探针）、ScriptManager 四入口过门 + requestReload/
  scheduleOnOwner 显式调度 + close 三取消点（domain=close-preempted）、EventBusJS 四分发点
  monitor 删除、WORLD 包 DefaultErrorTracker.record IAE 最小修复（不再掩掉 kill）。
- 双轴审查后整改：①markContextKilled 补 active 匹配判定（旧 generation 残留 kill 不得误隔离
  健康 active，AC6 反向防线 + 回归测试）；②guest 线程能力测试由恒真断言重写为真实 interop 断言
  （平台事实注记见 AC7 批注）；③REPORT 补票面 AC 映射（§8）。
- 测试：:common:check 195 suites/1449 tests 0 失败（含 Ticket07 13 用例/Gate 11/Watchdog 探针 5/
  Ticket06 时间窗探针转正/ErrorTracker 7）；:common-api-processor:test、guardLint、五节点
  build、npm run test:probe-types 全绿；普通测试零 golden 写入。
- 遗留（owner 已列，REPORT §5）：G1 回调内失控循环为墙钟盲区→票 19/后续 hardening；G2 通用平台
  owner dispatcher→维护者裁决；G3 monitor 删除残余角落（契约违反者由 Graal 拒绝呈现）→记录；
  G4 WORLD 包 ScriptExecutor:48 requirePath IAE→票 19；G5 interrupt 5s 上限/宿主阻塞等待→已知
  边界；G6 in-game smoke 未跑→维护者/minecraft-mcp 可选补证；G7 precommit 账本位次→票 10/W6/W7。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
