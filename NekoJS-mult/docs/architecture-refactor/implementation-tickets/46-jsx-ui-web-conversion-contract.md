# 46: AI-assisted 网页转换映射与输入契约

**What to build:** 定义 AI 辅助把受控网页资料转换为 NekoJS JSX 的映射契约：输入检查清单、HTML 结构到 host primitive 的映射、支持 CSS 子集到 NekoJS 布局/视觉属性的映射、事件与状态映射、资源引用规则、不确定性记录和 unsupported 诊断。交付代表性 fixture 与 conversion report；不承诺通用自动 HTML/CSS 编译器、运行时 parser 或一键像素级复刻。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [ ] 转换输入契约明确要求结构、样式、资源、字体、交互说明和六个 profile 的参考尺寸/截图；缺失资料产生显式 uncertainty，不由 AI 静默编造。
- [ ] 常见结构、flex row/column、gap、padding/margin、尺寸约束、align/justify、overflow/scroll、背景、边框、圆角、透明度、字体层级、图片和基础表单控件有确定映射。
- [ ] hover、focus、active、disabled、loading 转换为显式 signal/store 或受控 host state；网页事件转换为 NekoJS 稳定事件对象，不携带 DOM Event、事件冒泡或浏览器默认行为。
- [ ] 支持 CSS Grid、复杂 selector、伪元素、动画、脚本 DOM 操作、iframe/video/canvas/WebGL 和第三方框架生命周期时必须输出 unsupported/needs-human 诊断，不得静默丢弃或伪装成功。
- [ ] 转换产物是人工可读、可修改的 JSX/TSX、函数组件、signal/store 和 conversion report；不得生成 React/Preact/DOM API、React Hooks、任意 Java host 或未受控资源访问。
- [ ] representative fixture 的输入、映射决策、unsupported 项、产出和 Inspector 验证结果可追溯；若实现辅助命令，它只是显式工具，不进入 runtime 热路径，也不承诺完整浏览器兼容。
- [ ] 不新增 HTML parser、CSS parser、DOM、浏览器布局引擎或自动网页抓取/远程资源代理承诺。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
