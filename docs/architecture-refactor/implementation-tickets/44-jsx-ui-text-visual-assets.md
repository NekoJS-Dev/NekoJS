# 44: 文本测量、视觉样式、图片与受控资源解析

**What to build:** 补齐网页等价视觉所需的第一批受控能力：Minecraft Font Adapter 文本测量与换行、文本颜色和层级、背景、边框、圆角、透明度、图片/图标、裁剪与资源状态。图片/图标/字体通过受控资源标识进入 UI contract，复用既有 Assets/resource pack 根与安全边界，缺失资源产生可定位诊断而不是任意路径或 URL 访问。

**Blocked by:**
- [29: Assets/Lang 资源生成与回读收口](29-assets.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)

**Status:** in-review（实现/测试/证据已交付；AC4 加载/解码两类失败诊断与 AC6 真实客户端 smoke 未勾选，待纹理管线与 Minecraft MCP 环境；非维护者签收）

**Assignee:** workbuddy-kimi-44（main-session agent；mult worktree）

**Claim record:** 2026-09-25 由 main-session agent 认领；分支 mult / worktree 主仓。用户指令「继续来一张」（沿用 @skill:implement 领票流程）构成源码实施的另行授权。Blocker 偏差记录（同票 46/42 先例）：29/40/41 三张先决票均已交付、处于 in-review 未 closed；其实现已在主线可用，本票消费其已落地输出，不等待维护者 sign-off。预计改动范围：票 44 已随 1136f30a 批次落地的视觉/文本/资源实现的验证、补缺、测试与票据记账。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] 文本测量、换行、截断、baseline、颜色、字号和字体层级由平台 Font Adapter 提供；common 只消费测量结果，不猜 Minecraft 字体宽度。——证据：`api/ui/FontAdapter`（stringWidth/lineHeight/ascent）为 common 唯一测量入口；`TextLayouter`（贪心换行、段落、省略截断、`layoutScaled` 字号层级缩放）只消费 adapter 值；guest 运行时经 host `measureText` 契约取数并校验合法性（jsx-runtime.ts `textMetrics`）；host 侧 `McFontAdapter` 包 Minecraft `Font`（两 26.x 节点同源），`PainterJS.textWidth/wrapText/lineHeight` 亦委托同一 adapter。测试：`TextLayouterTest`、`Ticket44HandoffSmokeTest`(a/a'/b/g)、`PainterJSTextMembersTest`。
- [x] background、border、radius、opacity、image、icon、crop 和资源规格进入受控 props；不支持任意 CSS 字符串、浏览器 URL、文件句柄、Canvas/WebGL 对象或原生纹理长期进入脚本状态。——证据：`VisualStyleResolver` 白名单解析 12 个受控 prop；`UiColor` 仅接受 ARGB/RGB int、`#RGB/#RRGGBB/#AARRGGBB` 与 CSS 基础命名色（CSS 函数不可入）；`UiResourceId` 仅接受 `namespace:path` 受控语法（小写、禁 `..`、至多一个 `:`），URL/文件路径/句柄无入口。TS 侧 `jsx-primitive-props.tsx` probe 以 `@ts-expect-error` 钉住错误 prop 放置；review 修复后 opacity 对显式色与兜底色均生效（`applyOpacity`，测试 (h)）。
- [x] 图片/图标/字体资源标识复用 29 的资源根、路径校验、pack reload 和回读语义；不新增第二资源根、第二事件或第二资源 policy。——证据：`DiskPackUiResourceResolver` 构造于 `NekoJSPaths.get().root()`（`<gameDir>/nekojs`），解析 `assets/<ns>/textures|fonts` 子树，与票 29 `AssetGeneratorJS`（`NekoJSPaths.assets()`）同一物理根；id 语法与票 29 写入侧一致；无第二事件/policy。限制已诚实记录：解析为磁盘级（`Files.isRegularFile`），内存态 pack reload 不在此层观察，vanilla 资源栈在纹理 blit 接线（version owner 后续）后接管可用性；`Ticket44ResourceIdParityTest` 钉住与 vanilla `ResourceLocation` 的接受/拒绝一致性。
- [ ] 资源缺失、非法标识、加载失败、尺寸非法和解码失败进入统一诊断 seam，并可定位到 UI root、节点、资源和 generation。——部分满足未勾选：缺失（NEKO-6004）、非法标识（NEKO-6003）、尺寸非法（NEKO-6006，crop）三类已进 30 的统一 seam（`resolveVisual → ScriptErrorReporter.recordCallbackError`，Location 含 rootId/nodeType/nodeKey/resourceId/generation，证据测试 `Ticket44HandoffSmokeTest`(c/d/e)）；加载失败（NEKO-6005）与解码失败（NEKO-6007）按 Error-Reference 标注为 reserved 未发射——纹理加载/解码管线随 image blit 一并留待 version owner 接线，届时两条码已有契约位。
- [x] 视觉属性模型不得与 43 的 profile 覆盖模型冲突；二者组合后的 resize 布局与绘制结果由 45 统一验收。——证据（无冲突部分）：guest 侧 `resolvedProps → resolveResponsiveValue` 按当前 profile 把 `NekoUiResponsive<T>` 解析为具体值后才进入布局/冻结，`VisualStyleResolver` 只见标量，`fontSize: NekoUiResponsive<number>` 组合不产生误报诊断；组合验收按 AC 文本归属票 45。
- [ ] 至少一个真实 NeoForge 26.2 Screen smoke 覆盖多行文本、字体层级、图片/图标、透明度、裁剪和资源缺失诊断。——未勾选：本会话无 Minecraft MCP/真实客户端环境（同票 41/42 口径）。Minecraft-free seam `Ticket44HandoffSmokeTest` 已把六类场景钉为可执行契约（多行换行、字号层级、图片在/缺+NEKO-6004、透明度组合、crop、截断），缺真实客户端执行；另 image 绘制当前为占位框（纹理 blit 未接线，见 `JsxHostAdapter.paintNode` image 分支注释），真实 smoke 需与 blit 接线一同进行。

## Delivery record（2026-09-25, workbuddy-kimi-44）

- 认领：见上方 Claim record；实现主体此前已随 `1136f30a`（42/44/45/47 批次）落地，本票完成验证、review 补缺、测试与记账。
- 逐条 AC 验证通过（AC1/AC2/AC3/AC5 勾选，AC4/AC6 部分满足未勾选，理由见各条）。
- 双轴 code review（Standards + Spec 子代理）发现并已修复 3 项：
  1. **opacity 语义缺陷**：原 `argb(color, withOpacity(spec, fallback))` 在显式设色时静默丢弃 opacity；改为 `applyOpacity(spec, argb(color, fallback))`，opacity 对显式色（含自带 alpha）与兜底色一致生效，覆盖 panel/scroll/screen 填充与描边、image 占位、label 文本；新增 `Ticket44HandoffSmokeTest`(h) 钉住组合语义。
  2. **`DiskPackUiResourceResolver` Javadoc 与实现不符**：参数契约实为 pack 根 `<gameDir>/nekojs`（解析时自行拼 `assets/`），原文写成 `nekojs/assets`；已改文档并把字段/参数改名 `packRoot`，同时把「平台叠加 vanilla 资源管理器」的夸大表述改为诚实的磁盘级语义。
  3. **`VisualStyleResolver` 10 处诊断构造重复**：提取 `report(...)` helper，行为不变。
- review 记录不改项及理由：`ponytail:` 前缀为维护者既有惯例（票 42 同口径）；TS/Java 双侧 crop 形状校验为 guest fail-fast + host 权威的有意分层（TS 注释已声明）；`resolveFont` 已实现但绘制侧未选字——属「字体经受控 id 进入 contract」的契约面，行为有测试，管线接线随 blit 一并后续；`layoutScaled` ceil 语义可能溢出至多 scale px，Javadoc 已声明。
- 验证命令与结果：
  - 焦点：`./gradlew :common:test --tests "com.tkisor.nekojs.api.ui.*" :26.2.0:test --tests "com.tkisor.nekojs.client.ui.*" --tests "com.tkisor.nekojs.wrapper.client.*" :26.1.2:test --tests "com.tkisor.nekojs.client.ui.*" --tests "com.tkisor.nekojs.wrapper.client.*" :1.21.1:test --tests "com.tkisor.nekojs.ui.*"` → BUILD SUCCESSFUL；common api.ui 46/0，26.2.0 UI 25/0 + painter 3/0，26.1.2 UI 25/0 + painter 3/0，1.21.1 parity 3/0。
  - 全套（修复后无过滤器）：`:common:test :26.1.2:test :26.2.0:test :1.21.1:test :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava` → BUILD SUCCESSFUL；common 1928/4 skipped/0 失败，26.1.2 与 26.2.0 各 438/58/0（比修复前 +1 为新增 opacity 组合测试），1.21.1 313/14/0，两 fabric 节点编译通过。
- 已知限制（交付边界）：真实 NeoForge 26.2 客户端 smoke 未运行（无 Minecraft MCP）；image/icon 绘制为占位框，纹理 blit 与 NEKO-6005/6007 发射随管线留待 version owner；磁盘级资源解析不观察内存态 pack reload。

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
