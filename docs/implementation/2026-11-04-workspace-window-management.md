# JSX 工作区窗口管理扩展

## 范围与状态

用户在 2026-11-04 授权扩展现有 workspace：响应式外框、可配置标题栏、边缘／角落拖动缩放、最小化／最大化／还原，以及任务栏、浮动按钮和自定义 JSX 最小化入口。实现、声明、英文内嵌资料、双语 wiki、聚焦回归和最终五节点构建／产物检查均已完成；构建交付时尚未进行实机验收。后续 2026-11-04 至 2026-11-05 的 NeoForge 26.1.2 用户实机逐项反馈见[实机验收记录](2026-11-04-workspace-manual-acceptance.md)，用户于 2026-11-05 明确确认本次已执行范围验收通过，保留已知问题和未测项；其他节点和未覆盖范围仍不视为通过。

保留现有单 host／单 root、owner-thread 发布、候选失败保留已提交状态、模态优先和版本门禁。已有像素边界／裁剪修复不得回退；原有数值 rect 和默认关闭按钮行为保持兼容。没有新增依赖、提交、推送或升版；不自动部署、重启或操作游戏。

## 分工与验证 seam

- 控制器：`createWorkspace`、控制器操作、只读快照和实际 layout；通过真实模块加载器与事务 host 验证状态、失败保留和内容身份。
- 通用布局：现有 JSX root／layout／dispatch；验证尺寸策略、工作区可用范围和标题栏尺寸。
- 原生输入绘制：实际 host 的点击／拖动／释放与真实 GUI extractor，验证捕获、命中、窗口控制按钮及投影；记录 CPU 夹具与真实 GPU／物理输入证据的区别。
- 声明、打包资料和 wiki：源接口生成声明，类型正／负例、可执行示例与双语说明保持一致。

所有 Gradle、节点矩阵、wiki 同步和最终 gate 由 Lead 串行执行；各模块先以一个可执行失败用例确认行为，再做最小实现。

## 已确定的增量契约

- 保留数值 `rect` 作为普通／还原位置；新增 `responsiveRect` 表达 profile／百分比尺寸，不扩大既有快照数值类型。手动移动／缩放以实际普通窗口布局为基准，并退出响应式外框策略。
- 控制器模式为 `normal`／`minimized`／`maximized`，最大化前的位置保留；最小化自最大化状态后还原回最大化。隐藏不改变模式，`show` 保留原有还原／置顶／激活语义，关闭才移除内容。
- `resizable`／`minimizable`／`maximizable` 默认关闭。标题栏字段为 `titlebarHeight`（整数，默认 20，0 隐藏全部标题栏）、`titlebarButtonWidth`（正整数，默认 20）、`titlebarTitle`（默认 true）。
- 原生只发送受控请求：`onResize` 携带工作区局部数值矩形及八方向 edge；`onMinimize`／`onMaximize`／`onRestore` 独立事件。控制器拥有约束与模式，原生不私自修改窗口状态。
- `minimizedPresentation` 默认 `none`，可选 `taskbar`／`floating` 或自定义 JSX 回调；任务栏默认高度 28，以普通根 screen 列布局保留工作区范围。浮动／自定义使用显式 screen stack 覆盖。自定义回调返回一个直接的 `panel` VNode 或 `null`，须显式设置 width／height／anchor，可在内部使用组件；不通过隐藏的全屏包装器拦截窗口输入。显式设置的大尺寸展示区仍会遮挡自身范围内的窗口，这是有意的输入边界。展示区是根 screen 下显式标记的独立 panel；窗口仍只能直接属于 workspace。

## 当前验证记录

- 控制器最小化／还原首轮 RED → GREEN：`minimizingRetainsContentSubscriptionsAndRestoringKeepsIdentity` 原先因 `[NEKO-7013] Unsupported workspace window configuration: minimizable` 失败；最小实现后通过，保留已编辑状态与订阅。最大化／最小化／还原模式链也先出现 `Unsupported workspace window configuration: maximizable`，随后通过；该阶段只证明状态链，实际最大化布局单独验证。
- 通用标题栏 RED → GREEN：原先 `Unsupported prop for window: titlebarHeight`，添加三个严格校验的扁平字段后，32px 标题栏／16px 按钮宽度与内容裁剪用例通过；没有放宽旧校验。
- 响应式／最大化实际布局 RED → GREEN：原先 `Unsupported prop for window: mode`；实现后验证安全区内的百分比／profile、数值约束、微小工作区优先、最大化填满工作区和还原；仅 `root.resize` 不重跑业务组件。控制器缩放／响应式策略物化先出现 `[NEKO-7013] Unsupported workspace window configuration: resizable`，随后与实际标题栏／最大化发布一起通过。首轮完整夹具发现未设置的 maxWidth／maxHeight 被显式放进 VNode，违反既有脚本数据校验；修正为仅在已定义时输出，未放宽 VNode 或尺寸校验。
- 原始窗口控制事件的首次运行被新模块代码触发现有 TS 擦除器的对象展开三元表达式歧义，属于装载失败，不算行为 RED。误将其当作行为失败授权后，事件实现已开始；随后修正模块代码形状，不修改编译器，并保存实现前的已复制 runtime 资源（SHA-256 另见运行记录），在跳过 `processResources` 的隔离验证中确认干净的 `Unsupported prop for window: onResize`。该事件切片是回溯基线证明，不能宣称完整的严格先红后绿顺序；之后使用规范资源流程验证当前实现。
- 共享展示标记／隐藏事件门禁：完整 TS 夹具先遇到重复局部绑定、对象／数组三元表达式擦除歧义及一个误写的 overflow 属性，均为夹具装载／配置失败，不计行为 RED。独立的最小真实 CommonJS 入口最终在保存的实现前 runtime 上确认隐藏非窗口回调错误返回 true（预期 false），规范资源流程中修复后通过；该切片同样属于回溯基线，不能宣称严格先红后绿。原有通用响应式类型及 root 生命周期未扩大修改。
- 任务栏／浮动展示均已有真实控制器 RED → GREEN：任务栏最初出现预期 screen／workspace 根形状差异，之后发现原始恢复按钮缺少显式 id，既有回调注册规则不会为 key 自动注册事件；仅为工厂按钮分配私有且避开窗口 ID 的稳定标识，没有改变通用 JSX 注册规则。浮动展示先出现 `Workspace minimizedPresentation must be none or taskbar`，再实现无工作区预留的有界面板及既有非受控 scroll。末轮规范流程通用 runtime 18、workspace 23、真实打包示例 8，加上声明 golden 2 项，共 51 项通过（0 失败／错误／跳过）。真实 TSX 编译／模块加载执行了手册中的任务栏、浮动及自定义三个初始化版本，验证原有输入、计数器和内容标识；自定义上下文只读、渲染期间变更拒绝、非法面板候选回滚也已覆盖。
- 原生角落缩放、模式发布捕获隔离及按钮控制均有双节点 RED → GREEN：先确认角落无法捕获、最大化／还原发布后旧缩放捕获仍存活、标题栏最大化没有改变模式；最小实现后 NeoForge 26.1.2／26.2.0 各 12 项框线／受控输入绘制用例通过。捕获在发布时按模式／能力失效，不能先最大化再还原后继续旧手势；正常缩放保持原始相反边与已聚焦输入。原生按钮只发送请求，状态仍由控制器拥有。原生展示区 wheel／键盘／遮挡／模态路由另由真实原生 host 的展示区夹具覆盖，不能据此宣称 GPU 或物理输入验收。
- 类型消费者 RED → GREEN：原有生成声明缺少 `minimize`／`maximize`／`restore`／`resize` 和快照 `mode`；规范声明提取先确认两个预期差异，再生成并复核。新增只读 profile 负例出现 `TS2578: Unused @ts-expect-error directive` 后，仅新增窗口专用 `NekoUiWindowResponsive<T>` 的只读 profiles 类型，保留原有通用 `NekoUiResponsive<T>` 不变；再次规范生成后，完整 Probe 类型链通过。
- 末轮有界审阅发现两个控制器缺陷并先确认 RED：隐藏普通窗口仅修改宽度时，高度错误由 100 变为 20；100×80 的小视口中，内置浮动栏顶部错误为 -80，首个恢复项不可达。修复仅回退到隐藏窗口的普通数值矩形，并让内置浮动栏经现有 fill／max 尺寸分配器限制在安全区内；不改变自定义显式大面板的有意遮挡语义。两项已在规范 51 项聚焦流程中通过，完整 `:common:check` 也已通过。

- 原生末轮三项边界修复：最右侧标题栏按钮曾被右边缘缩放带抢占，现仅在没有命中真实控制按钮时捕获缩放，保留内容区边缘缩放。激活窗口的回调同步发布新展示面板时，原点击曾继续触发底层窗口按钮；两个节点均先确认面板已真实发布且窗口回调由预期 0 变为 1，再在既有激活后重新检查模态／展示区边界，不将原点击转发给新面板。空白展示区覆盖底层按钮时，真实 GUI extractor 曾仍排入底层 tooltip；两个节点均先通过面板边界、原有 tooltip 和未覆盖区域断言，再确认覆盖点本应 null 却得到 Window tooltip，修复为向遮挡窗口后代传递不可命中的鼠标坐标。初次激活夹具缺少必填 title 引发装载错误，不计行为 RED；补齐后才记录上述双节点干净 RED。所有测试在最小实现阶段保持不变。
- 最终原生聚焦验证：NeoForge 26.1.2／26.2.0 各 frame 12、chrome boundary 3、presentation 3、hover 1，共 38 项通过，0 失败／错误／跳过。hover 夹具记录真实 Screen.extractRenderState → GuiGraphicsExtractor.setTooltipForNextFrame 调用，不包含真实 GPU。

## 交付检查

接口、实现、声明生成及差异审阅、完整 common gate、五节点构建、源路径／产物审计、wiki 严格检查及最终差异审阅已完成。逐项 red/green 的真实顺序和回溯基线例外见上文，不将夹具装载错误计为行为失败。声明 golden 的有意变化为新 workspace 模块、窗口能力／事件及只读 profile 契约；inspector 基线包含新内置模块声明，均按规范生成流程审阅，没有为 Java 版本漂移或失败门禁修改预期。

| 检查 | 命令／流程 | 最终结果 |
| --- | --- | --- |
| Common 完整门禁与双产物 | `:common:check :common-api-processor:test :common:assemble :common:authoringStdioJar` | 2733 项，369 suites，0 失败／错误，4 跳过；common 隔离检查通过。 |
| 原生聚焦回归 | 两个 NeoForge 26.x 节点运行 frame／boundary／presentation／hover 四类 | 各 19 项，共 38 项，0 失败／错误／跳过。 |
| 五节点完整构建 | `:1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` | 全部通过；普通 test 数分别为 472／787／787／438／438，跳过 33／85／85／39／39，0 失败／错误；独立 platformGateTest 通过。 |
| 配套门禁 | `guardLint`；三个 NeoForge 节点 `nbtSmokeTest`；`npm run test:probe-types` | 全部通过。Gradle 使用 Zulu 25 daemon，保留原有测试 toolchain，不切换测试 Java 来更新 golden。 |
| 源路径与产物 | 五次 `tools/nekojs-ci-gates.py source-roots --node <node>`，随后 `all --out build/nekojs-gates-report.json`；双 common 和五节点产物逐项审计 | 四类 CI gate 全通过；七个产物各含 36 个与规范源内容／SHA-256 一致的 AI payload。bundleId 为 `1f7917f523fc5724b2a1a1b6696736740052d51904e07b31410dd9c3f8ed7554`；standalone 无 Minecraft 依赖，新增原生 chrome 类只出现在两个 NeoForge 26.x 产物中。 |
| Wiki 与最终差异 | `wiki-docs.mjs sync`、`stamp --topic jsx-client-ui`、`check --strict`；wiki 工具测试；代码围栏语法／锚点／证据检查；`git diff --check` | 30 双语主题 0 问题；8 工具测试通过；60 页 292 语法块 0 失败，60 页既有锚点序列保留、234 证据目标有效；同步幂等；差异检查通过，仅既有换行提示。30 项人工审阅仍为 pending。 |

本次构建交付阶段尚无实机、真实 GPU／物理输入及维护者人工结论；后续用户执行的实机检查及剩余范围见上述实机验收记录，不沿用前轮边框修复作为本轮证据。保留既有平台边界：原生 JSX workspace 仅 NeoForge 26.x，其他节点构建通过不表示它们获得该功能。没有提交、推送、升版、部署、重启或操作游戏。
