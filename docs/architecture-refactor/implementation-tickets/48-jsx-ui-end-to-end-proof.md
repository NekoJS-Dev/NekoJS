# 48: JSX UI feature end-to-end proof 与证据包

**What to build:** 用一个受控网页资料样例完成 AI-assisted 转换到 NeoForge 26.2 JSX Screen 的端到端 proof：结构、状态、输入、滚动、资源、响应式 Profile、Inspector、差异修正、reload、错误保留和 cleanup 全链路可验证，并采集 feature 性能与能力证据。该票关闭 JSX UI feature acceptance，不自动成为既有 1.2.0 / P4 发布 blocker。

**Blocked by:**
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)
- [42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线](42-jsx-ui-generation-reload-cleanup.md)
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)
- [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md)
- [47: AI UI Authoring Contract 与转换 Cookbook](47-jsx-ui-ai-authoring-docs.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] 代表性网页输入资料经过 46/47 的 AI-assisted 流程生成 JSX、状态、资源引用和 conversion report；报告记录假设、unsupported 项和人工处理点。
- [ ] NeoForge 26.2 真实客户端能打开、操作、resize、reload、触发资源/脚本错误并关闭样例 Screen；六个 Profile 均有测量输出，必要时有截图辅助。
- [ ] Inspector 差异驱动至少一次有记录的局部修正，最终结构、交互、响应式和视觉等价结果可追溯；明确列出不能逐像素一致的原因。
- [ ] reload 成功/失败、旧 Screen 关闭、旧事件失效、active UI 保留、资源缺失和 render/event 异常均进入证据包。
- [ ] 记录首次构建、首次布局、增量 reconcile、列表更新、输入、持续 paint、profile 切换和 cleanup 的测试环境与统计口径；未取得数据前不设立发布阻断数字。
- [ ] NeoForge 26.2 capability 只按真实 Adapter/smoke 结果记录；其它节点保持 `not verified` 或按证据标记 `supported` / `partial` / `unavailable`。
- [ ] 不修改 34–37 的既有 Blocked by；若维护者决定纳入某个发布，另行发布范围决策并更新对应 gate。

## Dependency rationale

- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线](42-jsx-ui-generation-reload-cleanup.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [47: AI UI Authoring Contract 与转换 Cookbook](47-jsx-ui-ai-authoring-docs.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
