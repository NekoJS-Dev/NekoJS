# JSX 组合式宿主与 Workspace 实现交付

## 1. 范围与状态

本轮按已批准的[原始审计](../reviews/jsx-ui-interop-and-optimization.md)实施后续工作。原始审计保留为历史分析，不在本文中改写当时结论；本文说明实际源码、调用边界、验证证据及尚未执行的人工验收。

**当前状态：本轮实现、回归、五节点构建、CI gate、wiki 与七个 JAR 产物一致性复核均已通过。真实客户端／第三方集成／维护者验收未运行；本文不是发布或人工验收通过声明。未提交、未推送、未升级版本。**

- 内嵌 AI 手册、示例与元数据全部使用英文；中文／英文 wiki 分别使用对应语言。
- 本轮不升级版本，不修改原始审计，也不把绘后叠画宣称为可交互挂载。
- 文档负责人不执行 Gradle、生成器、wiki 同步或盖章；集成验证由 Lead 统一协调。
- common 引擎、原生内存 fixture 与源码编译不替代真实 GPU、物理输入、OS 剪贴板、辅助功能、第三方 UI 或多人验收。

## 2. 核心内容契约

事实源是 [jsx-runtime.ts](../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts)：

- 函数组件身份由函数对象及外层 key／无 key 位置构成私有作用域；不同实例复用局部子 key 不串身份。无 key fragment／数组仍透明，直接同级重复 key 仍拒绝。公开 key 不改写；业务应保持组件函数稳定。
- owner-thread 重入 refresh 返回 `"queued"`，串行安排后续轮次而非递归提交；只有稳定候选可发布，最多 32 轮。耗尽后记录 render 诊断。`true` 表示至少一次候选提交成功，即使后续跟进失败；`false` 表示没有提交。入队本身不等于完成。
- `isUpdating()` 覆盖 render、组件展开、layout、stage、commit 及普通清空 host 的回调；空闲／已释放为 false。忙碌时普通 close 和 resize 返回 false，允许空闲后重试，不偷偷排队另一个 viewport。
- 普通 `close()` 在 host 清空失败时保留可重试 root；`forceClose(): void` 是仅 owner thread 的最终释放，即使清理失败也释放 guest render／事件／signal 依赖。更新中立即标记释放，host 清空延后到事务结束，排队工作不能复活旧 root。

## 3. 宿主：独占 Screen 与协作区域

事实源为 [ClientUiJS.java](../../src/main/java/com/tkisor/nekojs/bindings/static_access/ClientUiJS.java)、[JsxHostAdapter.java](../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java) 和 [JsxScreen.java](../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java)。原生范围仍是具备 ClientUI 的 NeoForge 26.1.2／26.2.0 CLIENT；不从 TSX 语法或内存 fixture 推导 Fabric／1.21.1 支持。

| 所有者 | 实际入口与责任 | 关闭边界 |
|---|---|---|
| 独占 Screen | `ClientUI.screen(title, pausesGame)`，createRoot／bindRoot／open；标题及暂停策略读取已提交根 screen props，缺省回退工厂参数 | 普通 close 可重试；removed／generation 结束强制最终清理；晚到清理不能关闭较新 Screen |
| 借用现有 Screen 区域 | `ClientUI.region(int x, int y, int width, int height)`，调用者提供实际 GUI 帧、输入链、焦点与 detach | 不创建／导航／关闭调用方 Screen；最终 detach 用 `dispose()` |
| 非 Screen GUI owner | 使用同一 cooperative region 接口，提供 owner thread、GUI extractor、viewport、有序输入与清理 | 不自动注册 HUD／全局监听器，也不是任意 renderer／第三方 UI 适配器 |

区域使用调用方 GUI 坐标与正数、可表示的宽高。实际绘制与命中按同一原点平移并裁剪，结束后恢复调用方 pose/scissor。调用者转发 `render(GuiGraphicsExtractor, int, int)`、mouseClicked／mouseDragged／mouseReleased／mouseScrolled、keyPressed／charTyped；输入方法返回消费 boolean，只在 false 时再交其它 owner，避免一次事件重复提交。

resize 用 `setRegion` 更新原 adapter；`setFocused`／`isFocused` 配合外部焦点链。区域外点击清除键盘焦点，但已捕获 drag/release 仍需转发；没有模态时边界 Tab 返回 false 交回调用方。仅丢失键盘焦点不等于取消指针捕获。模态保留自身输入边界；调用方仍负责与外部原生控件的绘制／输入先后，接口不会自动决定所有外部遮挡。

区域 open 可省略且不导航；仅已提交 generation 能绘制／处理输入。区域 close 仅 literal true 代表普通关闭成功，false 可重试；dispose 是最终释放。内嵌手册中的区域构造片段明确需要真实 caller，不作为可独立自动打开的示例或已完成第三方桥接。`pausesGame` 只是本地 Screen 策略，不会暂停远程服务器。

## 4. 一个 root 的 Workspace

独立模块 [jsx-workspace.ts](../../common/src/main/resources/nekojs/node/modules/jsx-workspace.ts) 复用现有 JSX root／host 事务，不另造 reconciler，也不为每个逻辑窗口切换 Minecraft Screen。

- `createWorkspace(host, options?)` 使用全新受管 host，自动创建并绑定唯一 root。原生 bindRoot 对同一 guest handle 重复调用幂等并拒绝其它 handle；layout 在发布前以 NEKO-7014 拒绝不匹配的 root ID。这些校验不保证同一 host 上单独创建的同名 root 能安全共存，调用者仍须遵守全新 host 前提。控制器提供 add／update／activate／hide／show／closeWindow／open／close／isDisposed／snapshot／layout。
- 运行时共 16 个 primitive；新增 workspace／window。最多一个 workspace，只能是 root 或根 screen 的直接子节点；其直接子节点只能是 window，最多一个 active。窗口及祖先使用 logical 坐标，不支持 design-space 窗口几何。
- 窗口配置是普通 title／rect／visible／draggable／closable 数据；默认矩形为 `{ x: 16, y: 16, width: 240, height: 160 }`，最小 40×20，标题栏高 20。snapshot 的不可变矩形是请求的 workspace-local logical 几何；layout 返回已发布、受视口约束的实际 root 布局。
- 显式子内容 ID 在组件展开后按窗口作用域处理，不同窗口可复用局部 ID，同一窗口重复仍拒绝；原生窗口 ID 保持原值。手动 dispatch 从已发布 layout／native props 读取实际不透明 ID，不能拼接或依赖序列化格式。
- hide 保留内容身份及 signal 更新，但阻止输入；show 提升并激活。隐藏不是暂停后台工作或持久化。最后窗口关闭默认保留空桌面；closeWhenEmpty 只请求普通 host close，失败不等于已释放。
- 标题栏捕获、z 顺序、激活、活动窗口焦点及遮挡由同一 host 协调。控制器在 root 发布／嵌套 render 中的变更被拒绝，普通事件才修改模型。变更在 owner thread 返回 boolean，跨线程可返回 queued；队列不是完成证明。

新的 [workspace.tsx](../../common/src/main/resources/nekojs/authoring/examples/client/workspace.tsx) 是完整 50 行场景：Notes、Tools、Preview 三窗口、四个外部 signal、独立输入／计数、只读钻石及预览摘要。安装到 `nekojs/client_scripts/authoring-examples/workspace.tsx`，只保留一个自动打开示例，执行 `/nekojs reload client`。关闭 Screen、移除脚本并再次 reload CLIENT 清理；不涉及文件、背包或网络权威。

[元数据](../../common/src/main/resources/nekojs/authoring/examples/metadata.json) 新增 client-workspace；当前共 15 场景、17 源文件。新源码与[手册](../../common/src/main/resources/nekojs/authoring/handbook/jsx-ui.md)、[食谱](../../common/src/main/resources/nekojs/authoring/handbook/scripting-examples.md)、[中文 wiki](../../wiki/jsx-client-ui_cn.md)、[英文 wiki](../../wiki/jsx-client-ui_us.md) 的完整代码块保持逐字一致。

Workspace 编写异常使用 NEKO-7013；managed-mount 区域边界／模式及单 root 所有权校验使用 NEKO-7014（已复核 validateRegion／setRegion／bindRoot／layout 源码）。它们不是 HTTP/MCP 结构化 wire error；原有 NEKO-7006 延迟 generation 工作被丢弃的语义不变。见[中文错误目录](../../wiki/error-reference_cn.md)和[英文错误目录](../../wiki/error-reference_us.md)。

## 5. 验证记录与证据限制

| 项目 | 当前结果 | 证据边界 |
|---|---|---|
| 文档静态检查 | 17／17 食谱源块、4 份 Workspace 完整副本、15 个唯一场景、两种 JSX wiki 各 32 个／错误目录各 10 个保留数字锚点均通过 | 文本与安装契约，不证明实际交互 |
| 链接／语言／格式 | 10 个 scoped 文件的 fence／英文 bundle／空白／wiki 表格列数检查通过；108 个本地链接／锚点（含无扩展名 wiki route）均通过；scoped git diff --check exit 0 | 不替代打包／生成器结果 |
| Workspace common fixture | JsxWorkspaceRuntimeTest 14 项通过，含实机按钮裁剪对应的四项尺寸回归 | 真实引擎及事务／layout 数据路径，不是原生可见画面 |
| 核心 key／busy／清理 | NekoTypeScriptJsxRuntimeTest 17 项通过，含组件展开取消、resize 释放边界及原生 ESM 导入 | 最终 common 聚合也通过；不是原生输入验收 |
| 完整 bundled Workspace 真引擎执行 | AuthoringExamplesEngineTest 6 项通过，包含原样三窗口 Workspace 示例 | 真实编译器／模块加载器与 fake host，不是原生 GUI |
| 原生生命周期／区域／Workspace | NeoForge 26.1.2 与 26.2.0 各 7＋5＋3 项，共 30 项通过，0 skip | 原生内存 fixture，不是 OS／GPU |
| common／声明／类型 | 最终 common 369 suites、2721 项，0 failure／error、4 skip；common:check、processor:test、assemble、authoringStdioJar、guardLint、test:probe-types 及三 NeoForge 声明基线通过 | Java 21 测试工具链；Zulu 25 Gradle daemon；399 guard blocks／598 files／0 warning |
| wiki gate | 30 双语主题 0 问题；8 项工具测试；60 页 288 代码块语法通过；60 页数字锚点稳定；234 证据链接通过；同步幂等 | 代码块语法不等于运行；30 主题仍 human-pending |
| 五节点完整构建／CI | 1.21.1、26.1.2、26.2.0、26.1.2-fabric、26.2.0-fabric 的 build 全部通过；五份 source-root trace 与四项聚合 CI gate 全部通过 | 节点 tests 分别 472／768／768／438／438，0 failure／error；skip 分别 33／85／85／39／39 |
| 七个最终 JAR | 两个 common 制品与五个平台制品的 36 个 authoring payload 源码及 SHA-256 全一致；六个运行时 JAR 的 runtime／workspace／modules.list 与源码一致 | stdio 仍仅 authoring／Gson 等隔离类，bytecode major ≤65；原生 Workspace 类仅两个 NeoForge 26.x 节点包含 |
| 真实客户端与维护者结论 | 26.1.2.71 修复版用户截图已显示完整按钮及计数；后续输入框边框缺失正在回归诊断，完整验收／维护者结论仍待执行 | 局部反馈不扩展为完整通过 |

本轮先观察行为 RED：组件 key 回归先失败后 common 单项通过；普通 close 回归观察到旧树保留断言 expected 1／actual 0；Workspace 在模块缺失时报告 `Cannot resolve module: nekojs/jsx-workspace`；重入回归复现旧 snapshot。偶发 FML 启动失败不作为行为 RED。

全量测试还暴露了旧文档 fixture 的全局路径污染：它以一次性 `@TempDir` 首次初始化 Platform，后续缓存及资源测试仍引用已删除／不匹配的根。文档 fixture 与 RegisteredItemAssetsTest 的两类最小复现为 5 项中 4 项 NoSuchFileException；仅改用稳定 TestPlatformInit 初始化、保留该 fixture 私有脚本目录后，最小复现及完整 common 均通过。生产沙箱未放宽。另一个旧 recipe 声明 fixture 的无配对 Platform 替换由源码确认，是全量路径不匹配的一条可能顺序链；未把未经顺序插桩的历史执行顺序当作实测结论。

首次五节点构建还发现旧 Ticket41 teardown 源码断言仍搜索普通 `close`。最终释放现在明确调用 `forceClose`，因此仅将断言搜索字面量改为该方法；保留“调用存在”以及“guest 释放先于 CLOSING”的两项顺序约束。没有为通过测试而改回可重试关闭。

源码测试索引：[ui-core.tsx](../../common/src/test/resources/nekojs/language-ts-examples/tsx/ui-core.tsx)、[JsxWorkspaceRuntimeTest.java](../../common/src/test/java/com/tkisor/nekojs/core/module/JsxWorkspaceRuntimeTest.java)、[NativeMountLifecycleTest.java](../../src/test/java/com/tkisor/nekojs/client/ui/NativeMountLifecycleTest.java)、[NativeRegionMountTest.java](../../src/test/java/com/tkisor/nekojs/client/ui/NativeRegionMountTest.java)、[NativeWorkspaceMountTest.java](../../src/test/java/com/tkisor/nekojs/client/ui/NativeWorkspaceMountTest.java)。索引表示覆盖意图／源码位置；通过状态以实际运行结果为准。

### 5.1 最终命令与产物复核

以下为实际执行且成功的命令；先前 RED、过时目录计数和旧 stdio 手册不作为最终结果。所有 Gradle／source-root driver 均由 Lead 串行协调，没有并发修改 Stonecutter 活动节点。

```bash
./gradlew.bat "-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25" :common:check :common-api-processor:test :common:assemble :common:authoringStdioJar guardLint --console=plain
npm run test:probe-types
./gradlew.bat "-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25" :1.21.1:platformGateTest --tests com.tkisor.nekojs.platform.DeclaredEventSurfaceGoldenTest :26.1.2:platformGateTest --tests com.tkisor.nekojs.platform.DeclaredEventSurfaceGoldenTest :26.2.0:platformGateTest --tests com.tkisor.nekojs.platform.DeclaredEventSurfaceGoldenTest --console=plain
./gradlew.bat "-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25" :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
# Python below is the bundled Python executable; JAVA_HOME points to Zulu 25.
for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do
  python tools/nekojs-ci-gates.py source-roots --node "$node"
done
python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
node scripts/wiki-docs.mjs sync
node scripts/wiki-docs.mjs stamp jsx-client-ui error-reference
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
git diff --check
```

声明 golden 的有意变化是 JSX 的 workspace／window、root 生命周期接口和新模块声明；旧 dialog 回调的 escape／backdrop 类型保持兼容。三平台事件声明基线普通验证通过，没有为本轮重写。

最终七个 JAR 的 authoring `bundleId` 均为 `a41acd596175b274b4513c87147723e4527828c7191ca9b699f48b5dcb2a2f84`。本地忽略目录中的产物审计复核 payload／manifest／SHA-256、运行时模块字节、stdio 类隔离和五节点原生 class gating；最后一次全部 exit 0。手册措辞修订后一度发现旧 stdio payload，已重新执行完整 common 检查及装配并再次确认七制品完全一致。

NBT smoke 本轮未单独重跑：没有修改 NBT／持久化路径。真实客户端已启动并收到首轮局部反馈；完整场景与维护者结论仍待下一节的逐项人工验收。

## 6. 待执行的人工验收

记录实际 node／loader、GUI scale、viewport、输入设备和维护者结论。下表完整场景尚未逐项验收；已收集的窗口／局部点击反馈见 6.3，不能据此填为整项通过。

| 场景 | 应观察的边界 | 状态 |
|---|---|---|
| 组件组合与更新 | 重复局部 key 不串身份；非法直接重复被拒绝；更新不会以旧输出覆盖新状态 | 未运行 |
| 借用区域 | 调用方原生控件仍可用；同一事件只消费一次；绘制与命中坐标一致 | 未运行 |
| 挂载生命周期 | 重复 init／resize 不重复安装；detach 不关闭调用方；失败 reload 保留现有会话 | 未运行 |
| 非 Screen owner | 实际 GUI extractor、viewport／clip、有序输入与 cleanup 均有真实 caller 证据 | 未运行 |
| 三窗口叠放 | 前窗空白及 tooltip 阻挡背景；露出的后窗可激活；一次点击最多记一次 | 未运行 |
| 标题栏拖动 | 越界仍保持 capture；release／关闭／reload／会话取消清理；仅键盘失焦不取消已捕获手势 | 未运行 |
| 焦点与模态 | 活动窗口才接键盘；边界 Tab 协作；全局模态及合法焦点恢复符合实现 | 未运行 |
| GUI scale／视口 | 标题栏与关闭入口可达；实际绘制、布局和命中矩形一致 | 未运行 |
| generation 结束 | 统一释放窗口、订阅、捕获及回调，不复活旧 guest 值，不关闭较新页面 | 未运行 |

维护者结论：**待执行**。真实 GPU、物理输入、OS 剪贴板、IME、辅助功能、第三方互操作及多人容器均未由本轮文档或内存 fixture 建立验收结论。

### 6.1 用户操作的首轮 Workspace 检查清单

只安装上文未改写的 Workspace 示例，避免多个自动打开示例争抢 Screen。开始前记录实际版本／loader、GUI scale 和窗口大小；以下步骤由用户在游戏内操作，观察后再记录结果。

1. 初始应显示 Notes、Tools、Preview，输入分别为 `Draft note`／`Tool input`，两个计数为 0，预览为 `Saved=0; clicks=0`，并显示钻石。Preview 初始在前。小视口会裁限请求矩形，按实际画面操作，不硬编码屏幕坐标。
2. 拉开窗口，Notes 输入改为 `Note A`，单击 Save 两次，每次只加 1，最终 saved=2。Tools 输入改为 `Tool B`，单击 Count 一次，最终 clicks=1、预览 `Saved=2; clicks=1`；Notes 文本及 saved 不受 Tools 输入影响。
3. 先聚焦 Notes 输入，再点 Tools 标题栏并输入 `X`，后台 Notes 不应变化；显式聚焦 Tools 输入后替换文本，仅 Tools 变化。Tab／Shift+Tab 不应把键盘交给后台窗口。
4. 把 Preview 的空白内容压在另一个窗口按钮上，记下两个计数，再点击被遮住的按钮位置：计数都不应变化。点露出的后窗标题栏抬到前面后，点击可见按钮应只加 1。原样示例没有开启钻石 tooltip，这一步不验收 tooltip。
5. 按住标题栏拖出原矩形再释放，拖动期间持续跟随，释放后停止。实际调整游戏窗口大小后，输入／计数保持，绘制与命中对齐，标题栏及关闭入口可达。通过关闭 Screen 进入 Options 再打开，不等同于原地 resize；CLIENT reload 则按契约重置状态。
6. 依次关闭 Notes、Tools，剩余窗口继续可用，共享计数保留；关闭最后一个 Preview 应留下空桌面。Escape 关闭并重新 reload CLIENT 后恢复初始值，不应受旧回调或捕获影响。

每步返回观察结果；出错时提供当前截图、操作顺序、预期与实际计数，以及对应日志。原样示例不覆盖 hide／show、重复局部组件 key、失败候选、重入、模态或合作区域；不能从三窗口成功推定这些项目通过。

合作区域另需真实 caller 转发 extractor、输入、焦点及 detach。当前 screenRender 的 PainterJS 不是该接线；hudRender 至多支持另写的绘制 smoke。没有实际 caller 时保持区域实机验收未运行，不能把手动打开 Workspace 或普通绘后叠画当作区域交互证据。

### 6.2 26.1.2 实机启动与操作交接（2026-11-03）

用户最终确认：MCP 或 Gradle 仅用于启动客户端；UI 的具体操作及视觉效果由用户验收，不再通过 MCP 驱动 UI 测试。此前已通过 MCP 查看标题页并请求创建隔离测试世界；这不构成 Workspace 交互验收。随后已执行退出控制，返回 `control_mode: false`。

- 独立实例：`common/build/mcp-live-26.1.2-20261103`，没有替换用户原实例的 mod 或存档。使用 Zulu 25.0.3、GraalMC 25.1.3.7、当前构建 NekoJS 1.1.0-preview3，以及官方 Minecraft MCP v0.4.2 的 26.1.2 NeoForge 桥接模组。
- 启动器原有 `26.1.2.36-beta` 在加载 NekoJS 自动订阅者时失败：`ClassNotFoundException: net.neoforged.neoforge.event.TagsUpdatedEvent$ServerDataLoad`。失败进程已按 PID 与独立目录校验后停止，旧日志保留在该实例的 `evidence/startup-oldloader.log`。这是已复现的旧 loader 兼容问题，不是 JSX 验收失败。
- 按仓库编译基线安装并指定 `neoforge-26.1.2.71` 后，标题页及实际 Mod List 确认版本正确；该实例日志随后记录 `NekoDev joined the game`。这仅证明启动及进入世界。
- 原样 Workspace 脚本已放到独立实例的 `nekojs/client_scripts/authoring-examples/workspace.tsx`。启动早期首次执行遇到 `Minecraft.getInstance()` 为 null 的 Screen 构造错误；自动 CLIENT reload 随后记录 `Components not bound yet`。这些是实际启动日志，尚未证明世界就绪后的手动重载是否也失败，不能宣称一般渲染缺陷或已修复。
- 只读诊断确认：早期 CLIENT 加载期间，原生 host 工厂立即捕获 Minecraft／创建线程并访问 Screen、font 和资源，符合首个异常栈。现有 Unsafe 原生 fixture 绕过了这条构造链，不能作为启动就绪性证据；全局延迟所有 CLIENT 加载也可能破坏早期事件注册，尚未采用任何生产修复。第二条异常的候选解释是钻石图标创建 ItemStack 时组件尚未绑定；源码可解释该消息，但实际运行调用链尚未确认。修复版进入世界后的重载日志见 6.3；启动就绪性问题本身仍未修复，不从启动阶段异常推定一般绘制失败。
- 初次交接时已请用户在世界内执行 `/nekojs reload client`；后续收到了窗口和按钮的局部反馈，详见 6.3。完整输入、遮挡、拖动及关闭仍未逐项完成。

### 6.3 首轮用户反馈：按钮裁剪（已修复，视觉复验待用户）

用户执行手动测试后报告 Notes 按钮异常，并授权 MCP 仅截图检查。原图保存在独立实例的 `evidence/04-user-notes-issue.png`：Notes 在前景，输入完整，但 Save locally 和 Tools 按钮仅显示上半部分。选项工具收集到用户确认：**显示不全，但露出部分点击有效**。这建立了窗口出现和局部点击的证据，不代表完整交互、遮挡、焦点或生命周期通过；上表完整验收项目仍待逐项执行。

源码诊断与截图一致：滚动区域中的自动高度 column，padding／gap 都是 6。测量时递归忽略输入及按钮显式高度 20，仅得到输入固有 0、按钮文字 9、标签 9，再加两组 padding／gap 共 24，合计 42；实际内容需要 20 + 20 + 9 + 24 = 73。截图 Notes 内容起点约为逻辑 y=120，错误裁剪终点 y=162（物理约 324），吻合按钮被切断的位置。原生裁剪遵循已发布的矩形，未发现独立的原生 scissor 栈缺陷，不能通过取消裁剪规避测量错误。

已先复现两项 RED：该 column expected 73／actual 42，通用 auto-row expected 108／actual 28.799999999999997。生产修复仅让子节点已解析的数值 width／height 及数值 min／max 参与父容器固有测量，保留 fill／百分比延后分配、隐藏和 dialog 脱离文档流的既有规则，没有改动原生 scissor。新增四项公共接口回归覆盖截图几何（完整按钮 clip=20、计数 clip=9）、通用 row／column、fill／百分比以及 responsive／design／minmax；Workspace／核心／高级控件聚合共 39 项通过。

完整 common 首次仅六 profile inspector golden 不同：原基线把 40×12 图片当作零固有尺寸，actions row 为 22×9，错误记录图片溢出。逐行审阅六 profile 共 30 行变化后，使用真实 collected snapshot 更新只读基线为 62×12 的 row 和完整图片 clip；scroll 顺延 3，真正的 200px 宽行溢出及滚动裁剪保留。没有削弱断言或自动跳过差异。随后 inspector 单类、完整 common（2721 项、0 failure／error、4 skip）、五节点 build、guardLint、类型 gate、五份 source-root、四项 CI gate 及七制品审计全部通过。

用户明确同意重启后，已校验旧 PID 3700 属于该独立实例并正常关闭；部署修复版 JAR，SHA-256 为 `5bdacee33207af870f1f4245b9bf6444ca01e4623742677b126eadb8ef8bb58f`，旧 JAR 留在隔离实例 evidence 中。按同一 NeoForge 26.1.2.71／Zulu 25 启动新 PID 5936，并核对进程路径及实际 Minecraft 窗口。未通过 MCP 操作 UI。随后仅被动读取实例日志：`latest.log:228` 记录 23:39:20.994 的 `NekoDev joined the game`；`logs/nekojs/client.log:27-30` 记录 23:45:21 的 CLIENT 重载开始、发现两份脚本和重载完成，该段没有新增错误。这只证明本次进世界及重载日志，不证明按钮几何、绘制或交互正确；修复后的视觉结论仍待用户返回。

### 6.4 后续输入框反馈（已修复并重启，待视觉复验）

用户反馈输入框似乎不完整，并明确允许在需要检查问题时直接通过 MCP 截图；仍未授权点击、输入或接管 UI。两次 MCP 被动截图返回相同的 854×480 模糊背景，未捕获 GUI，因此不能用作该缺陷的几何证据。随后按用户提示仅读取剪切板中的图片，保存为隔离实例 `evidence/07-user-clipboard-input.png`（2560×1528），未修改剪切板或操作游戏。

该用户截图中 Save／Count 按钮与计数均已完整可见；Notes 显示 saved=3，Tools 显示 clicks=7，Preview 显示 `Saved=3; clicks=7`。这只证明截图时的显示及状态，不推定点击次数准确、焦点隔离或遮挡通过。输入框文字完整，但上／左边线可见，下／右边线缺失。原图像素抽查 Notes 的 356×40 物理矩形：上边 356／356、左边 40／40 为白色，底边仅 2／356、右边仅 2／40 为白色，后两者仅来自角点交集。

新增 NativeInputFrameRegressionTest 复用真实 canonical root、原生 paint 以及 Minecraft 自身 outline／pose／scissor，只在最终 fill 边界记录有序像素并略过字体／GPU 提交，不重写 outline。26.1.2 首轮 7 项中原点对照通过，其余 6 项（有偏移的输入、聚焦输入、按钮、slider、图片 fallback 和带平移的合作区域）均复现底／右边线缺失，例如底边 expected -9406592／actual -14670549。26.2.0 首轮为 ScissorStack 构造签名变化导致 fixture 初始化失败，不作为行为 RED；改为调用对应版本的真实构造器后，两版本均得到 7 项中 6 项失败、原点对照通过、0 skip 的行为 RED。

根因是 JsxHostAdapter 的四处 outline 调用把终点坐标作为宽高传入，偏移位置上的下／右边因此超出真实 clip。生产修复仅将图片 fallback、button、slider、input 的 outline 参数改为 width／height；fill 仍使用终点坐标，没有取消 scissor、修改焦点／输入状态、控件配色或公共契约。修复后两个节点各 7 项全部通过。完整检查首次因新测试末尾守卫写为 `//? }` 而被 guardLint 拒绝；只改成规范的 `//?}` 后重跑，没有修改检查规则或断言。

最终执行 `:common:check :common:assemble guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build`，随后五份 source-root、四项 CI gate 及 common／workspace 制品审计均通过；最终完整节点测试报告再次确认边框回归 14／14、0 failure／error／skip。独立 Standards／Spec 复核均无待办发现。这些是真实 extractor 路径的 CPU 回归，不是 GPU 或用户视觉验收。最初的重启选项询问未收到答复，期间没有操作当前客户端；用户随后明确授权重启，现已部署并重新启动，视觉复验仍待用户完成。

部署前只读准备检查确认：修复版 JAR 的 SHA-256 为 `78c19793a2186ded2a44e1b66c56f620b1ab4f4d2479d70574868dc2ac91c8b8`，其中原生 host class 与该节点实际编译输出逐字节一致；隔离客户端 PID 5936 仍在运行，mods 中仍为前次布局修复版 `5bdacee33207af870f1f4245b9bf6444ca01e4623742677b126eadb8ef8bb58f`。准备的部署辅助脚本默认仅检查；实际部署要求批准的前后哈希均匹配且隔离客户端已退出，并保留独立备份。此时未关闭游戏、未替换 mods 或改动世界。

用户明确同意重启后，按 PID／Java 进程名／完整游戏目录核验旧 PID 5936，正常关闭并等待其退出，没有强制终止。前后 JAR 哈希均与批准值一致，部署脚本确认无隔离客户端运行后才替换本体，并保留 `evidence/nekojs-before-input-frame-fix-5bdacee33207.jar`。相同 CLI／NeoForge 26.1.2.71／Zulu 25 启动新 PID 27560；被动进程检查确认游戏目录及 Minecraft 窗口，`latest.log:3` 和 `latest.log:17-21` 确认隔离目录与实际 Mod List。未通过 MCP 点击、输入、进入世界或执行命令；启动成功不等于已进世界或视觉验收通过。本次被动读取 `logs/nekojs/client.log:1-26`，00:27:24 的首次加载仍出现 Minecraft 为 null 的 Screen 构造错误，00:27:28 自动重载仍出现 `Components not bound yet`；这是前述尚未修复的启动就绪性缺陷，不是新的边框回归证据。观察的本次日志中尚无进入世界及世界就绪后手动重载的证据，因此没有把自动重载的完成文字视作手动验收通过。

第二张用户剪切板截图 `evidence/08-user-input-independence.png` 显示 Notes=`Note A`、Tools=`Tool B`，各自值可见，计数为 9／18，Preview 同样为 9／18。该示例的标签只显示按钮计数，不回显输入内容，源码中输入只更新独立 signal，所以只改文字不应改变这些标签。精确单击加一及完整焦点隔离仍未得到用户确认。

### 6.5 拖动后的边线反馈（已复现原生像素裁剪，尚未实机复验）

用户继续反馈拖动窗口时输入框边线可能消失。本轮只做被动截图，没有通过 MCP 拖动、点击或输入；`evidence/09-user-drag-frame.png` 的前景 Notes 四边完整，背后的 Tools 部分被 Notes 遮挡，静止图不能判定瞬时绘制正确。

新增回归通过真实 workspace、原生标题栏输入及每次发布后的真实 outline／pose／嵌套 scissor 回放小数位置。最初两版本均通过，但复核发现像素记录器使用的 Minecraft `containsPointInScissor` 查询最外层 entry，真实 `innerFill` 却将私有 stack 的最内层 `peek()` 附在绘制命令上。仅修正记录器以读取真实命令裁剪矩形后，两个节点均为 8 项中拖动项失败、其余 7 项通过、0 skip；6 个帧检查复现白色边线应可见处仍为背景，expected -1／actual -1341124592。最初 GREEN 不构成内层裁剪正确的证据。

源头是原生几何分别 round 位置及尺寸，裁剪却向内 ceil／floor；窗口停在部分小数位置时会吃掉一像素边线。额外的同一路径用例约束小数位置和尺寸：逻辑 `[12.75,73.25) × [12.75,33.25)` 对应最近像素边界 `[13,73) × [13,33)`，避免只改 clip 仍让独立取整的宽高多出一像素。两版本在生产修复前均为 9 项中拖动与小数尺寸 2 项失败、0 skip；修复后均为 9／9、0 failure／error／skip。生产改动仅统一原生 bounds 与 clip 的最近像素边界，并在转 int 前检查长整数跨度；公共逻辑坐标、Inspector double 矩形、scroll 偏移与 dialog portal 语义不变，小数尺寸的原生像素宽高可能相对旧的独立取整差一像素，绘制及命中区域保持一致。两个节点全部原生 UI 测试及 guardLint 通过，随后 common check／assemble、五节点 build、五份 source-root、四项 CI gate、common／workspace 制品审计均通过。common 为 2721 项、0 failure／error、4 skip；七份 JAR 的 36 项手册内容与源、bundle hash 保持一致。CPU 命令裁剪证据不替代 GPU、实际鼠标拖动及用户验收。

只读部署检查确认修复版 SHA-256 为 `dbf6fdb804741c287a79818580de4ab63c88c935bbcf376989cba0e5210f6834`，其中 JsxHostAdapter／JsxHostLayout／JsxWorkspaceScope class 与当前节点编译输出一致。准备检查时 PID 27560 仍运行旧版 `78c19793a2186ded2a44e1b66c56f620b1ab4f4d2479d70574868dc2ac91c8b8`。用户随后通过选项明确选择立即安装并重启：核验 Java 进程及完整 gameDir 后正常关闭 PID 27560，未强制终止；确认无隔离客户端运行、前后哈希一致后部署，并保留 `evidence/nekojs-before-drag-frame-fix-78c19793a218.jar`。相同 CLI／NeoForge 26.1.2.71／Zulu 25 启动 PID 31648，被动检查确认其隔离 gameDir、进程存活、窗口响应。未操作游戏输入，未推定进入世界或视觉验收通过。独立 Standards／Spec 复核均无待办；严格 wiki 检查为 30 主题、0 问题，差异空白检查通过。随后被动日志确认 01:02:44 进入世界，01:07:09–01:07:10 的手动 CLIENT 重载片段无新错误。用户在此次新版复验后明确反馈“这次没问题了”，因此本轮拖动后边线消失问题已有真实用户通过结论；这不扩大为全部 UI／焦点／精确点击次数验收，旧启动就绪性问题也未在本轮修复。

## 7. 明确未交付的设计项

没有任意第三方 Screen 自动嵌入、任意 Java Widget JSX 标签、通用 renderer 能力协商／插件生态或自动 HUD 输入接管。cooperative region 是显式接线的原生 seam，不是已完成所有审计设计的证明。没有 React／DOM／CSS／IME composition 契约、服务端 Slot／菜单／背包权威同步或额外 OS 窗口。

窗口缩放、最小化／最大化、吸附、任务栏、布局持久化及性能收益不在本版承诺内。隐藏内容不暂停业务；只读 Item 不产生库存权限。历史审计讨论过的目标，仅在实际实现和独立证据支持时才可列为已交付。
