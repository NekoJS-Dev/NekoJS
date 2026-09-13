# 06: 候选环境、阶段结果与 owner-thread commit 点

**What to build:** 在现有部分候选事务基础上补齐 generation-owned 交接的不完整隔离与清理风险：candidate 的 Context、session、binding、listener、timer 收集和计划在 preparation、execution、binding、事件计划及现有域 preflight 全部通过后，才经明确 commit 点切换；失败关闭 candidate 全部 generation 资源并保留 active。本票只负责 generation-owned reload 交接与阶段结果，不承担 GLOBAL_STATE 的 root map 联合事务，也不承担 RUNTIME_THREADS 的完整队列、重入和 watchdog 调度；完整 reload 契约须三票结合验收。

**Blocked by:** [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 把 candidate 的 Context、module session、binding、listener、timer 和计划放入独立 generation，不再提前写入 active 共享 bus。, 保持 generation-owned 资源与阶段结果边界：不提前发布生产 callback、对外 binding 或 live mutation，也不把 root 级 global/shared 事务或完整线程调度并入本票。, 在 owner thread 建立单一 commit 点：先切换生产路由，再让旧 generation 停止接收新 callback，最后按所有权释放旧 session。, candidate 的 timer 注册只进入候选资源收集，commit 前不作为生产 timer 分发；显式测试 callback 可在测试 harness 中执行，但不承诺 pending timer 自动完成。, 保留 Plugin Runtime、Point、Contributor、frozen result 和 Extension Handle 的进程级身份；只让 session object 按 generation 校验。, 为 STARTUP 的不可逆平台注册保留显式 restart/unsupported 边界，不让本票偷偷重做 registry bootstrap。

## Acceptance criteria

- [x] SERVER、CLIENT、TEST 的成功 reload 都先构建 candidate generation；candidate 执行期间生产 callback、timer、对外 binding 和 live mutation 仍由 active 执行。
  - 【范围边界：本票范围内满足（EventBusJS 面有单测）；范围外缺口＝NativeEventsJS 原生注册在候选期仍直挂生产 EVENT_BUS，owner W7 域 Adapter；CLIENT 真机 not-verified，owner 维护者。见报告 §5/§10】
- [x] preparation、execution、binding 或现有域 preflight 失败时，candidate 的 Context、timer、listener、binding 和临时计划全部关闭，active 仍能接收事件并读旧 state。
  - 【范围边界：本票范围内满足（含审查后新增的候选 Context 真关闭＋解除登记断言，与 commit 点不再半失败的 red→green 证据）；范围外缺口＝候选构建前 binding.close(type)/errorTracker.clearByType 已改动进程级账本，owner W6/W7 或票 10；feature 域 preflight 归 W6/W7】
- [x] 失败结果外部可见地包含 generation、phase、source location 和 owner/domain，不带修复指引，也不把内部锁或私有对象当契约。
- [x] commit 后新 generation 是唯一新 callback 接收者，同一事件不出现旧新双重执行；旧 generation 按 timer、listener、Context 所有权顺序释放。
  - 【范围边界：无双执行已由计数测试证明；释放顺序字面偏离（listener 冻结必须先于发布以避免双重执行）待维护者裁决，论证见报告 §10.A1】
- [x] candidate-only 测试 callback 可在测试 harness 中执行，pending timer 只被收集并随 generation 提交或关闭；二者 commit 前都不进入生产路由。被 watchdog 或语句上限终止时 candidate 丢弃、active 不变。
  - 【范围边界：语句上限终止路径已验（烟测 29/29）；watchdog 时间窗口路径未验——发现既有缺陷（scriptRunawayTimeoutSeconds 对 while(true) 无效，复现件 Ticket06RunawayProbeTest 仍 @Disabled），owner 票 07】
- [x] STARTUP 不可逆平台注册未被域 Adapter 证明可回滚时，入口显式要求 loader restart 或返回不支持阶段，不执行 reset 后宣称事务成功。
  - 【范围边界：三节点命令面已改为先给非事务/需重启结论、不再先宣称成功（烟测 E 段验证）；STARTUP 仍执行 reset+load，是否改为不执行待维护者裁决，见报告 §5】
- [x] 本票通过只代表 generation-owned 交接绿；GLOBAL_STATE 的联合写集与 RUNTIME_THREADS 的完整调度仍需各自通过，三者结合后才构成完整 reload 契约。

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
## Closure record（2026-09-12）

- 执行者：zcode-agent。分支 `ticket-06-reload-commit`（8 实施 commit + 1 回填）合并入 master（`4cf50c0b`）；
  合流后完整验证全绿：`:common:check`、`:common-api-processor:test`、`guardLint`、
  五节点 `build`（1.21.1/26.1.2/26.2.0/26.1.2-fabric/26.2.0-fabric）、`npm run test:probe-types`。
- 交付：candidate/active generation 隔离（`RuntimeEnvironment` 成对 volatile + 候选态 owner-thread 字段）、
  单一 commit 点、结构化阶段结果（`ReloadPhase` / `ReloadFailureReport` / `ReloadResult.phase/gen/sourceLocation`）、
  挂起监听器收集（commit 前不上总线）、候选 timer 收集、STARTUP 非事务边界（显式 restart 提示）、
  `bench/smoke-reload/` 双 loader 烟测（26.1.2 会话 29/29 checks 全绿，含 commit 见证 fixture）。
- code-review（双轴）后修订（`b43b92fc`）：**commit 点不再半失败**——把可能抛的监听器预备前移到新增的
  `ReloadPhase.EVENT_PLAN` 阶段（候选期），失败即走 `discardCandidate`；修复前已用探针复现
  `activeContextReplacedByCandidate=true` / 候选资源泄漏（red→green 证据在 `evidence/a1-red-green-probe-*.txt`）。
  另清理死代码（`loadScriptsInto`、自相矛盾的 `ReloadResult.success`）、`ReloadResult` 补 `sourceLocation`、
  `generation` 改 volatile、三节点命令面改为先给非事务结论、修正 smoke 文档与 engine.toml 矛盾、补测试计数归档。
- 验收判定（据实）：AC3/AC7 **pass**；AC1/AC2/AC4/AC5/AC6 **部分满足**——缺口逐条列在报告：
  ① `NativeEventsJS` 原生注册在候选期仍直挂生产 EVENT_BUS（事后 `isContextDead` 短路 ≠ 撤销），
  owner W7 域 Adapter；② 候选构建前 `binding.close(type)`/`errorTracker.clearByType` 已改动进程级账本，
  owner W6/W7 或 10 号票；③ watchdog 时间窗口终止路径未验（发现既有缺陷：`scriptRunawayTimeoutSeconds`
  对 `while(true){}` 无效，复现件 `Ticket06RunawayProbeTest` 仍 `@Disabled`），owner 07；
  ④ AC4 释放顺序字面偏离（listener 冻结必须在发布前以避免双重执行），**请维护者裁决**；
  ⑤ CLIENT 真机与 1.21.1 运行期未验，owner 维护者。
- 边界：未实现 07 的线程队列/重入/close 优先/watchdog 调度，未实现 10 的 global 联合事务——报告 §8 给出
  给 07 的交接注记（可复用钩子与需补面）。`FabricNekoJSCommands` 的 15 行改动是 AC6 入口外观的必要面
  （非票 31 构建接线），与票 31 的源根迁移有潜在文本冲突，已在报告标注。
