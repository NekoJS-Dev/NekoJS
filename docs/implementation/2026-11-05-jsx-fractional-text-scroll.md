# JSX 小数缩放文本末行与三个独立滚动区域

## 用户反馈与复现

Java 长文本优化版安装后，用户反馈“调用栈能滚动，但原始详情和 Error 不能”。截图不能证明所选演示场景或全部内容可达，因此没有把反馈归因为操作错误。新增真实原生回归后，确认小数缩放存在绘制高度与排版高度不一致的问题；Error 消息此前也没有独立滚动区域。

原生绘制将每行逻辑高度向上取整，再逐行累加；排版总高则对完整未取整高度做一次取整。例如字号约 6.0047 时，160 行排版总高约 961，而旧绘制每行推进 7，实际约需 1120。滚到排版末端仍有末行处于裁剪范围之外。此前 426 宽的夹具没有覆盖相邻 427 宽或当前 854 宽下的这个边界。

## 实现与行为

`JsxHostAdapter.paintTextLines` 使用基础字体行距推进，再由 pose 一次缩放；可见行窗口使用相同的精确行距。修复由 Java 宿主自动承担，脚本作者无需计算小数补偿、额外留白或裁切文本。排版缓存、可见行绘制、资源失效与 generation 清理保持原有边界，公共 API 与 common 换行算法未变。

试作保留双栏、暗红消息框及原始详情／调用栈样式，为 Error 消息增加自然高度、最大 96 设计单位的独立 scroll。原始详情／调用栈继续为最大 160。长原文场景额外提供 Error 000–079、Raw snapshot 000–159，复制仍含完整原文；当前演示按钮与场景名高亮，避免混淆。原 HTML 四组 fixture 保持不变。

## 验证

- `NativeMultilineTextScaleTest` 在字号 3.00234375、6.0046875、9 下验证第 159 行实际绘制。旧实现前两组失败，基础字号通过；修复后三组通过。失败证据：`common/build/dashboard-jsx-fractional-lines-red.log`。
- `NativeDashboardScrollTest` 加载规范 TSX，经真实原生布局和滚轮路由，在 426×250、427×250、854×499、1280×720 四种视口分别验证 Error 第 079 行、原文第 159 行和最后 callback59 的绘制、内外偏移独立。两支持节点聚焦检查通过：`common/build/dashboard-jsx-fractional-three-panes-check.log`。
- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :26.1.2:build :26.2.0:build --console=plain` 全部通过（4 分 21 秒）：common 2736 项、零失败／错误、4 跳过；两节点各 827 项、零失败／错误、85 跳过，各 3 项 platform gate 通过。guardLint 扫描 610 文件，零警告。日志：`common/build/dashboard-jsx-fractional-scroll-build.log`。保留 Java 21 common 测试工具链，没有刷新 golden。
- wiki 严格检查 30 双语主题零问题，8 项工具测试通过。四个新 JAR 各 36 项 AI payload 与规范源及 manifest 哈希一致；两原生 JAR 的宿主、资源 resolver 与缓存类与编译输出一致。证据：`common/build/dashboard-jsx-fractional-scroll-artifact-audit.json`，bundleId 为 `81d1ba4a784d327d136c7f6acf4a093d2ba84b18ca4398394af8fb39abd790ff`。未重建其余三个节点，未重复四类全节点 CI gate。

## 实机边界

本轮三个文本区域的物理输入与实际流畅度等待用户确认；既有调用栈可滚动反馈不扩大为原文、Error 或性能验收通过。仅独立试作，未接入生产错误数据；GPU 表现、其他节点、region 与内部资源泄漏没有本轮人工结论。安装／重启信息记录在隔离实例的 `evidence/error-dashboard-jsx-20261105/acceptance.json`。

已沿用此前授权，通过 MCP 正常保存退出并关闭实例；校验旧版本哈希、备份后安装新 JAR 与规范试作源码。新 JAR SHA-256 为 `0b82f661b40c289ca68c42581e161d4d6bb325ae7feb6454171fd37fdca21f83`。PID 29672、MCP 9876，截图确认主菜单已最大化，保留用户 `guiScale:3`；没有代替用户进行界面操作验收。启动日志为 `common/build/dashboard-jsx-fractional-scroll-launch.log`。

用户随后按两个区域的独立滚动及末行检查反馈“没有问题了”，并要求增加可见滚动条以判断位置。该范围验收通过，性能仍未获得独立结论；后续实现见 [原生默认滚动条记录](2026-11-05-jsx-native-scrollbars.md)。
