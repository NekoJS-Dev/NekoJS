# 原生只读错误面板实施与验证

日期：2026-09-10。状态：实施中，尚未验收。

## 本轮边界与基线

- 依据用户批准的 `blue-a-2`，在现有三个 NeoForge 节点上替换旧 UI；Fabric 仍为聊天文本降级。
- UI：Astra xhigh；模型/安全定位后端：GLM-5.3 max；主控协调、集成与验证。同一轮最多六个子代理，不递归代理。
- 保留工作区之前的编辑器删除、架构规划等未提交改动，不 reset/revert/clean。
- 本轮开始旧 Screen 和语言 JSON 副本：`C:\Users\11515\AppData\Local\Temp\nekojs-native-ui-before-20260910-213910`。
- 编辑器删除阶段验证见 [此前验证](in-game-editor-removal-verification.md)。此前 Fabric 26.1.2 `processResources` 有重复 mixin 资源失败，未归因，不预设本轮通过，也不设置 EXCLUDE 掩盖。

## 验收重点

- 两种 Screen 渲染 API 均接线到同一纯逻辑，三份现有 `McClientCompat` 的 create/update 接口保持可用。
- 蓝色左右分栏、等高卡片、折叠列表、筛选/选择/滚动状态、长路径/长消息/空态和 Minecraft GUI-scaled 小视口。
- 复制只取快照完整原文；未知来源不可定位；本地路径和 URI 安全、点击时再次验证，失败有反馈。
- 冻结 DTO/packet 字段不变；不恢复脚本编辑、下载、保存、上传或 workspace 入口。
- 编译、测试、静态检查与真实游戏 smoke 分别记录，不互相替代。

## 实施文件与命令结果

待实现和集成验证后填入真实结果。

## 已知限制与尚未验证

- 2026-09-11 曾用 MCP 启动 26.2 客户端并确认 mod/脚本加载，但该 bridge 的 `execute_command` / `open_chat` 对 26.2 的 GUI 交互路径不可用；本轮仍未完成错误面板真实交互截图，不声称像素级视觉、实际 VS Code 启动或操作系统剪贴板集成已通过。
- 本轮交付不关闭架构重构票 27/30，不实现统一诊断 record 或 Fabric 新 GUI。

## 2026-09-11 后端与入口复核

- 复核 `ErrorDashboardModel`：完整快照、搜索投影、稳定 ID 选中、计数 long 累加和畸形 DTO 过滤均保持在纯模型内；UI 不写错误事实源。
- 复核 `LocalErrorSource` / `ErrorOpenService`：远端路径先拒绝、脚本根与 game-dir real-path 双重校验、点击时重新解析、VS Code argv 分派和“仅表示请求已提交”的语义完整；未发现编辑器/写入/下载旁路。
- 复核 NeoForge 打开入口：`ShowErrorListPacket` 注册与 client handler 的“已打开则被动刷新、未打开且 openIfMissing 才打开”语义完整；`/nekojs view_all_errors` 保持在 1.21.1 与 26.x 命令面中，Fabric 继续文本降级。
- 发现并修复 VS Code `--goto` 歧义：原逻辑只要看到 Windows 盘符就允许行号后缀，盘符后再次出现冒号时仍可能生成 `file:stream:line` 形式的歧义参数。现在仅允许“唯一盘符冒号”，其余含冒号路径降级为文件级打开，并新增回归测试。
- 发现并修复 wire 契约问题：`id`、`path`、`message` 此前使用 `FriendlyByteBuf.readUtf()/writeUtf()` 的隐式短 UTF 上限，长路径或较长摘要可能在编码阶段失败，导致面板无法收到完整只读快照。现在所有 DTO 字符串字段使用同一个显式 `262144` 上限，并新增 600 字符 id/path/message 的 packet round-trip 回归测试。
- 本节只覆盖模型/打开服务/命令与网络入口复核；真实 GUI 交互、实际剪贴板和真实 VS Code 打开仍未验收，不能据此关闭票 27/30。
- 验证通过：`guardLint`（250 blocks / 326 files，0 warning）、`:common:test --rerun-tasks`（1360 tests，0 failures，0 errors，4 skipped）、1.21.1/26.1.2/26.2.0 packet 回归测试、26.2.0 全量测试（155 tests，0 failures，0 errors，34 skipped），以及 1.21.1、26.1.2、26.1.2-fabric、26.2.0-fabric 编译。


