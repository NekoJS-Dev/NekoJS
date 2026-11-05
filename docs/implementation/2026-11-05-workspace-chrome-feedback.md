# Workspace 边框与按钮悬停反馈

## 范围与方案

用户要求缩放边框提供 Windows 风格指针或高亮，关闭、最小化、最大化／还原按钮单独悬停高亮。保持现有点击、模态、展示面板遮挡、owner-thread 与 generation 生命周期契约，不新增脚本 API 或依赖。

使用两个 NeoForge 26.x 节点均有的 `GuiGraphicsExtractor.requestCursor` 和 Minecraft 自有 `CursorTypes`。左右边使用 EW，上下边使用 NS，四角使用 ALL 并高亮对应两条边。当前 Minecraft 不提供对角缩放 CursorTypes；不另分配缺少公开释放接口的原生 cursor handle。每帧按实际命中请求，不保存跨 Screen 的系统指针状态。

关闭悬停红色，其他标题栏按钮使用浅色背景。边框反馈在窗口内容之后绘制，使用与实际点击相同的按钮优先和边框命中规则。模态、展示面板、上层窗口及禁用／隐藏状态阻止底层反馈；缩放捕获期间维持原方向，捕获取消后停止。

## 进度与验证

- 已阅读 CONTEXT、编码规范、原生 host／chrome／workspace 输入绘制与现有测试。
- 新增真实 Screen render extraction 回归，覆盖八方向、三个按钮、还原、遮挡和捕获。
- 首轮 fixture 使用了不受公开 VNode 支持的 window.disabled，属于装载失败，不计行为 RED；改为直接验证原生层已有禁用防护，不扩大公开 API。
- 26.1.2 干净行为 RED：17 项，17 assertion failures、0 errors／skips，缺少 cursor 请求和按钮高亮。26.2.0 同样为 17 assertion failures、0 errors／skips；两次均在生产改动前运行。
- 实现与自动验证已完成；2026-11-05 用户已逐项确认本次 26.1.2 边框指针／高亮与标题栏按钮悬停通过，范围与未测项见文末。前轮 14 组人工验收不替代本次反馈。

## 最终实现与契约

- `JsxWorkspaceScope.chromeAt` 使用既有模态、展示面板、窗口顺序和祖先可用性规则选择反馈窗口，允许未激活但可点击的窗口悬停，不让被遮挡的窗口漏出反馈。
- `JsxWindowChrome.controlAt` 同时检查横纵坐标；`resizeEdge` 共享实际按钮优先规则。未改变既有按钮尺寸、4px 缩放命中带或能力默认值。
- Host 每帧向真实 `GuiGraphicsExtractor` 请求 Minecraft 自有 cursor，未分配额外句柄、注册监听或保存全局指针状态。边框用 2px 浅蓝标记且在子内容之后绘制；关闭按钮悬停为红色，其余控件浅色。
- 事件、Script API、生成声明、generation 归属、支持节点和物品／网络权限均未扩展。仅 NeoForge 26.1.2／26.2.0 提供此原生功能。
- 英文内嵌手册和双语 wiki 已同步；资料 manifest 经已有 `generateAuthoringManifest` 流程重新生成，没有升版。

## 自动验证结果

Gradle daemon 使用 `-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25`，保留既有测试工具链与 golden。

| 检查 | 命令／流程 | 结果 |
| --- | --- | --- |
| 新增悬停回归 | 两节点 `test --tests com.tkisor.nekojs.client.ui.NativeWindowChromeHoverTest` | 最终各 20 项、0 失败／错误／跳过；原始 17 项先在两节点确认行为 RED，再完成实现，之后补充区域／取消／退出边界用例。 |
| 全部原生 UI 回归 | 两节点 `test --tests 'com.tkisor.nekojs.client.ui.*'` | 各 187 项、0 失败／错误、2 跳过。 |
| Common 与双产物 | `:common:check :common-api-processor:test :common:assemble :common:authoringStdioJar` | 通过；common 2733 项、0 失败／错误、4 跳过，包含隔离检查。 |
| 五节点完整构建 | `:1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` | 全部通过，含 platformGateTest；普通测试分别 472／807／807／438／438，0 失败／错误，跳过 33／85／85／39／39。 |
| 守卫、源路径与 CI | `guardLint`；五节点 `python tools/nekojs-ci-gates.py source-roots --node <node>`；`python tools/nekojs-ci-gates.py all --out common/build/chrome-feedback-ci-gates.json` | 通过，五节点源路径和四类 CI gate 零失败。 |
| Wiki | `node scripts/wiki-docs.mjs sync`、`stamp jsx-client-ui`、`check --strict`；`node --test scripts/wiki-docs.test.mjs` | 30 双语主题零问题，8 项工具测试通过；未代替人工翻译审阅。 |
| 产物一致性 | `common/build/chrome-feedback-artifact-audit.json` | 七个主要 JAR 各 36 个 AI payload 与规范源及 SHA-256 一致；两个支持节点的三个受影响 class 与编译输出一致，其余节点无原生 chrome 类；额外检查了 1.21.1 server JAR。 |
| 差异检查 | `git diff --check`，新文件空白与完整 diff 审阅 | 通过，仅换行提示；保留此前实机验收文档改动及所有无关未跟踪文件。 |

构建日志为 `common/build/chrome-feedback-final-build.log`，聚焦日志为 `common/build/chrome-hover-red.log`、`chrome-hover-red-26.2.log`、`chrome-hover-green.log` 和 `chrome-ui-regression.log`。资料 bundleId 为 `f27e5f59e3aef263bb91af688cfab6dc40627627ec8faa2dccc0fc805d2ac8ef`。

## 部署与人工验收

本轮自动结果不代表系统实际选择出的指针形状或 GPU 外观通过。CPU fixture 记录真实 Screen／region render 入口的 fill 和 cursor 请求；无 GLFW 显示环境时 cursor 类型可能回退 DEFAULT，不能替代实际系统指针观察。

部署前计划为等待用户选择安装并重启隔离游戏，再由用户检查指针和高亮。该计划已按后续用户授权完成，实际人工结论以文末为准。原有启动就绪性缺陷不属于本轮修复，维持已知问题。

未重复执行 NBT smoke 或 Probe 类型链：本轮未改 NBT、Script API、声明或类型生成器。没有提交、推送、升版或改动其他平台支持范围。

## 已授权部署与重启

用户明确选择安装并重启为大窗口、继续由用户手测。已通过系统正常关闭旧 PID 9372，日志确认 New World 全部维度存盘，等待进程退出后备份旧 JAR 并替换。本次部署 SHA-256 为 `db3e905d801dc7de0196b2b2e95db9157328266513faaec091922b5086d4523e`，与已验证构建一致；备份保存在 `evidence/chrome-feedback-20261105/nekojs-before-chrome-feedback-11240e1772a1.jar`。同一 MCP CLI／隔离目录／Zulu 25／NeoForge 26.1.2.71 已启动新 PID 5160，主菜单及系统最大化已截图确认。等待用户进入世界、手动 CLIENT reload 并测试，未代替用户操作游戏内测试。

## 实机反馈：边框指针与缩放限制澄清

用户首先反馈边／角指针、高亮及移开恢复均正常，但无法拖动调整大小；该整体反馈当时未计为完整通过。补充要求分别对 Tools 向内／向外拖动后，用户明确反馈“Tools 可以缩放，只有 Notes 放大受限”。规范示例的 Notes 设置 `maxWidth: 480`、`maxHeight: 320`，Tools 没有最大尺寸限制；该澄清与既有示例约束一致，未建立通用拖动失效，不因误判放宽约束或修改生产代码。因用户已澄清为约束行为，停止缺陷假设与修复流程；按钮高亮仍待用户验证。

## 最终人工结论（2026-11-05）

用户逐项确认：

1. 边和角的实际系统指针、对应边框高亮、移到内容区后的恢复均正常。
2. 补测确认 Tools 可以缩放，Notes 向外放大受已有最大 480×320 逻辑尺寸限制；不改变该示例契约。
3. 关闭／最小化／最大化三个按钮和还原按钮的悬停均正常，仅当前按钮高亮，关闭红色、其余浅色，移开恢复，按钮处不出现缩放指针；最大化及还原操作正常。

本次已执行的 **NeoForge 26.1.2.71 悬停反馈范围人工通过**，结论直接来自用户选项反馈。生产代码自自动验证后未再修改；只更新记录，无需重复完整构建。结构化结果及最终日志保存在 `evidence/chrome-feedback-20261105/`。游戏保持运行，未提交或推送。

未外推：26.2.0 实机外观尚未运行；协作 region 的指针、遮挡、捕获取消由本轮原生 CPU fixture 覆盖，未在此次增量中再次进行独立实机检查。此前启动就绪性错误仍为已知未修复问题。
