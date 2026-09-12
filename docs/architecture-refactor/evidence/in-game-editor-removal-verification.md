# 游戏内编辑器移除实施验证记录

- 日期：2026-09-10
- 性质：实施验证记录；本文不重新调研、不扩展产品方案。
- 范围：移除游戏内脚本编辑器与脚本文件同步链路，保留只读错误面板与外部 IDE/Probe 生态。
- UI 状态：本轮未做视觉重设计；两版 NeoForge 错误面板保留旧布局，仅移除编辑分支与文件操作并保持 `fullDetails` 只读显示。记录生成时 HTML UI 样例尚未完成或确认；后续 HTML 交付状态以 [专项说明](../editor-removal-and-error-ui.md#6-本轮交付与后续确认) 为准，原生新布局仍待用户确认。

## 实际更改

- 移除 `/nekojs editor` 与 `OpenWorkspacePacket` 入口；无旧命令 alias。
- 删除共享树与 1.21.1 的 `NekoWorkspaceScreen` / `NekoWorkspaceActions` / `NekoCodeEditor` / `NekoTabbedEditor` / 编辑器专用 `NekoMenuBar` / `NekoModal`；删除无消费者的 `JSHighlighter`。
- 删除 8 个编辑/文件同步 payload 与 handler：`OpenWorkspacePacket`、`SaveScriptPacket`、`FetchScriptRequestPacket`、`FetchScriptResponsePacket`、`FetchAllScriptsRequestPacket`、`UploadAllScriptsPacket`、`DownloadAllScriptsPacket`、`SyncFeedbackPacket`。
- 删除 `ScriptSyncService`、`ScriptSyncFiles`、`NekoJSPaths.verifyScriptSyncPath` 及专属测试。
- 两版 `NekoErrorDashboardScreen` 移除编辑状态、tab 编辑器、保存/上传/下载、new/delete、open-tab、modal 与编辑输入分支；保留现有列表、搜索、过滤、选中、`fullDetails` 只读视图、复制、打开日志和定位。
- 双击错误只更新选中项；定位先确认 singleplayer、路径受限于 NekoJS root 与脚本根且为常规文件，远端或不可用路径不再被当作本地文件打开。
- 删除 `McClientCompat.dashboardLoadServerScript` 与三个 provider override；`currentScreen/showScreen` 保留。
- 删除 editor 专用 `MultiLineEditBox` / `MultilineTextField` AT 与中英文 locale key；新增只读定位不可用文案。
- 保留 `ShowErrorListPacket`、`ErrorSummaryDTO`、`refreshOpenErrorDashboard`、PData/ClientData、脚本自定义网络、PackSync/pack 分发、`WorkspaceGenerator`、Probe 与外部 IDE 配置。
- 未修改 Gradle、版本树配置、构建版本号、CI、payload registrar 协议号或 mod 版本；`NekoJSNetwork` 仍为 `registrar("1")`。
- 通用 `NekoContextMenu` 与 `NekoToast` 未删除，仍由只读错误面板使用。

## 删除目标检查

在同一个 PowerShell 进程中先逐个 `Resolve-Path`，确认 25 个绝对目标均位于 `D:\mcmodDemo\NekoJS\NekoJS-mult\` 下，再逐个 `Remove-Item -LiteralPath`，最后逐个复查不存在。检查通过，目标数 25。完整记录：

`C:\Users\11515\AppData\Local\Temp\nekojs-editor-removal-delete-check-20260910-191040.log`

## 测试与构建验证

所有 Gradle 进程显式使用 `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2` 与该 JDK 的 `bin` 前置 PATH。

### 成功

- 改前基线 `.\gradlew.bat :common:test --console=plain`：exit 0。
  - `C:\Users\11515\AppData\Local\Temp\nekojs-editor-removal-pre-common-test-20260910-185542.log`
- 改后 `.\gradlew.bat :common:test --console=plain`：exit 0。
  - `C:\Users\11515\AppData\Local\Temp\nekojs-editor-removal-post-common-test-20260910-191141.log`
- 五节点 `compileJava` retry：1.21.1、26.1.2、26.2.0、26.1.2-fabric、26.2.0-fabric 全部 exit 0。
  - `C:\Users\11515\AppData\Local\Temp\nekojs-editor-removal-post-five-compileJava-retry-20260910-191408.log`
- `npm run test:probe-types`：exit 0。
  - `C:\Users\11515\AppData\Local\Temp\nekojs-editor-removal-post-npm-test-probe-types-20260910-191438.log`
- 最终组合 `guardLint :common:test :1.21.1:test :26.1.2:test :26.2.0:test :26.2.0-fabric:test :26.1.2-fabric:compileJava`：exit 0；guardLint 为 249 个守卫块、318 个文件、0 警告。
  - `C:\Users\11515\AppData\Local\Temp\nekojs-editor-removal-final-verified-20260910-192217.log`
- 新增 `NekoJSCommandsEditorRemovalTest` 在三个 NeoForge 节点均 tests=1/failures=0/errors=0，证明 editor 节点消失且 `error`、`view_all_errors` 保留。
- 新增 `ShowErrorListPacketReadOnlyContractTest` 在三个 NeoForge 节点均 tests=1/failures=0/errors=0，证明 `fullDetails` 冻结快照与 `openIfMissing=false` round-trip 保留。
- 外部 IDE 保留由既有 `WorkspaceGeneratorManagedTypesTest` 覆盖：tests=7/failures=0/errors=0。
- 精确代码/资源扫描：8 个 payload 类名与 wire id、workspace/editor 组件、`dashboardLoadServerScript`、`ScriptSyncService/Files`、`verifyScriptSyncPath`、`literal("editor")` 均 0 命中；保留链路 `ShowErrorListPacket`、`ErrorSummaryDTO`、`getFullDetailText`、`WorkspaceGenerator`、Probe、脚本自定义网络、PackSync、`refreshOpenErrorDashboard` 均仍有命中。
- `git diff --check` exit 0。

### 失败后修复

- 第一次五节点 compile 因本轮 dashboard 方法替换产生重复 `@Override` exit 1；修复后 retry 通过。
- 第一次 five-test 中新增命令测试误用 `RegisterCommandsEvent(dispatcher)` 一参构造 exit 1；为本轮新增测试问题，已改为三参构造并复验通过，不归为既有失败。
- 一次中间格式化引入字面 `\r\n` 导致 compile exit 1；修复后最终组合验证通过。

## 未解决与未验证边界

- `:26.1.2-fabric:processResources` 在组合与单独复现中均 exit 1：`Entry nekojs-fabric-dynamic.mixins.json is a duplicate but no duplicate handling strategy has been set.`
- 该失败未归因：未以旧 revision 同输入复现，因此不能称为既有失败。本轮未修改该 Fabric resource source set、mixin JSON 或 duplicate strategy，也未删除资源或设置 `EXCLUDE` 掩盖。该节点 `compileJava` 通过，`test` 被 `processResources` 阻塞。
- 未启动真实游戏、run 客户端或服务器 smoke；游戏内视觉、实际命令树展示、错误截图和真实外设交互未验证。
- 旧版 client/server 混用兼容未验证；协议号保持 `"1"` 只是代码事实，不构成兼容结论。
- Fabric 不新增 dashboard；本轮仅要求保留既有文本降级与外部 IDE/Probe 链路。
- 用户脚本、配置、存档、world `nekojs_packs/` 未被本实施修改；未执行真实游戏迁移。
