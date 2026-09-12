# 41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter

**What to build:** 将 40 的 host Adapter contract 接到 NeoForge 26.2 真实客户端：脚本可打开 JSX 描述的独立 Screen，看到 label/button/input/scroll/layout 的首次绘制，并通过稳定事件对象完成点击、文本输入、滚轮、Tab/Shift+Tab、Enter/Space、Escape、焦点、hover、disabled 和基础 narration。正常绘制帧只 paint 已提交 host tree，不重新执行 GraalJS render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

**协调但不硬阻塞：**
- [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)：26 继续拥有 keybind/HUD；41 拥有 JSX Screen 内输入路由。
- [27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)：27 继续拥有既有 error GUI/render callback 域；41 与其确认平台 Adapter 和 client owner 线程边界，不把 JSX runtime 塞入 27。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)：41 先通过公开诊断 seam 报告阶段化错误；与 30 的最终 record 字段对齐在 42 完成整合验收。

## Acceptance criteria

- [ ] NeoForge 26.2 真实客户端能打开、绘制、操作、resize 和关闭 JSX Screen；Screen 选项覆盖标题、关闭行为、暂停策略和外部替换 cleanup。
- [ ] 原生鼠标、滚轮、键盘、文本输入、焦点和捕获被转换为稳定脚本事件；事件对象不暴露原生 Screen、widget、`GuiGraphics`、Java 对象身份或版本特有字段。
- [ ] input 的值、光标、选择、焦点、最大长度和文本事件可用；scroll 正确管理内容范围、裁剪、偏移和滚轮消费。
- [ ] 焦点顺序、Tab/Shift+Tab、Enter/Space、Escape、hover、pressed、disabled、tooltip 和基础 narration 行为与 fake Adapter contract 一致。
- [ ] 首次构建、resize、状态更新和事件回调都不在普通 paint 帧重新执行 JSX render；每帧只绘制已提交 host tree。
- [ ] host Adapter 持有 Minecraft/loader 类型，shared/common author contract 不引入平台类型；脚本侧状态、VNode、host tree 和事件闭包只在 client owner thread 访问。
- [ ] 节点删除后旧事件闭包不再响应；Screen 关闭、外部替换和重复 cleanup 进入同一释放路径。
- [ ] NeoForge 26.2 真实客户端 smoke 使用仓库规定 Minecraft MCP 或等价注册 smoke 通道完成；其它节点只记录 `not verified`，不因 common 编译通过而标记支持。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
