# 错误仪表盘 JSX 试作交付

## 范围

用户指定 `docs/architecture-refactor/prototypes/error-dashboard.prototype.html`，并明确选择“先做独立 JSX 界面试作，看效果后再接入真实错误”。实现放在原型旁，保留正式错误仪表盘及其数据、定位服务和平台支持。

入口与操作说明见 [JSX 试作说明](../architecture-refactor/prototypes/error-dashboard-jsx-prototype.md)。本轮只使用已有 CLIENT JSX 与 Minecraft Binding，不新增公共 API、依赖、原语或版本号；原始 fixture 六字段内容来自 HTML。

## 工作记录

- 阅读原型 HTML、已确认方向、当前 dashboard、JSX 原语／宿主／本地化与模块测试机制；保留此前边框及描边工作。
- 内嵌浏览器的本地 HTML 预览调用超时；布局依据原型源码和样式，原生视觉结果待游戏检查，不声称浏览器对比已通过。
- 真实 TSX 编译／模块检查在修正试作源码形状后通过，两项检查覆盖主要交互和登录延迟。加载错误不当作行为 RED；未修改编译器。
- 初版及全屏修订后的完整 `:common:check` 均通过。此试作不是正式错误仪表盘迁移或版本交付。

## 隔离实例准备

MCP 确认原测试客户端 PID 21820 仍连接。已验证原 workspace 示例与规范源一致，备份至 `evidence/error-dashboard-jsx-20261105/workspace-before-dashboard.tsx` 后移出自动脚本发现目录；未改其他脚本。原型安装为 `nekojs/client_scripts/prototypes/error-dashboard.prototype.tsx`，安装 SHA-256 与仓库试作源码一致。等待用户手动 CLIENT reload 与观感确认，不需要重启或替换 JAR。

## 首轮实机反馈与全屏修订

用户反馈“打开了，但应该是全屏 UI，现在这个不合适”。截图确认新界面正在运行，但宽度受限且主面板只有约一半高度；不能记为人工通过。修订移除外层留白及最大宽度，主面板贴合视口，底部保留演示工具栏。隐藏 fill 占位块显式使用零高度，避免与主面板平分高度；未修改 JSX 布局器。

初次日志中的 `some`／`map` Binding 预检诊断未阻止脚本运行。修订使用具名数组接收者，通过真实预检回归，不关闭校验。两项聚焦检查通过，新增主面板贴边、占满高度和收起后工具栏保持底部的断言。修订已安装；初版脚本与重载前日志保存在同一 evidence 目录。游戏已恢复大窗口，等待用户重载确认全屏观感。

## 自动验证结果

- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' :common:test --tests 'com.tkisor.nekojs.authoring.AuthoringExamplesEngineTest.independentDashboardPrototype*' --console=plain`：两项通过。
- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' :common:check --console=plain`：通过，2735 项，零失败／错误，4 跳过，包含 common 隔离和 addon 依赖面检查；3 分 21 秒。保留既有 Java 21 测试工具链，未刷新 golden。
- 四组场景六字段与 HTML 原始数据逐项比较通过；`git diff --check` 通过。
- 原生全屏、物理输入和剪贴板尚未人工通过；未运行本轮五节点构建，未改生产 Java／公共 API／声明或发布资料。

## 全屏反馈后的图标调整

用户确认修订已铺满屏幕，要求优化左上角感叹号。截图可见感叹号贴着方框左上角；原实现将 label 铺满方框，但 label 的文字仍从左上绘制。改为容器双向居中、文字使用自然尺寸，字号从 18 调至 14，为 22×22 方框保留内侧留白。只调整试作源码，不改变公共 label 对齐契约。

聚焦检查使用 `:common:test --rerun --tests 'com.tkisor.nekojs.authoring.AuthoringExamplesEngineTest.independentDashboardPrototype*'` 强制执行，两项通过（20 秒）：试作位于 docs 目录，单独改动它不会使既有 Gradle test 输入缓存失效。图标修订已安装且与源码哈希一致，观感待用户重载确认；上面的完整 common 结果对应此次纯视觉调整之前的全屏修订。

## 详情操作与消息样式修订

用户确认感叹号正常，继续指出复制等按钮的位置、大小及消息框样式偏离 HTML。截图确认按钮占满整行，消息区具有四边描边。修订将详情标题和按钮组在大视口同排，按钮靠右且使用紧凑尺寸、中性灰色；空间不足时将按钮移到标题下方，极窄视口纵排。消息区恢复原型的暗红背景、左侧红色强调线与内侧留白，移除四边描边；详情滚动区内显式填充蓝灰背景，避免原生 scroll 默认半透明黑底改变底色。复制与定位操作契约保持不变。

此修订仅影响试作布局和外观，未修改引擎、正式仪表盘、本地化资源或依赖。`./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' :common:test --rerun --tests 'com.tkisor.nekojs.authoring.AuthoringExamplesEngineTest.independentDashboardPrototype*' --console=plain` 两项通过（16 秒），`git diff --check` 通过。修订已安装且与源码哈希一致，等待用户实机确认；未重复完整 common gate。

## 折叠标题条与其余按钮

用户继续指出原始详情等按钮仍与 HTML 不一致。截图确认原始详情和调用栈是居中的蓝色长按钮，并与内容框分离。统一用试作内的 `Disclosure` 组件呈现深灰标题条、左对齐文字、蓝色方向三角及连贯外框，整条响应点击，焦点与键盘操作沿用现有原生 button。标题范围内的透明输入层与错误卡片使用相同机制，不覆盖内容区。原始文本、六字段、复制全文和展开状态契约保持不变。

其余普通按钮复用中性灰色 appearance，侧栏收起保留蓝色箭头，消除初版遗留蓝色按钮风格。组件调用沿用 `UI.element`，避开已有 TSX 自闭合组件表达式限制；没有修改编译器。相同的聚焦命令强制执行两项检查通过（20 秒），`git diff --check` 通过；已安装并校验源码哈希，等待用户实机确认。未重复完整 common gate。

## 自动 GUI 缩放下的紧凑布局

用户确认折叠栏已好，随后将 Minecraft GUI 缩放设为自动，指出界面拥挤。截图与实例 `guiScale:0` 配置确认现象；自动缩放降低了可用逻辑视口，原来的双栏与多行详情操作区占据过多空间。只在试作中增加紧凑布局，不改玩家设置或原生缩放行为：宽度小于 640 或高度小于 360 时，全宽列表与全宽详情切换；详情提供本地化“返回列表”，保留搜索和选择。页头隐藏装饰标识，收紧卡片、操作区、内边距和底部演示栏；大视口保留既有双栏。

native root 的 resize 只对已有 VNode 重排，不重新执行 render。试作 root 转发器保留原 root 的释放与分发方法，仅在跨过布局阈值时追加 refresh；没有新增定时器、逐帧 render 或改动公共 API。紧凑模式隐藏列使用零宽度，避免隐藏 fill 项抢占另一列空间。

新增一项真实模块测试，以 426×250 检查全宽列表／详情、详情剩余滚动空间、按钮边界、复制、返回保留搜索、无匹配提示和恢复 960×540 双栏。它发现并验证了跨阈值需要 refresh 的行为；没有放宽断言。相同聚焦命令使用 `--rerun` 后三项通过（13 秒）。修订已安装且源码哈希一致，等待用户在自动缩放下反馈；完整 `:common:check` 进行中。

## 按用户要求撤回紧凑导航，保持双栏比例缩放

用户明确要求原布局在不同 UI 尺寸下保持一致，否决另加列表／详情页面。紧凑导航代码与专用测试已替换；前节仅为被撤回的尝试，其 common 检查最终通过（2 分 31 秒），不代表用户接受该交互。

当前试作保持左右分栏（列表宽 26%）、标题与右侧操作同排，使用现有 1280×720 设计画布，每个原语显式 `coordinateSpace="design"`。`min(width/1280,height/720)` 缩放数字几何、字号与间距。root 转发器在 native resize 时保留设计宽高，只重排同一布局，没有页面切换、额外定时器或逐帧轮询。

现有原生控件只缩放布局框，button 字体、input 的字体／光标／选区／横向偏移以及 slider 的内部绘制仍使用固定 GUI 尺寸。为满足此界面使用已有设计坐标的具体需求，补齐共享原生控制绘制：在设计单位下绘制控件细节，鼠标坐标转换到相同单位，保留 committed logical 命中矩形与事件坐标。普通 logical 控件不变。此处涉及生产 Java，与此前纯脚本样式修订的范围不同；未新增公共 API、声明、依赖、版本或错误数据接入。需要新 JAR 才能完整实机验证。

三项 common 聚焦检查通过。比例测试覆盖 960×540、426×250、320×240、1280×720：两栏始终可见、列表占 26%、按钮不越界、尺寸与设计比例一致、搜索与复制保留。两个支持节点的原生聚焦回归各 42 项通过，涵盖 1/3、1/2、1、2 倍下的文字尺寸、按钮点击、输入定位与拖选、横向滚动、滑块映射和既有 logical／窗框回归。新增 fixture 的矩阵变换后文字记录用于验证实际绘制比例。早先 slider 测试将 string 事件直接当作 number，已按现有事件契约显式转换；没有放宽生产校验。

英文内嵌手册与双语 wiki 已同步；wiki stamp 使用既有工具生成，30 双语主题零问题。最终完整 common、两个节点构建及 guardLint 正在进行，新 JAR 尚未安装，自动缩放下的双栏观感尚未人工通过。

## 比例缩放版本的最终自动验证与安装

- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :26.1.2:build :26.2.0:build --console=plain`：通过，3 分 14 秒。common 2736 项、零失败／错误、4 跳过；两个节点普通测试各 815 项、零失败／错误、85 跳过；各 3 项 platform gate 通过。guardLint 扫描 606 个文件、407 个守卫块，零警告。保留 Java 21 测试工具链，未刷新 golden。
- `python tools/nekojs-ci-gates.py all --out common/build/dashboard-jsx-prototype-design-ci-gates.json`：四类 gate 通过；其他三个未受影响节点使用已有构建元数据，不能扩大为本轮五节点重新构建。
- `node scripts/wiki-docs.mjs check --strict` 与 `node --test scripts/wiki-docs.test.mjs`：30 双语主题零问题，8 项工具测试通过；`git diff --check` 通过。
- 四个本轮新建 JAR（common、authoring stdio、两个支持节点）各 36 项 AI payload 与 manifest 哈希及规范源字节一致。两个支持节点中的 Host／Tree 类与最终编译输出一致。证据为 `common/build/dashboard-jsx-prototype-design-artifact-audit.json`，bundleId 为 `b34dcffaa638ab0e4daf00f4a307a41616e31d412e591dbc78635b92285db76c`。
- 沿用此前安装／重启测试实例的授权：电脑窗口激活失败后，通过 Minecraft MCP 正常保存退出到标题页，再正常退出游戏；没有强杀进程。旧 JAR 与脚本保存在同一 evidence 目录，安装前后均核对哈希。已安装 26.1.2 JAR 的 SHA-256 为 `139c8990a12d09388f5af7782c2dc33d62fae609cd5bdb06d40df546c0c048db`。
- MCP 已连接重启实例 PID 18268／端口 9876，NeoForge 26.1.2.71；截图确认主菜单已最大化，`guiScale:0` 保留。提示用户进 New World，实际比例、物理输入及观感待用户操作确认；没有将自动测试或启动成功写成人工验收。

## 展开内容的独立滚动

用户补充“展开的原始详情无法滚动”，随后明确外层详情滚动正常，希望原始详情框内能单独滚动。沿 diagnosing-bugs 流程先运行实际原型／原生宿主检查，原有外层滚动及完整文本可达性通过；澄清后本轮属于新增内层滚动行为，不继续把它当作引擎输入故障，也不修改原生事件路由。

共享 `Disclosure` 组件为原始详情与调用栈分别增加 `scroll`，自然高度、上限 160 设计单位，跟随既有全页比例缩放。原标题与共用边框保留，短文本不强占固定高度；外层详情滚动保持独立。没有修改六字段演示数据、复制原文、原生生产代码或 JAR。

新增 `NativeDashboardScrollTest` 加载规范试作源码，经真实 TSX 编译和原生字体／布局／滚轮路由验证；只替代翻译、登录及宿主桥接边界。测试用额外长文本使内外区域分别溢出，验证原始详情／调用栈的滚轮移动、末行可达、框高上限、内外偏移互不覆盖。旧源码在“没有独立 scroll viewport”断言失败，证据为 `common/build/dashboard-jsx-inner-scroll-red.log`；浮点偏移使用 1e-6 容差覆盖设计比例重投影约 1e-13 的误差，不改变滚动距离或可达性要求。待最终聚焦验证、脚本安装与用户反馈；无需重启游戏。

最终命令使用 Zulu 25 daemon：`guardLint :common:test --rerun --tests '*AuthoringExamplesEngineTest.independentDashboardPrototype*' :26.1.2:test --rerun :26.2.0:test --rerun`，原生过滤为 `NativeDashboardScrollTest`、`NativeDialogModalTest`、`NativeWorkspacePresentationTest`、`Ticket43HostLayoutProjectionTest`。完整命令及输出见 `common/build/dashboard-jsx-inner-scroll-check.log`，全部通过（30 秒）；`git diff --check` 通过。脚本已备份并安装，哈希与规范源一致，JAR 未变，等待用户 reload 后手测。本次仅试作与原生测试变化，未重复完整 common gate、节点构建或之前的整体人工验收。

计数为 common 3 项、两个支持节点各 19 项，共 41 项，零失败／错误／跳过。已提示用户：完整显示得下的短文本不会滚动，先用 Long text 的调用栈验证内层滚动；原始详情与调用栈共用相同实现，独立长原始文本由原生回归覆盖。

## Java 侧长文本优化与直接原文场景

用户确认调用栈独立滚动可用，并反馈展开后持续卡顿、滚动时也卡；要求尽量由 Java 承担优化。已在共享宿主实现可见行提交、有界排版复用和短前缀测量复用，普通脚本无需新增分页或缓存。额外提供“长原文”场景，便于用户直接滚动到编号 159。

最终实现、测量、缓存失效／清理边界、完整验证与安装证据见 [JSX 原生长文本性能记录](2026-11-05-jsx-native-text-performance.md)。Java 优化版已安装，游戏重启为大窗口（PID 2928），保留用户当前 GUI 缩放 3；common 2736 项和两节点各 821 项的完整门禁通过，实际性能及原文滚动待用户确认。

后续用户反馈原文与 Error 仍不能滚动；最新修订、真实小数行距根因及 827 项节点回归见 [小数缩放与三个独立滚动区域](2026-11-05-jsx-fractional-text-scroll.md)。最终状态以该记录与隔离实例 acceptance.json 为准。
