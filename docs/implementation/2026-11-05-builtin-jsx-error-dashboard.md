# 内置 JSX 错误仪表盘实施记录

## 已授权目标

将已验收的试作界面作为正式报错 UI，唯一界面源码位于 `common/src/main/resources/nekojs/builtin-ui/error-dashboard.tsx`。布局、样式、搜索、选中、折叠和弹窗使用 JSX／JS；Java 仅承担原生宿主、错误数据和剪贴板／源码定位接线。不依赖用户 CLIENT 脚本成功运行，不改变现有错误 DTO／网络协议；首轮覆盖 NeoForge 26.1.2 与 26.2.0，其余节点保留原行为。

## 实施步骤

1. 固定当前工作区基线，保留前序窗口、文本、滚动条及试作修改。检查报错入口、快照、源码定位与 JSX 宿主生命周期。
2. 提取生产 TSX，删除演示场景与登录自动弹出，定义内部打开／快照更新／销毁及原生操作接口。通过真实编译器验证真实六字段数据、更新后的状态保留和完整复制。
3. 增加由现有运行时 root 拥有的内部 UI 会话；复用原生 JSX 绘制及输入，保持普通脚本的 generation 校验。会话关闭和构造失败均释放 Context／宿主资源，普通 CLIENT 重载不使诊断 UI 失效。
4. 将现有报错面板打开及更新入口接到内置会话，复用实际剪贴板和异步本地源码检查／VS Code 打开。内置 JSX 故障回退既有原生面板，不递归打开，不让晚到更新复活已关闭界面。
5. 增加最小有效的编译、真实原生输入／数据更新、失败回退、重载隔离和关闭回归，运行 common 完整门禁、两个受影响节点构建、适用 CI gate 与打包检查；同步英文内嵌资料和双语说明。
6. 备份并移出隔离实例中的旧自动弹出试作，安装新 JAR，正常重启并最大化。通过选项工具由用户执行真实错误的人工验收；如需截图由助手获取。只有实际反馈才能登记人工通过。

## 验证边界

正式资源、运行时会话、原生操作接线与统一打开入口已实现，完整门禁、五节点构建和打包核对已通过。NeoForge 26.1.2 已安装并恢复大窗口，维护者已确认正式入口、真实错误显示、滚动、复制、实际编辑器打开，以及零错误时的空状态、输入和关闭／重开；本次已执行范围验收通过，临时测试脚本已移除。既有原型、窗口与滚动条验收不代替生产接入验收；CPU 夹具不代替 GPU、物理输入或实际编辑器启动结论。不创建 GitHub issue，不提交、推送、升级版本或发布。

## 实现与聚焦证据

- 生产 UI 直接使用已验收布局，删除演示数据／场景按钮／登录自动打开。全屏、1280×720 设计比例、三个独立文本滚动区域及细滚动条保留。内部 JS 工厂接收显式宿主、操作桥与真实六字段快照，更新时按稳定 ID 保留搜索、选中与已保留控件的偏移；复制始终读取原 DTO。
- `BuiltinUiRuntime` 由现有 root 的资源追踪器拥有，使用同一个 Engine；Graal 要求共享 Engine 的 Context 使用相同 HostAccess，因此通过已有环境工厂复用其精确配置。诊断 Context 不安装用户模块或脚本事件组，禁止类查找、文件 IO、进程和线程；宿主操作通过显式代理提供，原生节点使用不透明代理句柄，避免暴露 Java 节点对象。源码编译产物仅缓存固定两个内置资源，随 root 关闭清空；每次打开拥有独立 Context。
- 普通 `ClientUI.screen`／`region` 构造及 generation 检查保留；内部构造仅供已有 root 拥有的会话使用。原生绘制、像素边界、滚动、缓存和焦点机制继续复用。内部错误使用 `NEKO-7015`，故障请求一次原生回退，停机及晚到回调不复活界面。
- 原有 `ShowErrorListPacket`、DTO 六字段、命令权限、聊天链接与 `openIfMissing` 语义不变。26.x 客户端入口绑定会话工厂；1.21.1 使用同名版本 facade 保留旧界面；Fabric 错误输出未改。源码检查和打开复用 `LocalErrorSource`／`ErrorOpenService`，后台执行并在 owner thread 校验请求与选中 DTO，关闭／更新／选中变化使旧结果失效。
- `BuiltinUiRuntimeTest` 覆盖资源编译、显式桥接的无宿主访问上下文、guest 回调延迟关闭、跨线程关闭和会话隔离；root 生命周期回归验证 UI 在用户 CLIENT 重载成功及真正候选失败后仍可执行，root close 后失效。
- `NativeBuiltinErrorDashboardTest` 运行规范 TSX、真实原生宿主与代理，验证真实快照更新后的搜索／选中／滚动保留、完整原文复制、单次打开请求、用户 generation 清理隔离、实际 Screen 路由、复制失败模态、关闭后晚到更新、Context 故障回退与 root 停机清理。平台替身只接替原生窗口显示及无 GPU 记录；不运行玩家 UI 验收。
- 当前聚焦命令及结果见 `common/build/builtin-dashboard-lifecycle-focused.log`：common 5 项、26.1.2 原生 3 项通过。后续完整验证包含最新修改；不能以该阶段结果冒充最终构建。

## 最终自动验证与打包

命令：`./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`。最终构建 3 分 15 秒通过，106 项任务，日志为 `common/build/builtin-dashboard-full-build-final.log`。保留 Java 21 common 测试工具链，未刷新 golden。

| 检查 | 测试总数 | 失败／错误 | 跳过 |
|---|---:|---:|---:|
| common 完整 check | 2741 | 0／0 | 4 |
| NeoForge 1.21.1 | 472 | 0／0 | 33 |
| NeoForge 26.1.2 | 838 | 0／0 | 85 |
| NeoForge 26.2.0 | 838 | 0／0 | 85 |
| Fabric 26.1.2 | 438 | 0／0 | 39 |
| Fabric 26.2.0 | 438 | 0／0 | 39 |

- guardLint 检查 616 文件、417 条 guard，零警告；五节点 platform gate、source-root 探针及四类 CI gate 通过。CI 报告和日志为 `common/build/builtin-dashboard-ci-gates.json`、`common/build/builtin-dashboard-ci-gates.log`；工具为 `python tools/nekojs-ci-gates.py source-roots --node <node>` 和 `python tools/nekojs-ci-gates.py all --json`。
- 最后聚焦回归日志 `common/build/builtin-dashboard-final-focused.log` 通过，覆盖 root 生命周期、内置运行时及两个支持节点的内置界面和既有输入检查。完整构建早期发现共享测试 Engine 的 HostAccess 污染，以及普通宿主 resize 调用形式变化；前者让测试自行拥有 Engine、保持被测 root 内共享关系，后者恢复普通宿主原有调用形式。最终完整构建通过，未修改 golden 或放宽门禁。
- Wiki 严格检查 30 双语主题零问题，工具测试 8 项通过；`git diff --check` 通过。未更改 Script API 声明，不另行宣称 Probe 类型链或 NBT smoke 已运行。
- `common/build/builtin-dashboard-artifact-audit.json` 核对 8 个 JAR，各 36 项 AI payload 与规范源／manifest 的字节及哈希一致。7 个非 CLI 制品含相同生产 TSX，SHA-256 为 `15558d3d4ba77faa5a8367ea9ef1d6b971a86054b07a658e9bba821f961665f3`。独立 authoring stdio 工具不包含 UI 或运行时 root，符合工具边界。原生内置 Screen 只进入两个 NeoForge 26.x 节点；公共资源出现在其他节点不代表功能支持。核对同时覆盖 root／会话及两个原生节点宿主／桥接的已编译类。
- 文档 bundleId 为 `c04313787338000d55fb6758b8a99f1b08daa4099f2bc5e468b53cf071709617`。英文内嵌手册与双语 JSX、命令和错误码文档已同步，没有升级版本或增加依赖。

## 安装与维护者验收进度

隔离实例为 `common/build/mcp-live-26.1.2-20261103`，NeoForge 26.1.2.71。安装前实例已停止；校验旧 JAR 和旧试作脚本后备份到 `evidence/builtin-error-dashboard-20261105/`，旧试作移出自动加载目录。生产 JAR SHA-256 为 `3083910c989fbf2ecd14d402c12fd824f74e03665906651369f3e7bb0ab8b0b9`，与构建产物一致。MCP 启动后 PID 为 29092、端口 9876，先前截图确认主菜单已最大化，`guiScale:3` 保留。启动日志为 `common/build/builtin-dashboard-launch.log`。

临时脚本 `nekojs/client_scripts/acceptance/__builtin_error_dashboard_acceptance_20261105.js` 产生包含 000–159 编号的真实 CLIENT 错误，用于验证用户脚本失败时仍可打开诊断 UI；此脚本需要在验收结束后校验哈希并移除。原有 `src/main.js` 未改，旧试作保持备份、不恢复自动弹出。

维护者通过选项工具反馈：**“新界面正常打开，能看到真实测试错误”**。同时 MCP 读取到当前 Screen 为 `BuiltinErrorDashboard`，证明本次实际进入内置 JSX 路径。后续真实错误交互检查反馈：**“滚动、复制、VS Code 打开都正常”**，覆盖 Error／原始详情框的独立滚轮和滑块、路径与完整原文复制，以及实际编辑器打开对应文件。截图时检测到物理 Esc，助手停止该轮电脑操作，未把未获取的截图记为证据。详细状态保存在隔离实例 `evidence/builtin-error-dashboard-20261105/acceptance.json`。

交互验收后，助手核对临时脚本 SHA-256 为 `b66e384335adf04b9df8c9be00c6bd7d7887f5685459c5079f776fa6e946d577`，在证据目录保存 `.js.backup` 后移除自动加载目录中的该单个文件，并确认原路径已不存在。生产 JAR 哈希仍与安装记录一致。维护者随后正常 CLIENT 重载并反馈无错误，确认临时错误已清除；该反馈进一步引出下述零错误显式打开需求，不把文件移除本身当作重载成功。

26.2.0 实机、其他 GUI 缩放、真实停机资源泄漏测量和 GPU 性能未验收；自动生命周期夹具覆盖的成功／失败重载、关闭和故障回退不扩大为上述实机结论。

## 零错误时显式打开

清理临时脚本后，维护者反馈重载显示无错误，并要求 `/nekojs view_all_errors` 在零错误时也打开仪表盘。原命令在发送包之前按错误数量拦截；现仅在两个 NeoForge 26.x 节点为玩家显式命令发送空快照，使用原有 `openIfMissing=true` 和既有 JSX 空状态。控制台零错误提示、`/nekojs error` 概览、被动刷新、1.21.1 与 Fabric 行为保持原样，网络协议未改。

既有原生路由回归增加空快照显式打开、空状态／搜索控件、无复制按钮、Esc 关闭和会话释放检查；网络只读契约回归增加空快照的显式／被动标志往返检查。英文内嵌手册与双语命令说明同步。前述真实错误人工通过仍有效，不当作本次新增入口行为的证据。

重新执行前述 common 完整检查、guardLint 与五节点 build 命令，3 分 55 秒全部通过，日志为 `common/build/builtin-dashboard-empty-open-build.log`。common 2741 项、4 跳过；1.21.1 为 473 项、33 跳过；两个 NeoForge 26.x 各 839 项、85 跳过；两个 Fabric 各 438 项、39 跳过；全部零失败／错误。两个支持节点的内置面板回归各 3 项、空快照网络契约各 3 项零跳过。四类 CI gate 再次通过，沿用未改动的五节点 source-root 证据，报告为 `common/build/builtin-dashboard-empty-open-ci-gates.json`；wiki 30 主题零问题、8 项工具测试通过，`git diff --check` 通过。

`python common/build/builtin-dashboard-empty-open-audit.py` 核对 8 个最新 JAR 的 36 项资料、规范 TSX、公共运行时及支持节点的命令／宿主编译输出，全部一致；报告为 `common/build/builtin-dashboard-empty-open-artifact-audit.json`。TSX 哈希未改，文档 bundleId 更新为 `796858da549c2201afbe9708813585899a85fb6a1af6f2951dda82ac1df95195`。

已正常保存世界、退出旧 PID 29092，确认进程停止并核对安装哈希后备份替换。新 JAR SHA-256 为 `d935d8715e566da39644aed3725574d6c1a85dfddb12112a8094f26341683a1f`，新 PID 35656、MCP 9876。启动日志为 `common/build/builtin-dashboard-empty-open-launch.log`；临时脚本仍不存在。截图确认新实例主菜单已最大化，GUI 缩放仍为 3。安装记录保留此前版本的人工反馈和备份。

维护者按选项执行正常 CLIENT 重载、零错误显式打开、搜索输入及 Esc 关闭／重开后反馈：**“零错误也能打开，空状态、输入、关闭重开都正常，本次验收通过”**。最终复核安装 JAR 与审计哈希一致、临时脚本原路径不存在、四类 CI gate 全通过；验收 JSON 状态为 `MAINTAINER_ACCEPTANCE_PASSED_EXECUTED_SCOPE`，待办为空。该结论仅覆盖当前 NeoForge 26.1.2、GUI 缩放 3 的已执行项目；26.2.0 实机、其他缩放、GPU 性能与实机资源泄漏测量仍未运行。
