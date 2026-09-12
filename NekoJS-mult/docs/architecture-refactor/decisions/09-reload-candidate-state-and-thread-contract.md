# reload 候选环境、状态所有权与线程边界如何闭合？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: 主 agent + 维护者共同裁决
Blocked by: [运行时所有权、reload 与数据保护的契约是什么？](05-runtime-lifecycle-and-data.md)、[搬运功能如何适配 NekoJS 事件面与运行时扩展？](08-ported-features-event-surface.md)

## Question

决策 05 已规定普通 reload 先构造候选环境，候选完整通过后才切换；失败时保留 NekoJS 自有旧 runtime/state。但当前实现和证据显示 listener、binding、timer、候选 environment、插件产物与共享状态之间仍有提前发布或清理风险。需要在开始 W1/W4 前闭合以下契约：

- candidate session 与 active session 是否完全隔离；generation 如何标识；平台事件、脚本 listener、timer、binding、动态注册计划在 commit 前是否可见；旧/新回调的顺序和并发边界是什么。
- Plugin Runtime、Point/Handle/result、Binding、session、timer/listener、module cache、trust/persistent data、诊断与日志分别由谁拥有；哪些跨 reload 保留，哪些按 generation 失效，shutdown 的 close 顺序是什么。
- SERVER、CLIENT、STARTUP/TEST 和 guest-created thread 的线程亲和性、调度入口、跨线程访问、reload/close 重入和锁顺序是什么；watchdog 终止 Context 后是隔离失败、重建候选还是保留旧 active session。
- 事件化功能（Villager Trades、Dynamic Registry、PostEffects）如何使用同一 candidate/commit/rollback 语义，避免事件 facade 通过 Adapter 提前修改 live 外部对象。

本票只裁定运行时 Interface 的可观察契约，不新增 RuntimeKernel/RuntimeGateway/ServiceLocator，不改变决策 05 已确认的单一 `NekoRuntimeRoot`、普通 reload 不重启 Plugin Runtime 和不承诺撤销外部副作用。

## Resolution

### Candidate、active 与 generation

1. `NekoRuntimeRoot` 仍是唯一 runtime owner；普通 reload 为同一 ScriptType 创建新的 candidate generation，不重启或重新 bootstrap Plugin Runtime。
2. candidate 与 active 完全隔离。commit 前，真实平台 callback、生产 timer、对外 binding 和 live registry mutation 只属于 active generation；candidate 只收集和预验证自己的 session 资源及事件/注册计划。
3. candidate 依次完成 preparation、execution、binding、事件计划、Dynamic Registry/Villager Trades/PostEffects 等域的 preflight 后，才在一个明确 commit 点切换为 active。切换时旧 generation 停止接收新 callback，新 generation 接管，随后按所有权顺序释放旧 session；不得出现旧新双重回调。
4. 失败时关闭 candidate 的全部资源，active generation、其 listener/binding/timer 和 NekoJS-owned state 保持可用；错误至少包含 generation、phase、source location 和 owner/domain。决策 05 的外部副作用不回滚边界保持不变。

### 候选切换状态图（已确认契约的展开，非实现结果）

```text
ACTIVE(N) 保有生产事件路由与自己的资源
    │
    ├─ 发起 reload → CANDIDATE(N+1)：准备源码、执行声明、收集 session 资源/域计划
    │                    ├─ 失败或取消 → 关闭候选；ACTIVE(N) 不变
    │                    └─ 校验及适用域 prepare 完成 → READY(N+1)
    │                                                    │
    └──────────────────── owner-thread 交接点 ────────────┘
                             │
                     新回调路由切到 N+1
                             │
                     N 停止接收新回调并释放
                             │
                         ACTIVE(N+1)

candidate 被 watchdog 终止 → 丢弃 candidate，旧 active 不变
active 被 watchdog 终止    → FAILED；不再向被终止 Context 分发，等待显式 reload
close                     → 停止接受新工作，关闭候选/会话，最后释放 root-owned 资源
```

“旧 active 保持可用”不是两个 Context 在同一 owner thread 上并行执行或零停顿承诺；交接不得与在途回调重叠，具体安全点由平台 Adapter 接线。候选执行可用自己的绑定、收集事件与测试回调，但不能提前挂上生产事件路由或启动生产 timer。

此处的隔离限于 NekoJS 管理的 generation 资源与声明计划，不是对任意 Java 对象、世界或文件的深拷贝沙盒，既有高级 Java 访问和外部副作用边界不变。域 preflight 通过不等于 live mutation 已可安全回滚：Dynamic Registry 等域必须另外证明准备、可见性和失败处理；`prepare/ack` 消息本身不是跨进程原子性的证明，未通过其 gate 不得宣称该能力已实现。

### State ownership

| 状态 | owner / 生命周期 | 普通 reload 行为 |
|---|---|---|
| Plugin Runtime、Point、Contributor、frozen result、Extension Handle | `NekoRuntimeRoot` / Plugin Runtime，进程级 | 不重建；Handle 本身保持有效 |
| Graal Context、prepared module session、script binding、listener、timer | Script Execution Environment，generation 级 | candidate 成功后切换；旧 generation 关闭 |
| Handle 获取的 session object、事件 token、临时注册计划 | 对应 generation/session | 以 generation 校验；旧对象不得静默操作新 session |
| Dynamic Registry active overlay、claim/stale bookkeeping | Registry Runtime + platform/version Adapter | 成功 commit 更新；失败保留旧 active state；stale 不在普通 reload 物理删除 |
| module cache、Probe/manifest 临时结果 | 可重建的 preparation/managed-surface owner | 按 source/definition/generation 失效或重建，不作为 active state 回滚替代物 |
| config、world、pdata、pack、trust-store、用户编辑 workspace/declaration | Data Protection / Pack Trust owner，跨 session/进程 | 不因普通 reload 覆盖或删除 |

### Thread、重入与 watchdog

1. SERVER、CLIENT、STARTUP/TEST 分别由其既定 owner thread 访问；Graal Context、binding、listener 和 timer 不允许被任意外部线程直接触碰。跨线程请求必须排队到对应 owner thread。
2. 同一 ScriptType 的 evaluate、reload、close 串行；reload 不重入，close 优先于新的 reload；平台 callback 在 generation commit 前后按同一序列化入口分派。
3. guest-created thread 不能绕过 owner-thread 规则；它只能使用显式调度入口访问 NekoJS runtime。回调、timer 与 reload 的锁顺序和取消点必须在 W1/W4 fixture 中验证。
4. watchdog 终止 candidate Context 时，candidate 失败并关闭；watchdog 终止 active Context 时只记录隔离失败并保持单一 owner，不自动偷偷创建第二个 active runtime，后续由显式 reload 尝试恢复。

### 线程与重入矩阵（计划约束）

| 请求/阶段 | 归属入口 | 调度与重入约束 |
|---|---|---|
| SERVER eval、脚本回调、reload | Minecraft server owner thread | 同一 ScriptType 串行；commit 不与该 generation 在途回调重叠 |
| CLIENT eval、资源计划、脚本回调 | client/render owner thread | GPU/渲染操作由 client Adapter 在对应安全点执行；不借服务端线程执行 |
| STARTUP | bootstrap/loader 的既定串行执行入口 | 不借普通 reload 重做启动期平台 registry/network 注册 |
| TEST | test runner；涉及 MC 时进入对应平台 owner 入口 | 纯引擎测试与 MC smoke 分开；测试线程不能直接绕过 MC 线程要求 |
| 非 owner 线程请求 NekoJS managed lifecycle | 对应 ScriptType 的调度入口 | 排队，不直接进入 Context；不扩大为禁止高级 Java/线程能力 |
| 回调内部请求 reload | 当前串行生命周期入口 | 不递归开启候选；由实现使用排队/拒绝结果明确呈现，不能同步等待自身队列 |
| close / watchdog | owner 生命周期 + 已有中止控制面 | close 优先于尚未开始的 reload；active 被终止后等待显式恢复，不自动重跑脚本 |

Plugin Runtime 的冻结定义/贡献对象与各 generation 的绑定实例必须区分；`Binding.value()` 返回共享 Java 对象时，不能仅凭“绑定”名称把它当成隔离快照。session 清理不得关闭仍由进程级 Plugin Runtime 持有的共享产物；root shutdown 先停回调与 session，再释放其拥有的插件产物（若有释放契约）。

### 对工作项的约束

- W1 必须验证 candidate/active 可见性、generation 切换、owner-thread 序列化和无双重 callback。
- W2 必须验证进程级 Plugin Handle 与 generation-scoped session object 的失效边界。
- W4 必须验证 candidate 资源释放、失败保留、watchdog 和 reload/close 重入。
- W6/W7 的事件化域只能生成 candidate plan；Dynamic Registry、Villager Trades、PostEffects 的平台 Adapter 不得在 candidate commit 前提前修改 live 外部对象。

本 Resolution 只补齐运行时 Interface 的可观察契约，不新增 RuntimeKernel、RuntimeGateway、ServiceLocator，不改变单一 `NekoRuntimeRoot`、普通 reload 不重启 Plugin Runtime 或外部副作用不回滚的既定边界。

## Evidence

- `common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java`
- `common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java`
- `common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java`
- `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java`
- `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionHandle.java`
- `docs/architecture-refactor/decisions/05-runtime-lifecycle-and-data.md`
- `docs/architecture-refactor/proposal.md`（Execution Environment、Plugin Runtime、Pack Trust/Data Protection Interface）

## Exit criteria

本 Resolution 已留下 candidate/active/generation 状态图、state ownership 表、线程/重入矩阵和 watchdog 恢复策略；W1/W2/W4/W7 必须将其转化为失败保留、资源释放、事件 transaction 和 runtime contract fixture gate。

## 关联已闭合决策（不改变本票已确认结论）

共享绑定不自动变成 generation 私有快照；global 的决策已闭合，完整语义以 [跨 reload 的 global 共享状态如何参与候选事务？](10-shared-global-candidate-writes.md) 的 Resolution 为准。本票的隔离条款不代表全部共享对象均可回滚。
