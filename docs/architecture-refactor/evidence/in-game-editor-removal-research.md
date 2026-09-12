# 游戏内编辑器移除影响调研

> 调研日期：2026-09-10
> 范围：NekoJS 游戏内工作区编辑器及其保存/同步/文件管理链路。只读调查，不执行 Gradle，不修改用户脚本、配置、存档或世界数据。
> 结论状态：代码边界已收敛；最终报错 UI 布局等待 HTML 参考，不在本调研内定型。

## 1. 结论

本项应删除的是“游戏内编辑脚本”这一整条产品链路：

- `/nekojs editor` 命令入口。
- `OpenWorkspacePacket` 打开游戏内屏幕的入口。
- `NekoWorkspaceScreen`、单选/多标签编辑器、菜单和本地文件管理。
- 保存、上传、下载、单文件拉取、全量推送、全量拉取及同步回执的 8 个 payload。
- 与这些 payload 配套的 `ScriptSyncService`、`ScriptSyncFiles` 和服务端直接写文件逻辑。

必须保留的是“看错误”，不是“在游戏里改脚本”：

- `/nekojs error`、`/nekojs view_all_errors`、错误刷新和 `ShowErrorListPacket`。
- `ErrorSummaryDTO` 及其 `fullDetails`。其中已经包含冻结错误报告时生成的多行源码摘录，因此只读源码摘录不需要保留脚本下载协议。证据：`common/src/main/java/com/tkisor/nekojs/core/error/ScriptError.java:160-195,250-272,411-431`，`common/src/main/java/com/tkisor/nekojs/network/ErrorSummaryDTO.java:3-10`。
- `NekoErrorDashboardScreen`；移除编辑分支后，现有 `StackTraceList` 可直接作为只读错误与源码摘录视图。证据：`src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java:165-169,222-239`。
- 外部 IDE 工作区生成、Probe、类型声明和编辑器配置。它们与游戏内 `NekoWorkspaceScreen` 同名概念相邻，但不是同一功能。`WorkspaceGenerator` 生成 `jsconfig.json`、README 和 Probe 配置，并在脚本目录、包目录与 `.neko_probe` 下建立 IDE 类型映射。证据：`common/src/main/java/com/tkisor/nekojs/script/WorkspaceGenerator.java:24-38,61-69,88-108,160-190`。
- 用户脚本、配置、`.neko_probe`、脚本包和世界持久数据。

移除工作不得触碰运行时数据根。规范路径包括 `nekojs/{startup_scripts,server_scripts,client_scripts,test_scripts,config,assets,data,packs}`、`.neko_probe` 与 `server_packs`；启动逻辑只创建缺失目录。证据：`common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java:60-84,109-121`。世界脚本包固定位于 `<world>/nekojs_packs/`，证据：`common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackScope.java:9,19`、`common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java:15,27,86-92`。Fabric 的实体持久数据随实体 NBT 保存，证据：`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataMixin.java:17-22,31-38,46-65`。

## 2. 精确边界

### 2.1 游戏内编辑器链路

NeoForge 命令树只有一个编辑器命令，没有单独的保存、上传、下载、新建或删除子命令：

- `/nekojs editor` 发送 `OpenWorkspacePacket`。证据：`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:111-118`；1.21.1 孪生同构，证据：`versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:105-112`。
- `OpenWorkspacePacket` 的 wire id 是 `nekojs:open_workspace`。证据：`src/main/java/com/tkisor/nekojs/network/OpenWorkspacePacket.java:10-18`。
- 客户端收到后直接打开 `NekoWorkspaceScreen`。证据：`src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java:97-99,140-142`。
- `NekoWorkspaceScreen` 自身包含文件扫描、缓存、搜索替换、新建、删除和编辑打开。证据：`src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceScreen.java:29-38,173-180,401-451,456-497,501-518`。
- 新建和删除没有专用网络包或命令，它们是本地 UI 方法直接操作 `nekojs/` 文件：新建在 `NekoWorkspaceScreen.java:456-477`，删除在 `NekoWorkspaceScreen.java:482-497`。右键菜单把这两项暴露给 UI：`NekoWorkspaceScreen.java:658-663,843-848`。

### 2.2 保存、上传与下载链路

`NekoWorkspaceActions` 同时实现本地保存和 4 种同步动作：

- 本地保存直接写脚本文件。证据：`src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceActions.java:45-57`。
- 当前文件上传/下载分别发送 `SaveScriptPacket` / `FetchScriptRequestPacket`。证据：`NekoWorkspaceActions.java:59-73`。
- 全量上传/下载分别发送 `UploadAllScriptsPacket` / `FetchAllScriptsRequestPacket`。证据：`NekoWorkspaceActions.java:75-86`。
- 错误仪表盘为了编辑器又复制了一套同样的保存、同步和外部打开动作。证据：`src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java:265-326`。

服务端处理链：

- `FetchScriptRequestPacket -> FetchScriptResponsePacket` 读取脚本。
- `SaveScriptPacket` 覆盖脚本。
- `FetchAllScriptsRequestPacket -> DownloadAllScriptsPacket` 全量下发。
- `UploadAllScriptsPacket` 全量覆盖服务端脚本。
- 上述结果均通过 `SyncFeedbackPacket` 回传。

证据：`src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java:146-216`。实际读写由 `ScriptSyncService` 完成：`readScript` 在 `26-29`，`saveScript` 在 `31-37`，`collectAllScripts` 在 `39-56`，`writeBatch` 在 `58-68`。批量集合和路径校验由 `common/src/main/java/com/tkisor/nekojs/network/ScriptSyncFiles.java:16-92` 完成。

这 8 个可删除 payload 及其 id 为：

| Payload | id |
|---|---|
| `OpenWorkspacePacket` | `nekojs:open_workspace` |
| `SaveScriptPacket` | `nekojs:save_script` |
| `FetchScriptRequestPacket` | `nekojs:fetch_script_req` |
| `FetchScriptResponsePacket` | `nekojs:fetch_script_res` |
| `FetchAllScriptsRequestPacket` | `nekojs:fetch_all_scripts_req` |
| `UploadAllScriptsPacket` | `nekojs:upload_all_scripts` |
| `DownloadAllScriptsPacket` | `nekojs:download_all_scripts` |
| `SyncFeedbackPacket` | `nekojs:sync_feedback` |

id 证据依次为：`OpenWorkspacePacket.java:11`、`SaveScriptPacket.java:11`、`FetchScriptRequestPacket.java:11`、`FetchScriptResponsePacket.java:11`、`FetchAllScriptsRequestPacket.java:11`、`UploadAllScriptsPacket.java:15`、`DownloadAllScriptsPacket.java:15`、`SyncFeedbackPacket.java:11`。

`SyncFeedbackPacket` 名字含 Sync，但它不是“错误同步”；全部发送点都在上述脚本文件同步 handler 中。证据：`NekoJSNetwork.java:89-91,146-216`。因此应删除；`ShowErrorListPacket` 才是错误同步，必须保留。

### 2.3 错误同步与只读源码摘录

保留链路：

- `/nekojs view_all_errors` 把 `ErrorSummaryDTO` 列表发送给玩家。证据：`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:97-108`。
- reload/test 生命周期通过 `refreshOpenErrorDashboard` 刷新已打开的错误仪表盘，`openIfMissing=false` 避免意外重新弹窗。证据：`NekoJSCommands.java:389-396,476-495`。
- wire payload 为 `nekojs:show_error_list`，字段是 `id/path/line/count/message/fullDetails`，单个 `fullDetails` 上限 262144。证据：`src/main/java/com/tkisor/nekojs/network/ShowErrorListPacket.java:13-34,37-47`。
- 网络注册和客户端打开/更新屏幕位于 `NekoJSNetwork.java:36,81-83,105-111`。

`ScriptError` 在错误发生时就生成源码摘录：

- 读取脚本或 embedded source，选择错误行前后各 2 行，并标出错误行与列。证据：`ScriptError.java:160-195`。
- concise/full detail 都把摘录写入文本。证据：`ScriptError.java:256-273,411-431`。
- `errorSnapshot()` 把 `getFullDetailText()` 放入 `ErrorSummaryDTO.fullDetails`。证据：`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:464-473`。
- 仪表盘的非编辑分支已经逐行渲染 `fullDetails`。证据：`NekoErrorDashboardScreen.java:165-169,222-239`。

因此最小删除实现应让仪表盘永远走这个非编辑分支；不要为了“只读源码”保留 `FetchScript*`、`SaveScriptPacket` 或 `dashboardLoadServerScript`。

`dashboardLoadServerScript` 是编辑器专用 seam：26.x 实现把拉回内容 set 到活动编辑器并标 saved，1.21.1 实现还要求 path 匹配当前 tab。证据：`src/main/java/com/tkisor/nekojs/platform/compat/McClientCompat.java:58-59`，`src/main/java/com/tkisor/nekojs/platform/compat/Nf1211ClientCompat.java:45-48`，`versions/26.1.2/src/main/java/com/tkisor/nekojs/platform/compat/Nf261ClientCompat.java:42-45`，`versions/26.2.0/src/main/java/com/tkisor/nekojs/platform/compat/Nf262ClientCompat.java:43-46`。删除文件同步后，该接口方法和三个实现都应删掉。

### 2.4 外部 IDE WorkspaceGenerator / Probe / 类型声明

这些不是游戏内编辑器，不得随本次删除：

- `WorkspaceGenerator.setupWorkspace()` 创建 README、加载引擎配置并确保 `probe.toml` 存在。证据：`common/src/main/java/com/tkisor/nekojs/script/WorkspaceGenerator.java:33-39`。
- `createWorkspaceConfigs()` 为 SERVER/CLIENT/STARTUP/TEST、脚本包和 `.neko_probe` 生成配置，证据：`WorkspaceGenerator.java:61-69`。
- 插件 `modifyWorkspaceConfig` 和 TypeScript/JSX 配置保留。证据：`WorkspaceGenerator.java:71-108,110-150,193-204`。
- `/nekojs probe` 保留并继续调用 `ProbeCoordinator`。证据：`src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java:503-627`，Fabric 对应入口：`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java:429-547`。
- Probe 配置、backend reset 与 `WorkspaceGenerator.createWorkspaceConfigs()` 的联动保留。证据：`common/src/main/java/com/tkisor/nekojs/probe/ProbeCoordinator.java:105-151`。
- TypeScript 输出、`jsconfig.json` 合并、snippets 与声明生成保留。证据：`common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/TypeScriptProbeBackend.java:307-325,359-394`。
- 已有 `WorkspaceGeneratorManagedTypesTest`、Probe 输出兼容测试和 `npm run test:probe-types` 不参与删除。证据：`common/src/test/java/com/tkisor/nekojs/script/WorkspaceGeneratorManagedTypesTest.java:31-125`、`.github/workflows/ci-build.yml:138-139`。
- `FabricCatalogPlatformProvider` 是 Probe/workspace 目录数据源，不是游戏内屏幕，保留。证据：`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricCatalogPlatformProvider.java:1-4,43-45`。

## 3. 五个 Stonecutter 节点的 source overrides

节点集合来自 `settings.gradle.kts:34-40`；26.1.2 是 active 节点，`stonecutter.gradle.kts:10-11`。NeoForge 约定把共享 `src/main/java` 加节点本地 `versions/<node>/src/main/{java,resources}` 一起编译，节点本地可放“差异过大不便用守卫表达的孪生文件”。证据：`buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:1-7,78-81`。Fabric 约定读取 `deps.fabric_source_node` 并把 `versions/<value>/src/main` 作为 Java/resources/AW 根。证据：`buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:21-25,88-97`。

| 节点 | 实际 source override | 编辑器删除影响 |
|---|---|---|
| `26.1.2` NeoForge primary | 共享 `src/main/java` + `versions/26.1.2/src/main/java/com/tkisor/nekojs/platform/compat/Nf261*.java` 三个 compat 文件 | 改共享 GUI/network/command；删除 `Nf261ClientCompat` 的编辑器 seam；无 GUI 本地副本。入口属性见 `versions/26.1.2/gradle.properties:1-8`。 |
| `26.2.0` NeoForge secondary | 共享 `src/main/java` + `versions/26.2.0/src/main/java/com/tkisor/nekojs/platform/compat/Nf262*.java` 三个 compat 文件 | 与 26.1.2 同一套共享删除；另删 `Nf262ClientCompat` 的编辑器 seam。入口属性见 `versions/26.2.0/gradle.properties:1-8`。 |
| `1.21.1` NeoForge experimental | 共享树中 `<26` 文件/守卫 + 节点本地完整 GUI 孪生 | 除共享删除外，必须同步删除 `versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/{NekoWorkspaceScreen,NekoWorkspaceActions}.java` 及 `components/{NekoCodeEditor,NekoTabbedEditor,NekoMenuBar,NekoModal}.java`；同步改本节点的 `NekoErrorDashboardScreen.java` 与 `NekoJSCommands.java`。 |
| `26.1.2-fabric` | 共享树求值结果 + 自有 `versions/26.1.2-fabric/src/main` | 当前没有 `NekoWorkspaceScreen`、编辑器打开命令或编辑器 8 包；不需要为 Fabric 做删除式等价改动。见 `FabricNekoJSCommands.java:42-54,66-81` 与 `FabricPlayNetwork.java:43-67`。 |
| `26.2.0-fabric` | 无自有 `src/main`；`deps.fabric_source_node=26.1.2-fabric`，借用同一 source root | 与 `26.1.2-fabric` 一同受益；没有第二份编辑器代码可删。证据：`versions/26.2.0-fabric/gradle.properties:1-6`、`buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:92-97`。 |

补充：`1.21.1` 的 GUI 本地副本在 `versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/`；共享 GUI 第一行使用 `neoforge` 整文件守卫，1.21.1 由节点本地同名类承接。证据：`src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceActions.java:1-4`、`src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceScreen.java:1-4`、`versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceActions.java:1-4`。这解释了为什么删除必须同时覆盖共享 26.x 与 1.21.1 本地副本。

## 4. GUI components 共用消费者

| 组件 | 当前消费者 | 结论 |
|---|---|---|
| `NekoWorkspaceScreen` | `/nekojs editor` 的 `OpenWorkspacePacket` 客户端 handler | 删除。唯一 screen 消费者见 `NekoJSNetwork.java:140-142`。 |
| `NekoWorkspaceActions` | `NekoWorkspaceScreen` | 删除；它集中保存和同步动作，见 `NekoWorkspaceActions.java:23-90`。 |
| `NekoCodeEditor` | 仅 `NekoTabbedEditor` | 删除；内部创建 `MultiLineEditBox`，见 `NekoCodeEditor.java:29-32,90-96,142`。 |
| `NekoTabbedEditor` | `NekoWorkspaceScreen`、`NekoWorkspaceActions`、`NekoErrorDashboardScreen` | 删除；目标不是保留多标签编辑，而是把错误页固定到只读详情分支。 |
| `NekoMenuBar` | 仅 `NekoWorkspaceActions` / `NekoWorkspaceScreen` | 删除。错误仪表盘使用自己的 `MenuCategory` 和 `NekoContextMenu`，不消费 `NekoMenuBar`。证据：`NekoErrorDashboardScreen.java:68-90`。 |
| `JSHighlighter` | 仅 `NekoCodeEditor` | 默认删除。只有最终 HTML 参考明确要求 Java 侧只读语法高亮时才保留并复用。 |
| `NekoModal` | workspace 实际 `openInput/openConfirm`；dashboard 只做生命周期检查，未发起 modal | 移除编辑链路后可删除，并从两张错误页移除 modal plumbing。workspace 实际使用见 `NekoWorkspaceScreen.java:658-663,843-848`。 |
| `NekoContextMenu` | workspace、菜单、error dashboard | 保留；error dashboard 的全屏和外部打开右键菜单依赖它，见 `NekoErrorDashboardScreen.java:598-602`。 |
| `NekoToast` | workspace、菜单、error dashboard | 保留；dashboard 的复制、定位、日志反馈仍依赖它，见 `NekoErrorDashboardScreen.java:54,248-263`。 |

## 5. 网络注册、compat、provider、mixin、资源和测试

### 5.1 Network register

`NekoJSNetwork.register()` 当前按以下顺序注册：

- `ShowErrorListPacket`：保留。`NekoJSNetwork.java:36`。
- `FetchScriptRequestPacket`、`SaveScriptPacket`、`FetchScriptResponsePacket`：删除。`NekoJSNetwork.java:37-39`。
- `SyncFeedbackPacket`：删除，只是文件同步回执。`NekoJSNetwork.java:42`。
- `FetchAllScriptsRequestPacket`、`UploadAllScriptsPacket`、`DownloadAllScriptsPacket`：删除。`NekoJSNetwork.java:45-47`。
- `OpenWorkspacePacket`：删除。`NekoJSNetwork.java:50`。
- `PDataSyncPacket`、`ClientDataSyncPacket`、`NekoScriptPayload`：保留。`NekoJSNetwork.java:52-61`。
- PackSync、配置任务、PData/ClientData 的 dispatcher assembly：保留。`NekoJSNetwork.java:32-33,63-78,101-103`。
- Fabric 已单独注册 `NekoScriptPayload` 双向 receiver，和编辑器无关，保留。证据：`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java:43-67`。

`NekoScriptPayload`、`NetworkJS`、`NetworkMessageHandler`、`NetworkEvents`、`PlayPacketDispatcher*` 都是脚本自定义网络通道，不是编辑器协议，不得删除。证据：`src/main/java/com/tkisor/nekojs/network/NekoScriptPayload.java:11-23`、`src/main/java/com/tkisor/nekojs/wrapper/network/NetworkJS.java:17-47`、`src/main/java/com/tkisor/nekojs/network/NetworkMessageHandler.java:10-48`。

### 5.2 Compat/provider

- `McClientCompat` 的 `currentScreen/showScreen` 仍需供 `ShowErrorListPacket` 打开/更新 dashboard，保留。证据：`src/main/java/com/tkisor/nekojs/platform/compat/McClientCompat.java:44-50`、`NekoJSNetwork.java:105-111`。
- `McClientCompat.dashboardLoadServerScript` 仅把服务端拉取文本注入可编辑 tab，删除。证据：`McClientCompat.java:58-59` 与 `NekoErrorDashboardScreen.java:286-291`。
- 三个 NeoForge `McClientCompat` provider 类继续保留，只删除 `dashboardLoadServerScript` override。provider 注册不变：`versions/1.21.1/src/main/resources/META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl:1`、`versions/26.1.2/src/main/resources/META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl:1`、`versions/26.2.0/src/main/resources/META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl:1`。
- `McPlatformCompat.registerScriptPayload` 和三个 `Nf*PlatformCompat` 实现保留，脚本自定义网络不能随编辑器一起删。证据：`src/main/java/com/tkisor/nekojs/platform/compat/McPlatformCompat.java:19-50`、`Nf1211PlatformCompat.java:32-40`、`Nf261PlatformCompat.java:30-36`、`Nf262PlatformCompat.java:29-35`。
- `FabricCatalogPlatformProvider`、`NeoForgeCatalogPlatformProvider` 与 Probe/类型声明 catalog 保留，不作为 GUI 删除项。
- 三个 NeoForge 节点各自的 `McVersionCompat` / `McPlatformCompat` / `McClientCompat` service 文件均保留；本次只修改 Client compat 的接口实现。

### 5.3 Mixin/AT

- 未发现编辑器专用 mixin。modern/legacy/fabric mixin 配置中没有 `NekoWorkspace*`、`NekoCodeEditor`、`NekoTabbedEditor` 目标；例如 `src/main/resources-modern/nekojs.mixins.json:6-26` 只列 reload/registry/recipe/inject/Shader 项，`src/main/resources-legacy/nekojs.mixins.json:6-24` 的 client 列表为空。不要为本次删除改 mixin。
- 唯一明确的编辑器专用访问扩展是 `MultiLineEditBox`/`MultilineTextField`。证据：`src/main/resources/META-INF/accesstransformer.cfg:7-14`。其全部 `MultiLineEditBox|MultilineTextField` 代码消费者就是 `NekoCodeEditor`，因此删除编辑器后可删除 `accesstransformer.cfg:7-14`。Fabric convention 本来会过滤该 AT，见 `buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:153-164`。

### 5.4 资源文案

中英文文案位于 `common/src/main/resources/assets/nekojs/lang/`，不是节点覆盖资源：

- 保留：dashboard title、search/filter、dashboard copy/log/locate、context fullscreen/open_external、copy/locate/log toast、`nekojs.command.error.*`、`nekojs.error.tracker.*`。当前行号见 `en_us.json:6,9-17,33,37-44,67-70,93-101` 与 `zh_cn.json:6,9-17,33,37-44,67-70,93-101`。
- 删除或停止使用：workspace title/editor、save、sync menu、save/edit buttons、open_tab、new file/dir/delete、workspace search/replace、modal new/delete、save/pull/create/delete/replace/toast/error-not-editing 等编辑专用键。当前集中范围见 `en_us.json:7,20-31,34-36,40,43,45-65,71-91` 与 `zh_cn.json:7,20-31,34-36,40,43,45-65,71-91`。
- 最终 HTML UI 可以新增自己的 key，但必须是新增项，不能借此保留已删除的编辑器动作。

### 5.5 测试影响

- 删除：`src/test/java/com/tkisor/nekojs/network/ScriptBatchPacketLimitTest.java`。它全部测试 `UploadAllScriptsPacket` / `DownloadAllScriptsPacket`，见该文件 `31-123`。
- 删除：`common/src/test/java/com/tkisor/nekojs/network/ScriptSyncFilesCollectLimitTest.java`。它测试已删除的批量收集器。
- 保留：`src/test/java/com/tkisor/nekojs/network/NekoScriptPayloadChannelValidationTest.java:11-37`，验证脚本自定义通道而不是编辑器协议。
- 修改：`src/test/java/com/tkisor/nekojs/core/fs/NekoJSPathsTest.java:84-100` 删除 `verifyScriptSyncPath` 测试；对应实现 `common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java:174` 删除。该方法只有 `ScriptSyncService`、`ScriptSyncFiles` 和 workspace actions 三个消费者。
- 保留全部 Probe、types、workspace-generator、PData/world 数据测试。

## 6. 有限文件写集

以下列表按“直接改代码”排列。`D` 表示删除文件，`M` 表示修改，`T` 表示测试，`DOC` 表示随功能删除同步更新，不是新架构。

### 6.1 共享 GUI（26.1.2 / 26.2.0；1.21.1 另有孪生）

- `D src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceScreen.java`
- `D src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceActions.java`
- `D src/main/java/com/tkisor/nekojs/client/gui/components/NekoCodeEditor.java`
- `D src/main/java/com/tkisor/nekojs/client/gui/components/NekoTabbedEditor.java`
- `D src/main/java/com/tkisor/nekojs/client/gui/components/NekoMenuBar.java`
- `D src/main/java/com/tkisor/nekojs/client/gui/components/NekoModal.java`
- `D src/main/java/com/tkisor/nekojs/client/gui/JSHighlighter.java`，除非 HTML 参考明确要求复用为只读高亮
- `M src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java`
- `D versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceScreen.java`
- `D versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/NekoWorkspaceActions.java`
- `D versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/components/NekoCodeEditor.java`
- `D versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/components/NekoTabbedEditor.java`
- `D versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/components/NekoMenuBar.java`
- `D versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/components/NekoModal.java`
- `M versions/1.21.1/src/main/java/com/tkisor/nekojs/client/gui/NekoErrorDashboardScreen.java`

`NekoErrorDashboardScreen` 的删除边界：删除 `isEditing`、`tabbedEditor`、`readFailedTabs`、编辑器菜单/按钮/sync 方法、`openTab/loadServerScript`、编辑输入与渲染分支；保留错误列表、过滤、复制、定位、日志、`fullDetails` 只读显示。26.x 当前证据：`NekoErrorDashboardScreen.java:40-46,72-91,165-207,265-326,385-544`；1.21.1 同类证据：`versions/1.21.1/.../NekoErrorDashboardScreen.java:39-44,69-83,163-204,263-494`。

### 6.2 命令与网络

- `M src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java`
- `M versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java`
- 删除两个 `NekoJSCommands` 中 `Commands.literal("editor")` 分支和 `OpenWorkspacePacket` import；保留 `error`、`view_all_errors`、`refreshOpenErrorDashboard` 与 `probe`。
- `M src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java`：删除 `NekoWorkspaceScreen` import、8 个编辑/文件同步注册、对应 handler、`receiveServerScript/processFeedback/receiveAllScripts/openWorkspace`；保留 `ShowErrorListPacket` 注册、`showOrUpdateDashboard`、PData/ClientData/NekoScriptPayload/PackSync。
- `D src/main/java/com/tkisor/nekojs/network/OpenWorkspacePacket.java`
- `D src/main/java/com/tkisor/nekojs/network/SaveScriptPacket.java`
- `D src/main/java/com/tkisor/nekojs/network/FetchScriptRequestPacket.java`
- `D src/main/java/com/tkisor/nekojs/network/FetchScriptResponsePacket.java`
- `D src/main/java/com/tkisor/nekojs/network/FetchAllScriptsRequestPacket.java`
- `D src/main/java/com/tkisor/nekojs/network/UploadAllScriptsPacket.java`
- `D src/main/java/com/tkisor/nekojs/network/DownloadAllScriptsPacket.java`
- `D src/main/java/com/tkisor/nekojs/network/SyncFeedbackPacket.java`
- `D common/src/main/java/com/tkisor/nekojs/network/ScriptSyncService.java`
- `D common/src/main/java/com/tkisor/nekojs/network/ScriptSyncFiles.java`
- `M src/main/java/com/tkisor/nekojs/NekoJSMod.java`：删除 `ScriptSyncService` import 与 `ScriptSyncService.bindErrorTracker(...)`，保留 `WorkspaceGenerator`。
- `M versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java`：同上。
- `M common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java`：删除只服务脚本同步的 `verifyScriptSyncPath`；不要动 `initFolders`、脚本根、config、assets、data、packs、probe 根。

### 6.3 Compat、AT、文案、测试

- `M src/main/java/com/tkisor/nekojs/platform/compat/McClientCompat.java`
- `M src/main/java/com/tkisor/nekojs/platform/compat/Nf1211ClientCompat.java`
- `M versions/26.1.2/src/main/java/com/tkisor/nekojs/platform/compat/Nf261ClientCompat.java`
- `M versions/26.2.0/src/main/java/com/tkisor/nekojs/platform/compat/Nf262ClientCompat.java`
- 上述四处只删除 `dashboardLoadServerScript`，保留 screen compat。
- `M src/main/resources/META-INF/accesstransformer.cfg`：删除 `MultiLineEditBox`/`MultilineTextField` 四项权限。
- `M common/src/main/resources/assets/nekojs/lang/en_us.json`
- `M common/src/main/resources/assets/nekojs/lang/zh_cn.json`
- `D src/test/java/com/tkisor/nekojs/network/ScriptBatchPacketLimitTest.java`
- `D common/src/test/java/com/tkisor/nekojs/network/ScriptSyncFilesCollectLimitTest.java`
- `M src/test/java/com/tkisor/nekojs/core/fs/NekoJSPathsTest.java`
- `DOC M docs/fabric-port-status.md`：从 P2 删除“编辑器同步 8 包 + 工作区 GUI”，并清理 `FabricPlayNetwork` 附近的历史待移植描述。当前引用：`docs/fabric-port-status.md:29-30,70-83,311-314`。
- `DOC M wiki/命令.md`：删除 `/nekojs editor` 行、对应详解，并修正 Cleanroom 对比句。当前引用：`wiki/命令.md:13-25,113-120`。
- `DOC M wiki/常见问题.md`：删除把 `NetworkEvents + ScriptSyncService` 描述为远程脚本同步的攻击面；脚本自定义 `NetworkEvents` 本身保留。当前引用：`wiki/常见问题.md:158-160`。

不要把 `docs/architecture-refactor/implementation-tickets/27-client-gui-render.md` 当作本轮可执行授权；它仍把“菜单/编辑器”列入 GUI 验收。当前冲突证据：同文件 `24,32,57`。若实施前必须保持规划一致，应先由维护者更新该票，而不是在代码里保留旧编辑器。

## 7. 保留清单

### 7.1 错误与日志

- `common/src/main/java/com/tkisor/nekojs/core/error/`、`ErrorTracker`、`ScriptError`、`ErrorSummaryDTO`。
- `src/main/java/com/tkisor/nekojs/network/ShowErrorListPacket.java`。
- `NekoJSNetwork` 的 `ShowErrorListPacket` 注册和 `showOrUpdateDashboard`。
- `NekoErrorDashboardScreen` 的列表、过滤、复制、定位、日志和只读 detail。
- `/nekojs error`、`/nekojs view_all_errors`、reload 后 `refreshOpenErrorDashboard`。
- 日志历史和 source map，不在编辑器数据迁移范围。

### 7.2 外部 IDE 与声明

- `common/src/main/java/com/tkisor/nekojs/script/WorkspaceGenerator.java`。
- `common/src/main/java/com/tkisor/nekojs/probe/` 全部 coordinator、backend、IR、declaration 与 editor-config contributor。
- `src/main/java/com/tkisor/nekojs/api/NeoForgeCatalogPlatformProvider.java`。
- `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricCatalogPlatformProvider.java`。
- `/nekojs probe`、`probe.toml`、`.neko_probe`、`jsconfig.json`、TS `.d.ts`、Python stub、snippets。
- `WorkspaceGeneratorManagedTypesTest` 与 Probe 兼容/golden 测试。

### 7.3 网络与持久数据

- `NekoScriptPayload`、`NetworkJS`、`NetworkMessageHandler`、`NetworkEvents`、`PlayPacketDispatcher*`。
- `PDataSyncPacket`、`PDataSyncService`、`ClientDataSyncPacket`、`ClientDataMessageHandler`、`ClientDataStore`。
- `PackHashListPayload`、`PackBundlePayload`、`PackSync*` 和 trust 逻辑。
- `NekoJSPaths` 的脚本、config、assets、data、packs、server_packs、probeDir。
- world `nekojs_packs/` 和 Fabric/NeoForge 实体 NBT 持久数据。

## 8. 验证命令

以下只给验证者使用；本轮未执行 Gradle。

先做静态消费者检查：

```powershell
rg -n "OpenWorkspacePacket|SaveScriptPacket|FetchScriptRequestPacket|FetchScriptResponsePacket|FetchAllScriptsRequestPacket|UploadAllScriptsPacket|DownloadAllScriptsPacket|SyncFeedbackPacket|NekoWorkspaceScreen|NekoWorkspaceActions|NekoCodeEditor|NekoTabbedEditor|NekoMenuBar|dashboardLoadServerScript|ScriptSyncService|ScriptSyncFiles" src versions common
```

预期：代码树无结果；若只剩历史文档，另按文档迁移处理。

再断言保留链路仍在：

```powershell
rg -n "ShowErrorListPacket|ErrorSummaryDTO|getFullDetailText|WorkspaceGenerator|ProbeCoordinator|NekoScriptPayload|NetworkMessageHandler" src common
```

然后执行 CI 同口径门禁：

```powershell
npm run test:probe-types
.\gradlew.bat guardLint --console=plain
.\gradlew.bat :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
```

本项目的 CI 以同一五节点 build 命令覆盖 compile/test/check，证据见 `.github/workflows/ci-build.yml:130-146`。

人工 smoke（至少 NeoForge 1.21.1、26.1.2、26.2.0）：

1. 启动客户端，执行不存在的 `/nekojs editor`，确认命令已移除。
2. 制造一个脚本错误，执行 `/nekojs view_all_errors`，确认 dashboard 打开、错误列表存在、`fullDetails` 中源码摘录可见。
3. reload 后确认 dashboard 仍会刷新且不会因 `openIfMissing=false` 意外重开。
4. 确认 `nekojs/`、`nekojs/config/`、`.neko_probe/`、world `nekojs_packs/` 的头寸与内容未因测试被改动。
5. Fabric 两个节点确认仍保留文本 `error` / `view_all_errors` 和 `probe`；本项不要求增加 Fabric GUI parity。

## 9. 产品歧义与建议裁决

1. **“保留错误仪表盘”的平台范围**：当前 dashboard 只在 NeoForge 存在；Fabric 的 `error/view_all_errors` 是文本降级，见 `FabricNekoJSCommands.java:47-48,217-249`。建议本项只要求 NeoForge 不回归；Fabric dashboard 另议，不把删除编辑器与 Fabric UI 新功能绑在一起。
2. **只读摘录的数据源**：建议使用当前错误记录中的 `fullDetails` 快照。它无需读文件网络协议，也不受服务端后续改文件影响。如果产品要求“打开时读取实时文件/完整文件”，那就是新增只读文件读取协议，超出本次删除范围。
3. **双击/右键行为**：当前 dashboard 双击和“Open in Tab”都进入编辑器，见 `NekoErrorDashboardScreen.java:588-603`。删除后建议保留“Open with System App”/Locate，双击改成无操作或仅选中；不要悄悄改成下载完整源码。
4. **`/nekojs editor` 的退役方式**：建议彻底不注册，不做兼容 alias。若产品要求提示“请使用外部 IDE”，需要明确保留一个小型提示命令，不能保留旧 screen/packet。
5. **多标签编辑器的复用**：`NekoCodeEditor`/`NekoTabbedEditor` 对错误页没有独立价值；只有 HTML 参考明确要求新 UI 复用原有控件时才考虑保留。按当前证据，建议删除。
6. **协议兼容**：旧客户端连接新服务端时，这 8 个 payload 会被移除。`NekoJSNetwork` 使用 registrar version `"1"`，见 `NekoJSNetwork.java:29-30`；是否接受旧/新客户端混合连接属于发布策略，必须用真实双端 smoke 验证，不能仅凭编译通过宣称兼容。
7. **HTML 参考时序**：删除补丁可以先落地并把 dashboard 收敛到现有只读列表；最终布局/交互实现应等 HTML 参考确认后另做增量，避免把编辑器拆除与视觉重做混成无法审查的大改动。

## 10. 一句话交接

GLM 的最小实现是：删除游戏内 workspace/editor 组件与 8 个文件同步包，清理其命令、compat、AT、文案和测试；保留 `ShowErrorListPacket/fullDetails` 错误链，让 `NekoErrorDashboardScreen` 的只读列表成为唯一错误查看路径；外部 `WorkspaceGenerator/Probe/.neko_probe`、脚本文件、配置、脚本包和世界持久数据完全不碰。