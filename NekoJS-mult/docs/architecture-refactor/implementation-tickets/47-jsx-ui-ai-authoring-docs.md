# 47: AI UI Authoring Contract 与转换 Cookbook

**What to build:** 交付面向 AI 的 UI authoring 文档：以已发布 declaration/Probe 输出、primitive catalog、真实事件对象、signal/store 语义、Profile 规则、资源规则和 Inspector record 为事实源，说明如何生成、自检、诊断和局部修正 JSX。文档包含网页转换 cookbook、禁令、输入清单、输出结构、unsupported 报告格式和 Inspector 修正协议。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)
- [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] 文档中的每个 primitive、props、事件字段、状态 API、资源标识和 capability 均引用或生成自已冻结 managed declaration / Probe 输出；不存在仅存在于文档的 aspirational API。
- [ ] 每个 primitive 条目包含用途、children 规则、props 类型、默认值、布局/视觉行为、事件对象、profile 行为、常见错误和最小示例。
- [ ] AI 自检清单覆盖未知标签、未知 CSS 能力、非法资源、错误线程、每帧 render、key、焦点、滚动、文本可读性、reload 残留 callback 和 conversion uncertainty。
- [ ] Inspector 修正协议要求基于 profile、节点矩形、裁剪、resolved style、资源状态和差异报告做局部修正；禁止每次偏差重写整棵 UI。
- [ ] 文档明确优先级为结构等价、交互等价、响应式等价、视觉等价，并说明 Minecraft 与浏览器字体/栅格化差异导致不承诺逐像素一致。
- [ ] 文档示例可被 typecheck/contract fixture 执行或验证；示例不使用 unavailable/not verified 能力。
- [ ] 文档不把 AI 辅助转换描述为自动浏览器兼容层、通用编译器或无需验收的一次性生成。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
