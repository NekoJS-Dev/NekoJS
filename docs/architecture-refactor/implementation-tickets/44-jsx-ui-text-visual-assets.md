# 44: 文本测量、视觉样式、图片与受控资源解析

**What to build:** 补齐网页等价视觉所需的第一批受控能力：Minecraft Font Adapter 文本测量与换行、文本颜色和层级、背景、边框、圆角、透明度、图片/图标、裁剪与资源状态。图片/图标/字体通过受控资源标识进入 UI contract，复用既有 Assets/resource pack 根与安全边界，缺失资源产生可定位诊断而不是任意路径或 URL 访问。

**Blocked by:**
- [29: Assets/Lang 资源生成与回读收口](29-assets.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] 文本测量、换行、截断、baseline、颜色、字号和字体层级由平台 Font Adapter 提供；common 只消费测量结果，不猜 Minecraft 字体宽度。
- [ ] background、border、radius、opacity、image、icon、crop 和资源规格进入受控 props；不支持任意 CSS 字符串、浏览器 URL、文件句柄、Canvas/WebGL 对象或原生纹理长期进入脚本状态。
- [ ] 图片/图标/字体资源标识复用 29 的资源根、路径校验、pack reload 和回读语义；不新增第二资源根、第二事件或第二资源 policy。
- [ ] 资源缺失、非法标识、加载失败、尺寸非法和解码失败进入统一诊断 seam，并可定位到 UI root、节点、资源和 generation。
- [ ] 视觉属性模型不得与 43 的 profile 覆盖模型冲突；二者组合后的 resize 布局与绘制结果由 45 统一验收。
- [ ] 至少一个真实 NeoForge 26.2 Screen smoke 覆盖多行文本、字体层级、图片/图标、透明度、裁剪和资源缺失诊断。

## Dependency rationale

- [29: Assets/Lang 资源生成与回读收口](29-assets.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
