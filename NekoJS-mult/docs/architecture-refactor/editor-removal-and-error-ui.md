# 内置游戏内编辑器移除与只读报错 UI 规划覆盖

> 决定日期：2026-09-10  
> 文档性质：用户批准的产品边界与规划一致性说明。本文编写/文档工作不执行源码修改；源码移除由独立实现工作进行，结果另记。新视觉 UI 仍待 HTML 样例确认。  
> 证据输入：[游戏内编辑器移除影响调研](evidence/in-game-editor-removal-research.md)、[现有游戏内脚本报错 UI 与数据契约：现状证据](evidence/error-dashboard-ui-research.md)。

## 1. 决定与历史约束关系

用户在 2026-09-10 明确批准移除内置游戏内编辑器，并要求报错 UI 先提供 Astra HTML 样例、经用户确认视觉后再优化原生 UI。这是晚于既有重构规划和“公开功能删除须维护者确认”约束的具体产品决定；该确认只覆盖本文列出的内置编辑器、workspace GUI 与编辑文件同步链路，不重开已关闭决策或 spec，也不解除数据保护、runtime、网络、插件契约或其他公开功能约束。

因此，旧文档中“保留全部公开功能”“公开功能不删除”或“workspace 行为必须保留”的一般表述不得反向否决本决定：在本文边界内，它们仅继续保护外部 IDE workspace、诊断只读报告、用户数据和自定义网络，不保护游戏内编辑脚本入口。本规则不自动放行 03+ 或整个架构重构，也不改变任何已发布实现票的 Status、Assignee 或验收勾选。

## 2. 移除边界

仅移除“在游戏内编辑脚本”这条产品链路：

- `/nekojs editor` 命令入口与 `OpenWorkspacePacket` 打开链路。
- `NekoWorkspaceScreen`、单文件/多标签编辑器、菜单和游戏内新建/删除/搜索替换等文件管理 UI。
- `OpenWorkspacePacket`、`SaveScriptPacket`、`FetchScriptRequestPacket`、`FetchScriptResponsePacket`、`FetchAllScriptsRequestPacket`、`UploadAllScriptsPacket`、`DownloadAllScriptsPacket`、`SyncFeedbackPacket`，合计 8 个编辑器专用 payload。
- `ScriptSyncService`、`ScriptSyncFiles` 和只服务这些 payload 的服务端脚本写入逻辑。
- 错误页中为编辑器复制的编辑、保存、同步与 `dashboardLoadServerScript` seam。

以下能力不是游戏内编辑器，必须保留：外部 `WorkspaceGenerator`、Probe、TypeScript/Python 类型声明、snippets、`jsconfig.json` 和 editor-config contributor；用户脚本、配置、`.neko_probe`、脚本包、packs、PData/ClientData、世界持久数据和历史日志；脚本自定义 `NetworkEvents`/payload；外部 IDE 打开或本地定位这类显式本机动作。

## 3. 只读报错 UI

错误查看是只读报告，不是远端脚本编辑器：

- 保留错误列表、过滤、选中状态、复制完整详情、打开日志，以及路径有效时的本地定位/外部打开。
- 源码摘录只允许来自服务端生成错误快照时已经写入 `ErrorSummaryDTO.fullDetails` 的冻结文本；不得为查看源码保留完整脚本下载、远端编辑、保存或同步协议。
- `fullDetails` 当前是包含源码摘录的单一文本快照。票 30 规划的统一 frozen diagnostic record 仍是未来目标；截至本文没有新的运行时结构化 diagnostic record 已存在或已完成，不得把 `fullDetails` 伪装成该 record，也不得因等待该 record 推迟编辑器删除。
- 原生 UI 优化前必须先由 Astra 提供 HTML 视觉样例并取得用户确认。本文发布时视觉尚未确认，不能宣布新 GUI、布局或报错面板已完成。
- Fabric 继续保持现有错误聊天文本降级；本文不承诺、也不自动排期 Fabric 新错误面板。

## 4. 实现票口径

- [27: CLIENT GUI 与 render Adapter 资源呈现清理](implementation-tickets/27-client-gui-render.md) 不再要求 workspace、菜单或编辑器可用。其错误 GUI 验收目标是只读报告；票 30 完成前的现状 `fullDetails` 只能作为编辑器删除工作流的过渡快照证据，不能当作 27 的最终 frozen record 验收或已实现事实。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](implementation-tickets/30-diagnostics.md) 中的 workspace 明确指外部 IDE workspace/declaration/Probe 生成与定位；错误 dashboard 是只读报告。统一 diagnostic record 是 30 的目标，不是本轮编辑器删除或错误快照展示的前置条件。
- 两张票保持原编号、标题、状态和未勾选验收；本说明只修正正文口径，不关闭已发布重构票，也不授权跳过原有数据保护与验证 gate。

## 5. 验证与并行工作边界

源码删除由另一条 GLM 独立实现工作流执行，其提交、构建、运行时验证和结论另行记录，不由本文代替。本文及其引用的静态 evidence 只说明规划边界；在那条工作流结果被本仓库验证之前，不得宣称编辑器源码删除已经完成、编译通过或运行时验证通过。


## 6. 本轮交付与后续确认

- 编辑器源码移除与验证结果已独立记录在 [实施验证记录](evidence/in-game-editor-removal-verification.md)。主控复核了日志、新测试 XML 与源码残留扫描；该结果不关闭票 27/30，也不代表所有架构重构完成。
- [HTML 参考样例](prototypes/error-dashboard.prototype.html) 与 [运行及浏览器检查说明](prototypes/error-dashboard-prototype.md) 已交付。Astra xhigh 接续 medium 草稿完成；它未进入游戏制品。
- 用户已选择左右分栏 A；按下方反馈继续完善 HTML，原生界面实现另行推进。现有游戏代码仅将旧报错界面只读化，不宣称视觉重设计完成。
- Fabric 26.1.2 的 `processResources` 重复 mixin 资源问题仍未归因，真实游戏 smoke 与混版本连接兼容未验证；不能用 HTML 预览或其他节点通过来覆盖这些限制。


## 7. 左右分栏样例的用户反馈

2026-09-10，用户明确选择左右分栏，并要求以下调整；本轮先修改 HTML 参考，不修改 Minecraft 原生界面：

- 冷深蓝/蓝色强调替代绿色配色，错误信息仍保留红色语义色。
- 删除额外的布局介绍区域，错误条目数与累计次数移入标题栏紧凑显示。
- 左侧错误卡片高度一致；长路径/长摘要不能撑高卡片，详情保留完整原文。
- 错误列表可向左收起，再从窄栏展开；不丢失选中项或搜索条件。
- 详情增加“在 VS Code 中打开”。HTML 仅演示定位计划，不启动外部应用；真实原生实现必须区分可验证本地文件与远端/虚拟/未知路径，不重新引入脚本下载或编辑协议。接口事实见 [VS Code 定位调研](evidence/vscode-error-location-research.md)。
- ~~增加错误信息最大化/还原，最大化时隐藏列表并扩大详情可用空间，还原后恢复之前列表状态。~~（已在第三轮撤销，见下节。）
- ~~增加关闭按钮。HTML 只关闭演示面板并提供重新打开，不关闭浏览器、不操作真实游戏。~~（已在第三轮撤销，关闭回到 Esc，见下节。）

本反馈不改变 DTO 字段、不启用新网络请求，也不把 HTML 交互当作原生功能完成。

## 8. 第三轮反馈：面板内不再有窗口控制与演示控件

2026-09-10 同一轮迭代中，用户在同一份 HTML 样例上追加反馈，已按下列口径修改为 `blue-a-2`（仍只改 HTML 与说明文档，不修改 Minecraft 原生界面）：

- 删除面板内「最大化」与「关闭」按钮：面板不再提供窗口控制，关闭语义回到游戏内 Esc（Esc 先关弹窗，再关面板）。上节两条相应作废。
- 底栏不再放原型说明，改为有信息量的只读信息条：左侧实时状态（「只读快照 · 逻辑服务端 · 未读取脚本文件」），右侧快捷键提示（「/ 搜索 · Esc 关闭」）。
- 演示场景切换器与「重新打开报错面板」移出 `main#panel`，放进面板外的原型控制条（虚线、灰暗、标注「不属于游戏内界面」），使面板本身即按游戏内形态呈现；这是“做成真实游戏内样子”的直接落地。
- 未改变的范围：DTO 六字段、只读语义、无网络请求、无脚本下载或编辑协议；Fabric 仍只有聊天降级，不承诺新 GUI。
- 本轮到目前仍未进行游戏内渲染、gradle 构建或游戏 smoke；HTML 实测结果见 [运行及浏览器检查说明](prototypes/error-dashboard-prototype.md)。

## 9. 原生界面实施授权（2026-09-10）

用户已批准 `blue-a-2`，要求开始按该样例实现游戏内界面，允许旧 UI 全面替换；UI 指定 Astra xhigh，后端逻辑指定 GLM-5.3 max。HTML 阶段的「原生实现待确认」由本授权替代，不再需要重新选择布局。

- 实施范围为三个现有 NeoForge 节点（1.21.1、26.1.2、26.2.0）的只读错误面板；Fabric 的文本降级维持不变。
- 原生界面保留左右分栏、蓝色主题、等高卡片、左侧收起、搜索、复制路径/全文、经过校验的本地 VS Code 打开、窄状态栏；无最大化按钮、关闭按钮、原型工具条或内置编辑器。
- 游戏界面尺寸按 Minecraft GUI-scaled 像素适配，不将 HTML 的物理像素大小直接照搬；关注最小视口、独立滚动、键盘焦点、resize/live refresh 的状态保持。
- 本轮消费现有 `ErrorSummaryDTO` 六字段和冻结 `fullDetails`，保持现有 packet 及 `openIfMissing` 语义；UI 内的选择/过滤属于快照投影，不产生新的错误事实源。不得把此交付当作票 30 的统一 frozen diagnostic record 已实现，或据此关闭票 27/30。
- VS Code 打开只能使用已确认本地来源、脚本根内的可读普通文件；远端/虚拟/未知来源不得猜路径、下载或创建脚本。操作反馈区分「已提交打开请求」和「已成功在编辑器打开」，不推断列号。
- 后续实施与验证记录见 [原生错误面板验证](evidence/native-error-dashboard-verification.md)。HTML 预览通过不替代原生编译、测试或真实游戏验收。
