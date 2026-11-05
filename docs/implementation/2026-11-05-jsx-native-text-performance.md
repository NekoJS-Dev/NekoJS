# JSX 原生长文本性能与原文验收场景

## 需求与范围

用户确认调用栈独立滚动正常，要求直接验证原始详情，并反馈展开后持续卡顿、滚动时也明显。用户进一步要求尽量由 Java 侧承担优化，减少脚本作者职责。

本轮只在 Java JSX 宿主完成性能优化：可见行绘制、共享排版复用与单次排版的短前缀测量复用。没有让脚本分页、维护测量缓存或截断完整数据；common 的换行算法与公共 API 不变。试作仅增加“长原文”演示，保留原有四组 HTML fixture，六字段结构不变。

## 可复现测量

`NativeDashboardScrollTest.expandedStackPaintWorkStaysWithinItsVisibleViewport` 加载实际试作源码，使用真实编译器、原生字体入口和滚轮路由，记录字体调用数、绘制提交量和计时。CPU 夹具中的字体测量／绘制记录不代表真实 GPU 帧率；冷启动单次耗时含类初始化与 JIT，不能用它宣称实机耗时改善。时间样本单独记录，回归门禁使用确定性的工作量与内容可达性。

基线命令：`./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' :26.1.2:test --tests '*NativeDashboardScrollTest.expandedStackPaintWorkStaysWithinItsVisibleViewport' --console=plain`。

基线在可见行预算断言失败：调用栈仅约 17 行可见（门禁容许 19），却提交了全部 60 个 callback 标记行；整帧 314 次文本绘制。展开触发 582588 次字体测量、50141801 个字符的测量输入；一次未改变内容的滚动触发 21164 次测量、1606793 个字符。证据：`common/build/dashboard-jsx-stack-performance-baseline.json` 与 `dashboard-jsx-stack-performance-red.log`。

## 分步验证与实现

1. 假设屏幕外长文本仍被提交。Java `paintTextLines` 根据 committed clip 与缩放字号计算行窗口，仅提交可见行。仅做此变更时，整帧提交降至 77，callback 标记行降至 5；测量量不变，区分了绘制与排版成本。证据：`dashboard-jsx-stack-performance-culling.json`。
2. 假设滚动重复换行。`UiTextLayoutCache` 由单个 host 拥有，供公共测量入口与原生文字准备共同使用；键包含文本、字体、有效字号、宽度、换行及截断方式。未改内容的滚动测量降至 0。
3. 假设重复长路径反复测量相同前缀。独立测试先出现 16560 次测量并失败；单次排版内复用精确字宽结果后低于 2000 次，并与原换行算法的完整结果比较。没有用字宽估计、二分单调性假设或脚本切片替代字体适配器。

持久排版缓存限制为 256 项及 1048576 个 UTF-16 单位（源字符串与结果行合计），按最近使用顺序淘汰；超大结果可正常返回但不保留。单次排版的前缀表只保留至多 2048 个、长度至多 256 的短字符串，构建结束即释放。字体资源 revision 或显式 invalidate 清空旧结果；宿主／generation 最终释放时清空缓存。owner-thread 与过期 generation 的检查仍先于缓存访问。普通关闭失败仍保持可重试资源。

定向检查已观察到展开测量降至 2355 次、326045 个字符；新增“长原文”按钮后整帧提交为 78，调用栈标记为 5，滚动测量为 0。最终完整构建后的计数以生成的性能报告为准，不把单次夹具时间当作实机结论。

## 原始详情的直接验收入口

新增“长原文 / Long raw”场景，原始详情含 `Raw snapshot line 000` 至 `159`，后接原有调用栈。无需修改脚本或依赖测试内部注入即可观察原始详情内的独立滚动。common 模块测试验证完整原文复制含第 159 行；原生回归验证框内滚动、内外偏移独立、末行 159 与 callback59 在滚动末端实际提交绘制，防止可见行优化漏画末行。

## 验证与剩余工作

定向字体、原生滚动、鼠标与 box painter 检查已通过；英文内嵌手册与双语 wiki 已说明由 Java 宿主自动承担这些工作。完整 common、两个支持节点构建和 guardLint 已通过，新 JAR 已安装并重启。原文滚动、展开后的持续流畅度和滚动手感等待用户验证；尚无本轮真实 GPU 或维护者性能验收结论。

## 最终验证与安装

- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :26.1.2:build :26.2.0:build --console=plain`：全部通过（2 分 14 秒），common 2736 项、零失败／错误、4 跳过；两节点各 821 项、零失败／错误、85 跳过，各 3 项 platform gate 通过。未切换 Java 21 测试工具链或刷新 golden。
- wiki 严格检查 30 双语主题零问题，8 项 wiki 工具测试通过；原四组 HTML 数据逐项比较不变，新增长原文恰为六字段、含第 159 行；四类 CI gate 通过（未受影响节点复用既有构建元数据），`git diff --check` 通过。
- 四个新 JAR（common、authoring stdio、两个受影响节点）各 36 项 AI payload 与规范源／manifest 字节及哈希一致；Host、资源 resolver 与新缓存类与最终编译输出一致。证据：`common/build/dashboard-jsx-stack-java-artifact-audit.json`，bundleId 为 `44315967bffc8d0329461e6bddac9d846022c3c55b7948464aca1977a3eeb13a`。
- 最终夹具计数：展开测量 2355 次、326045 个字符；整帧提交 78，callback 标记行 5；滚动测量 0。热态 7 样本中位数为绘制约 0.980 ms、滚动约 1.466 ms，仅为本机 CPU 夹具观察，不和冷启动基线耗时直接比较，也不宣称真实 GPU FPS。证据：`common/build/dashboard-jsx-stack-performance-final.json`。
- 沿用此前授权，已通过 MCP 正常保存退出、正常关闭游戏，备份旧 JAR／脚本后安装；没有强杀进程。新 26.1.2 JAR SHA-256 为 `ab760147cd4865897a114593fadb0c3f26473ede13be42c8b035ea9805f06ee7`。实例 PID 2928、MCP 9876，截图确认主菜单为大窗口，保留用户当前 `guiScale:3`。
- 用户已确认此前调用栈独立滚动可用；本轮长原文独立滚动及 Java 优化后的实际手感仍待用户进世界确认，没有扩大为完整界面或 GPU 验收通过。

## 后续实机反馈与小数边界修订

用户反馈调用栈可滚动，原始详情与 Error 仍不能。后续回归确认旧绘制行距在部分设计缩放下逐行向上取整，导致排版末端仍有文字落在裁剪范围之外；此外，Error 消息此前没有独立滚动区域。真实失败、Java 修复、四种视口的末行检查和最新完整构建见 [小数缩放与三个独立滚动区域](2026-11-05-jsx-fractional-text-scroll.md)。上述 821 项为上一阶段结果，最新各节点为 827 项；新修订的物理输入与性能结论仍待用户确认。
