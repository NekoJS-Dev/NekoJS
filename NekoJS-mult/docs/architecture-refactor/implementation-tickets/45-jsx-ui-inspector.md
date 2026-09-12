# 45: UI Inspector、布局测量与截图差异基线

**What to build:** 提供 JSX UI 的公开 Inspector contract 与 fake/NeoForge 双输出：当前 profile、逻辑视口、安全区域、节点树、矩形、裁剪、最终解析样式、溢出、资源状态、事件绑定摘要和 layout diagnostics；支持采集 actual 截图并与参考图/参考尺寸输出差异报告。Inspector 是 runtime 事实工具，不依赖 AI 文档、网页转换映射或自动转换器。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] fake Adapter 与 NeoForge 26.2 均可输出同构 Inspector record；字段来自公开 host contract，不暴露 Java 对象身份、原生 widget、`GuiGraphics` 或内部 reconciler 结构。
- [ ] Inspector 能定位节点、最终 props、resolved style、profile、矩形、裁剪、滚动偏移、焦点、事件绑定摘要、资源引用和错误阶段。
- [ ] 六个 Viewport Profile 都能生成稳定测量输出；输出可作为 golden，普通测试只读，更新必须走旧新 diff 与维护者审阅。
- [ ] actual 截图或等价像素 evidence 只作为辅助，必须与公开测量/行为断言配合，不把截图对象或私有渲染缓冲作为脚本 API。
- [ ] 差异报告能指出偏差最大的节点/属性，并保留输入、环境、profile、参考图和实际输出来源。
- [ ] 45 的实现与验收不读取、不依赖 46/47 的 AI 文档或网页转换规则；后续 AI 工作流只消费 45 输出。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
