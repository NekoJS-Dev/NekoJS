# Workspace 默认细描边

## 范围与方案

用户要求默认 Windows 风格的极细窗口描边，以区分相邻／重叠窗口。保留已验收的边框指针、缩放高亮及标题栏按钮悬停，不修改命中范围或脚本 API。

在窗口子内容之后、缩放悬停高亮之前绘制完整内描边。使用灰色，活动窗口略亮、非活动窗口更暗；宽度按 Minecraft GUI scale 换算为约一个 framebuffer 像素，不让 GUI 放大同时加粗描边。沿用既有窗口裁剪、层序和像素边界，不新增配置或依赖。

## 进度

- 已阅读 CONTEXT、编码规范、当前 diff、绘制流程及原生回归。
- 使用真实 Screen render extraction fixture 验证 GUI scale 1／2／3／4 下的四边厚度、内容之后的绘制顺序、悬停高亮优先、无标题栏及隐藏状态。
- 实现和自动验证已完成；2026-11-05 用户明确确认本次 26.1.2 默认描边观感及原有悬停反馈通过，范围见文末。

## 实现与影响

`JsxHostAdapter.paintWindowOutline` 在局部 pose 中抵消 GUI scale 后绘制一像素 outline，finally 恢复 pose。活动窗口为 `#697582`，非活动窗口为 `#454D57`。描边在既有窗口范围及裁剪内，先于已有缩放高亮；无标题栏时仍显示，隐藏或零尺寸窗口不绘制。只改变默认外观，不改窗口内容身份、布局、命中、输入、Script API、声明或支持范围。

沿用既有 Minecraft API；没有新增依赖、配置、原生资源或版本号。英文内嵌手册和双语 wiki 已更新，资料 manifest 经原有流程生成。

## 验证命令与结果

Gradle daemon 为 Zulu 25，保留既有测试工具链与 golden。

| 检查 | 命令／流程 | 结果 |
| --- | --- | --- |
| 描边 RED → GREEN | 两节点 `test --tests com.tkisor.nekojs.client.ui.NativeWindowOutlineTest` | 实现前各 4 项 assertion failure，0 errors／skips，均缺少四边描边；最终各 4 项通过，GUI scale 1／2／3／4。 |
| 全部原生 UI | 两节点 `test --tests 'com.tkisor.nekojs.client.ui.*'` | 各 191 项、0 失败／错误、2 跳过；覆盖前轮按钮及缩放反馈。 |
| 受影响节点构建 | `:26.1.2:build :26.2.0:build` | 全部通过，含 platformGateTest；普通 test 各 811 项、0 失败／错误、85 跳过。 |
| 资料与守卫 | `guardLint :common:assemble :common:test --tests com.tkisor.nekojs.authoring.AuthoringBundleTest` | 全通过，资料 bundle 检查数见下方；隔离 common 引擎未改，未重复完整 `:common:check`。 |
| 其他节点资源打包 | `:1.21.1:jar :26.1.2-fabric:jar :26.2.0-fabric:jar` | 通过；此增量不宣称重新完整构建这三个不支持原生 JSX 的节点。 |
| CI gate | `python tools/nekojs-ci-gates.py all --out common/build/window-outline-ci-gates.json` | 四类 gate 全通过。 |
| Wiki | `node scripts/wiki-docs.mjs sync`、`stamp jsx-client-ui`、`check --strict` | 30 双语主题、0 问题；仅增补说明，无代码块变更。 |
| 产物 | `common/build/window-outline-artifact-audit.json` | 七个主要 JAR 各 36 个 AI payload 与规范源／哈希一致；两个支持节点 Host class 与编译输出一致，其他节点无原生 Host 类。 |
| 最终差异 | `git diff --check`、新文件空白及完整 diff 审阅 | 通过，仅换行提示；保留此前改动及所有无关文件。 |

`AuthoringBundleTest` 为 13 项、0 失败／错误、0 跳过。资料 bundleId 为 `db22b8679ebd5caa4c321976843cff4627a821deb7f716d1dde3d3bff717ce65`。本轮构建日志为 `common/build/window-outline-build.log`，原生 UI 日志为 `common/build/window-outline-ui.log`，初始 RED 日志为 `common/build/window-outline-red.log`。

## 实机与剩余范围

部署前计划为安装并重启隔离游戏，由用户观察描边粗细、活动／非活动灰度及悬停高亮；已按后续授权及用户反馈完成本次 26.1.2 检查。前轮指针／按钮验收不替代本轮描边反馈。26.2.0 实机及逐 GUI scale 的实际像素厚度测量仍未运行；CPU fixture 测量变换后几何，用户反馈确认实际观感，两者不等同于标定后的 framebuffer 像素测量。既有启动就绪性问题仍未修复。不涉及 NBT、类型生成或公共 API，未重复 NBT smoke 和 Probe 类型链。没有提交、推送或升版。

## 已授权部署与重启

用户明确选择安装并重启为大窗口、由用户确认描边观感。通过系统正常关闭旧 PID 5160，日志确认全部世界维度存盘，等待退出后备份并替换本体。部署 SHA-256 为 `4967baa794d80278619f55d3303218c44094b2c2608a03289fa761ee43e95225`，与已验证构建一致；旧版保存在 `evidence/window-outline-20261105/nekojs-before-window-outline-db3e905d801d.jar`。MCP 在同一隔离目录启动 PID 21820，主菜单与大窗口已截图确认；尚未替用户操作游戏内验收。

## 最终人工结论（2026-11-05）

用户明确反馈：**“描边粗细和颜色合适，窗口边界清楚，悬停反馈也正常”**。本次已执行的 NeoForge 26.1.2.71 默认细描边观感检查人工通过，覆盖重叠窗口区分、活动／非活动灰度及原有缩放／按钮悬停反馈。

结构化反馈和最终日志已保存到 `evidence/window-outline-20261105/`。实际 GUI scale 矩阵和标定像素测量、26.2.0 实机仍未运行；既有启动就绪性问题保留。自构建验证后生产代码未再修改，仅更新本记录，因此未重复构建。游戏保持运行；没有提交、推送或升版。
