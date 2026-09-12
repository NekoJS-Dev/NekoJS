# 42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线

**What to build:** 把 JSX UI root 纳入唯一 `NekoRuntimeRoot` 与 CLIENT generation 生命周期：candidate 中准备的 UI 计划和 root 对生产 Screen、输入路由不可见；commit 后旧 generation 停止接收事件并关闭旧 Screen；candidate 失败或取消只清理候选资源并保留 active UI。render/layout/event/host/resource 错误进入既有诊断 record，旧 handle 与过期 generation 明确失败。

**Blocked by:**
- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] UI root 创建时绑定当前 CLIENT generation；不新增 UI global singleton、静态跨 generation registry 或第二 runtime owner。
- [ ] candidate UI 计划、binding、signal 订阅、事件 token 和 host 计划在 commit 前对生产 Screen/输入路由不可见；candidate 失败全部清理且 active Screen 继续可用。
- [ ] commit 后旧 generation 停止接收新事件，取消 pending reconcile，释放订阅、host node、原生 widget、输入 token 和 Screen handle；新 generation 恰好接收一次事件。
- [ ] reload 成功、reload 失败、Screen 主动关闭、外部 `setScreen` 替换、客户端退出和 close 抢占的 cleanup 幂等；旧 handle 不能操作新 generation。
- [ ] 非-owner-thread 状态更新只能显式排队或明确失败；回调内 invalidate/close、关闭期间输入和 watchdog/cancel 路径可观察。
- [ ] render、component、layout、event、host Adapter、resource 和 disposed/stale root 错误进入 30 的统一诊断链路，包含阶段、脚本来源、UI root、generation 和 owner；不建立 UI 专用错误事实源。
- [ ] 真实 NeoForge 26.2 smoke 覆盖成功 reload、失败 reload、旧 Screen 关闭、旧事件失效和 active UI 保留。

## Dependency rationale

- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
