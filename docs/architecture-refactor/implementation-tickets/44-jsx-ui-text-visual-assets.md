# 44: 文本测量、视觉样式、图片与受控资源解析

**What to build:** 补齐网页等价视觉所需的第一批受控能力：Minecraft Font Adapter 文本测量与换行、文本颜色和层级、背景、边框、圆角、透明度、图片/图标、裁剪与资源状态。图片/图标/字体通过受控资源标识进入 UI contract，复用既有 Assets/resource pack 根与安全边界，缺失资源产生可定位诊断而不是任意路径或 URL 访问。

**Blocked by:**
- [29: Assets/Lang 资源生成与回读收口](29-assets.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)

**Status:** in-review（纹理/圆角/alpha 组合及真实资源修复回读已补证据；AC3 custom font 与维护者整体结论仍待收口）

**Assignee:** workbuddy-kimi-44（main-session agent；mult worktree）

**Claim record:** 2026-09-25 由 main-session agent 认领；分支 mult / worktree 主仓。用户指令「继续来一张」（沿用 @skill:implement 领票流程）构成源码实施的另行授权。Blocker 偏差记录（同票 46/42 先例）：29/40/41 三张先决票均已交付、处于 in-review 未 closed；其实现已在主线可用，本票消费其已落地输出，不等待维护者 sign-off。预计改动范围：票 44 已随 1136f30a 批次落地的视觉/文本/资源实现的验证、补缺、测试与票据记账。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] 文本测量、换行、截断、baseline、颜色、字号和字体层级由平台 Font Adapter 提供；common 只消费测量结果，不猜 Minecraft 字体宽度。——证据：`api/ui/FontAdapter`（stringWidth/lineHeight/ascent）为 common 唯一测量入口；`TextLayouter`（贪心换行、段落、省略截断、`layoutScaled` 字号层级缩放）只消费 adapter 值；guest 运行时经 host `measureText` 契约取数并校验合法性（jsx-runtime.ts `textMetrics`）；host 侧 `McFontAdapter` 包 Minecraft `Font`（两 26.x 节点同源），`PainterJS.textWidth/wrapText/lineHeight` 亦委托同一 adapter。测试：`TextLayouterTest`、`Ticket44HandoffSmokeTest`(a/a'/b/g)、`PainterJSTextMembersTest`。
- [x] background、border、radius、opacity、image、icon、crop 和资源规格进入受控 props；不支持任意 CSS 字符串、浏览器 URL、文件句柄、Canvas/WebGL 对象或原生纹理长期进入脚本状态。——证据：`VisualStyleResolver` 白名单解析 12 个受控 prop；`UiColor` 仅接受 ARGB/RGB int、`#RGB/#RRGGBB/#AARRGGBB` 与 CSS 基础命名色（CSS 函数不可入）；`UiResourceId` 仅接受 `namespace:path` 受控语法（小写、禁 `..`、至多一个 `:`），URL/文件路径/句柄无入口。TS 侧 `jsx-primitive-props.tsx` probe 以 `@ts-expect-error` 钉住错误 prop 放置；review 修复后 opacity 对显式色与兜底色均生效（`applyOpacity`，测试 (h)）。
- [x] 图片/图标/字体资源标识复用 29 的资源根、路径校验、pack reload 和回读语义；不新增第二资源根、第二事件或第二资源 policy。——2026-10-05 round4 已通过真实 ResourceManager/native provider codec 路径验证 custom font selection、provider load failure、missing/corrupt fallback、invalidate 后修复回读和客户端资源 reload；`Ticket44TextureLoadingTest` 覆盖缓存与恢复，证据见 [round4 UI evidence](../evidence/2026-10-05-round4-ui/README.md)。不宣称任意未支持 provider 的兼容性。
- [x] 资源缺失、非法标识、加载失败、尺寸非法和解码失败进入统一诊断 seam，并可定位到 UI root、节点、资源和 generation。——2026-10-05：真实 ResourceManager → bounded PNG read → native decode/upload → root-owned texture slot 路径已接通；NEKO-6004/6005/6007/6006 由 `MinecraftUiResourceResolver` 与 `UiTextureBlitPlan` 发射，原始 cause 保留并进入 `ScriptErrorReporter`。`Ticket44TextureLoadingTest` 覆盖缓存、失败准备、inspect、提交后释放、清理异常汇总、加载/解码/上传失败及定位字段；首帧 root id 随布局 envelope 传入。真实 MCP 客户端已观察缺失与损坏 PNG，仍可点击健康按钮；加载/上传失败由实际 ResourceManager/可控 backend 回归覆盖。证据见 [texture integration pack](../evidence/2026-10-05-ticket44-textures/README.md)。
- [x] 视觉属性模型不得与 43 的 profile 覆盖模型冲突；二者组合后的 resize 布局与绘制结果由 45 统一验收。——证据（无冲突部分）：guest 侧 `resolvedProps → resolveResponsiveValue` 按当前 profile 把 `NekoUiResponsive<T>` 解析为具体值后才进入布局/冻结，`VisualStyleResolver` 只见标量，`fontSize: NekoUiResponsive<number>` 组合不产生误报诊断；组合验收按 AC 文本归属票 45。
- [x] 至少一个真实 NeoForge 26.2 Screen smoke 覆盖多行文本、字体层级、图片/图标、透明度、裁剪和资源缺失诊断。——2026-10-05：维护者通过选项确认真实 `ticket44-visual-flow.tsx` 的石头纹理、半透明裁剪石头、纸图标、18/9 字号差异和两行文本，点击健康按钮后正常 Esc 关闭；截图与诊断/回调日志保存于 [texture integration pack](../evidence/2026-10-05-ticket44-textures/README.md)。原 jar、fixture 已恢复，独立损坏资源已删除。此项不替代完整 radius/custom font 与资源 reload 修复后回读验收。

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

## Delivery follow-up (2026-10-05, round 2)

- [evidence pack](../evidence/2026-10-05-round2-jsx/README.md) 保存真实 radius/borderWidth/opacity 与修复资源 smoke。背景/边框以 prepared spans 绘制，圆角按 box 尺寸裁剪，边框先 source-over 背景再应用 element opacity，避免透明边框挖空或重复 alpha。七项实际 Screen 输出回归覆盖原生 Adapter 接线、字面圆角像素、边框厚度、clip、透明/半透明组合与极端尺寸；两 NeoForge 节点均通过。
- 维护者通过选项确认方角/圆角/半透明圆角正常；图片由损坏占位修为绿色白十字，F3+T 后实际显示，并能点击健康按钮与 Esc。日志保存 ResourceManager/CLIENT reload、重建 Screen renderCount=1 与健康回调。它证明新 generation 回读，不证明同 root 自动修复；Inspector 长文本被日志截断，因此 RESOLVED status 不作独立日志断言。
- Standards 1 项、Spec 2 项 P2 审查发现均已用公开回归修复，复核无剩余 scoped findings。完整 common/processor/五节点测试、构建、guard 与 Probe 检查通过。两测试客户端停止，独立 jar/fixture/PNG 已删除，原入口哈希一致。
- Radius、custom font selection 和资源 reload/recovery 已通过 focused/native/real-client evidence；automatic narrator output 和维护者 whole-ticket conclusion 仍按下方边界记录，整体保持 `in-review`。

## Delivery follow-up (2026-10-05, round 4)

- [round 4 evidence](../evidence/2026-10-05-round4-ui/README.md) adds controlled `label.font` selection. Common retains authored responsive selectors for measurement/Inspector, while `VisualSpec.font` normalizes one `.json` suffix for native `FontDescription.Resource`; default three-argument measurement hosts remain compatible.
- NeoForge host measurement and cached painting use one selected native `Style`; bounded `font/<id>.json` definitions are validated through the native provider codec. Missing definitions report `NEKO-6004`; malformed definitions report `NEKO-6007` with causes. Singular native `font/` semantics are now documented and tested; provider load success is not inferred from file presence.
- Native tests cover selected width parity, `.json` aliases including logical ids ending `.json`, default fallback, separate Inspector font status and canonical root auto-layout. Full common/processor/five-node matrix, builds and `guardLint` pass.
- Real NeoForge 26.2 smoke observed default/custom bitmap font width and size differences, missing/corrupt fallback diagnostics and healthy callback/tooltip. The bitmap fixture was corrected once after maintainer feedback; final font confirmation passed. Automatic narrator output was not accepted and remains unverified.

AC3 custom font behavior is now technically evidenced but whole-ticket status remains `in-review` pending robust provider compatibility and maintainer whole-ticket conclusion.

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
