# JSX 原生默认滚动条

## 范围与实机反馈

用户已按上一轮选项确认 Error 消息与原始详情的独立滚动、末行可达没有问题，随后指出缺少滚动条，无法判断当前位置。这一结论仅覆盖已执行的两个文本区域，不扩大为 GPU 性能或整套界面验收。

本轮在两个受支持 NeoForge 26.x 节点共用的 Java JSX 宿主增加默认滚动条，规范 TSX 试作不增加辅助节点、布局计算、缓存或逐帧刷新。common 布局算法、声明、数据格式与正式错误仪表盘路由未改。

## 行为与实现

- 内容溢出时显示常驻细窄轨道与滑块；无溢出时不显示。滑块长度表示可见比例，位置表示当前偏移，悬停与拖动时变亮。
- 滚动条覆盖末端边缘，不改变现有布局分配；需要时可用已有 padding 留出内容空间。当前试作已有文本内边距。
- 默认纵向，既有 row 方向滚动使用横向条。设计缩放下将 authored range 转为逻辑像素计算比例，绘制与输入复用 `JsxScrollBar` 几何。
- 鼠标主键可拖动滑块，点击轨道移动一屏；拖动离开区域后仍由既有 tree capture 路由至释放。偏移使用宿主原有 relayout／scrollOffset 路径，改变后发送既有 scroll 回调，delta 沿用每 12 authored units 一步的约定。
- 滚动条绘制在内容之上，输入命中先于自身子控件，避免误触底层按钮；兄弟遮挡、workspace、模态与 clip 继续使用现有边界。条内悬停不向内容传递 tooltip。
- 捕获只保留节点身份、方向与抓取比例；隐藏、删除、禁用、模态切换、溢出消失或方向改变均不能继续旧手势。没有新资源所有者或跨线程工作。

## 验证计划与证据

`NativeScrollBarTest` 经真实运行时及原生绘制／输入路径检查四种设计视口的进度和无溢出隐藏、内外独立、末端拖动、轨道翻页、单次回调、横向条、兄弟遮挡与失效捕获。基础字号的旧实现已在“Overflow must paint a visible native scrollbar”处失败；最初缩放夹具的无关文本被原始 Java interop 的整数宽度限制阻挡，之后改为无文本内容面板。没有修改生产绑定或弱化门禁。

用户将继续在大窗口内手测；CPU 绘制记录不能代替滑块观感、物理鼠标或性能验收。最终验证与安装结果见下。

## 最终验证与安装

- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :26.1.2:test --tests '*NativeScrollBarTest' --tests '*NativeDashboardScrollTest' --tests '*NativeDialogModalTest' --tests '*NativeInputFrameRegressionTest' :26.2.0:test --tests '*NativeScrollBarTest' --tests '*NativeDashboardScrollTest' --tests '*NativeDialogModalTest' --tests '*NativeInputFrameRegressionTest' --console=plain`：两节点各 35 项通过，零失败／错误／跳过。日志：`common/build/dashboard-jsx-scrollbar-focused-both.log`。
- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :26.1.2:build :26.2.0:build --console=plain`：全部通过（5 分 3 秒）。common 2736 项、零失败／错误、4 跳过；两节点各 835 项、零失败／错误、85 跳过，各 3 项 platform gate 通过；guardLint 扫描 612 文件、零警告。日志：`common/build/dashboard-jsx-scrollbar-build.log`。保留 Java 21 common 测试工具链，未刷新 golden。
- 五节点 `source-roots` 与四类 CI gate 通过，未受影响节点复用既有输出。报告：`common/build/dashboard-jsx-scrollbar-ci-gates.json`。wiki 严格检查 30 双语主题零问题、8 项工具测试通过，`git diff --check` 通过。
- common、authoring stdio 和两原生 JAR 各 36 项 AI payload 与规范源／manifest 字节、哈希一致；宿主及滚动条全部类与编译输出一致。报告：`common/build/dashboard-jsx-scrollbar-artifact-audit.json`，bundleId 为 `18b9a430ac43df32aeb209da99e6b2663ce38c7dd0bfff3c238d9bef1a2af4ab`。其余三个节点未重建，未宣称其发布包资料已同步。
- 沿用此前授权，通过 MCP 正常保存退出、正常关闭旧实例，校验旧哈希并备份后安装；试作脚本未改。新 JAR SHA-256 为 `ba3a46f6b3ffdf76ac2b6b896652079f036e17a1d94d39ab00ad4c7ef6490299`。新 PID 7140、MCP 9876，截图确认主菜单已最大化，保留 `guiScale:3`。启动日志：`common/build/dashboard-jsx-scrollbar-launch.log`；安装证据：隔离实例 `evidence/error-dashboard-jsx-20261105/acceptance.json`。
- 已通过选项工具交给用户手测滚动条可见性、位置、拖动和轨道翻页，结论待回复；没有代替用户操作验收，也没有新增 GPU 性能结论。

## 滚动条宽度减半

用户按上述检查反馈“没有问题”，确认滚动条可见性、位置、拖动与轨道操作正常，随后要求宽度减半。原生几何将基准厚度从 6 改为 3 设计／逻辑单位，最小可绘制厚度从 2 改为 1 逻辑像素；轨道和滑块同步变细，横向条对应减小高度。现用 GUI 缩放 3 的试作设计比例下，厚度由 4 变为 2 逻辑像素。整数像素取整仍适用。

不改变颜色、主轴长度、滚动比例或事件／捕获流程，脚本与文档 bundle 内容未变。本轮复用既有原生滚动条与详情滚动回归，不增加镜像实现的测试。构建和安装结果见下，新宽度观感仍待用户确认。

减半版本验证：`./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :26.1.2:build :26.2.0:build --console=plain` 通过（1 分 6 秒），两节点各 835 项、零失败／错误、85 跳过，各 3 项 platform gate 通过，guardLint 零警告。日志：`common/build/dashboard-jsx-scrollbar-half-width-build.log`。四类 CI gate 通过，未受影响节点复用既有输出；common 与声明未变，本轮未重复 `:common:check`、Probe 类型链或 wiki 工具测试。`git diff --check` 通过。

两个新原生 JAR 各 36 项 AI payload 与规范源／manifest 哈希一致，宿主及滚动条类与编译输出一致，bundleId 保持不变。证据：`common/build/dashboard-jsx-scrollbar-half-width-artifact-audit.json`；四类 gate 报告：`common/build/dashboard-jsx-scrollbar-half-width-ci-gates.json`。

已正常保存退出旧实例，校验哈希并备份后安装减半版；新 JAR SHA-256 为 `7d23cdeff60db74657d632135f7bfed79eba2ad62f33b61f0ccbadb1745d72f5`。新 PID 23412、MCP 9876，截图确认主菜单最大化，GUI 缩放仍为 3。日志：`common/build/dashboard-jsx-scrollbar-half-width-launch.log`；安装记录仍在同一 acceptance.json。已通过选项工具交给用户确认新粗细与拖动手感，本轮观感结论待回复。

维护者通过选项工具反馈“粗细合适，拖动正常”。本轮减半宽度在 Error 与原始详情中的观感、滑块拖动验收通过，沿用当前默认宽度；此前滚动条可见性、位置与轨道翻页也已获用户确认。结论仅覆盖当前 NeoForge 26.1.2 隔离实例及上述已执行范围，不扩大为 26.2.0 实机、横向条、其他 GUI 缩放或 GPU 性能验收。
