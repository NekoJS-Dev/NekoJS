# 票 27 迁移与删除边界材料（client GUI 与 render Adapter 资源呈现）

日期：2026-09-27。本票**未删除任何旧公开路径、未更新任何 golden**；本文记录当前域内的
单路径事实、本票新增的 Java 侧契约（加法）、以及留给维护者裁定的残留项。

## 1. 本票新增的契约（全部为加法，无 breaking）

| 面 | 变更 | 消费者 | 兼容性 |
|---|---|---|---|
| `common/.../core/state/CandidateStatePlan` | 新增 `default void discard() {}`（候选失败/取消的释放通知，契约：幂等、不得抛） | 所有既有实现（global 写集内建计划、票 16/22/39 领域计划、票 26 渲染批次、测试计划）继承空默认，行为不变 | Plugin API 加法；联合成败语义不变 |
| `common/.../core/state/GenerationGlobals` | `discard()`/`close()` 在清空计划列表前逐个通知 `plan.discard()`（失败记 WARN `[NEKO-1001]` 并继续） | 候选事务边界本身 | 行为加法：失败当刻释放候选资源（票 27 AC4 字面兑现） |
| `common/.../core/error/LocalErrorSource` | 新增 `resolve(DiagnosticOpenAction, boolean)`；与 DTO 入口共用同一套 `resolveLocation` 校验 | 错误 GUI 调用者 Interface（票 30 seam 消费） | 加法；DTO 路径行为逐字节不变 |
| `common/.../core/error/ErrorOpenService` | 新增 `openAsync(DiagnosticOpenAction, boolean, Executor)`；两个入口共用 `dispatch` 收尾 | 同上 | 加法；DTO 路径行为不变 |
| `src/.../client/render/ClientRenderRegistry` | 新增只读观察面 `pendingCandidateRegistrations()`；`clearAll()` 补充清空候选批次（生产代码零调用者，仅诊断/测试语义补全）；`Candidate` 实现 `discard()` | 生命周期 fixture / 诊断 | 加法；`registerHud/registerWorld/dispatchHud/dispatchWorld` 等既有公开面零变化 |
| `wiki/en_us/Error-Reference.md` | 登记 `NEKO-1001`（候选计划丢弃失败，teardown 继续） | 诊断查询 | 与 coding.md 的同变更登记规则一致 |

脚本 API（Script API）零变化：事件组成员、payload、binding、声明面均未改动（golden 零 diff，
`:26.1.2:platformGateTest` 无 member-drift —— 见 REPORT §4）。

## 2. 域内单路径事实（无长期双路径）

- **渲染分发 handler 唯一**：`ClientRenderRegistry.dispatchHud/dispatchWorld` 的生产调用点只有
  平台 Adapter `ClientRenderEvents`（26.x 共享树 + `versions/1.21.1` 孪生），见
  `command-output/05-old-route-remnant-scan.txt` §2。`NekoReloadProgressHud` 是 root 拥有的
  reload 进度 HUD（自包含订阅、不属任何脚本 generation），不是第二条渲染注册路由。
- **错误 GUI 入口唯一**：`NekoErrorDashboardScreen.create` 的生产调用点只有
  `NekoJSNetwork.ClientHandler.showOrUpdateDashboard`（每节点一个 packet handler）。
- **内置编辑器链路零残留**：`OpenWorkspacePacket`/`SaveScriptPacket`/`FetchScript*`/
  `UploadAllScriptsPacket`/`DownloadAllScriptsPacket`/`SyncFeedbackPacket`/`ScriptSyncService`/
  `ScriptSyncFiles`/`NekoWorkspaceScreen`/`dashboardLoadServerScript` 在生产源码 0 命中
  （同上 §1）——编辑器删除工作流（editor-removal-and-error-ui.md §6）的删除在本分支成立，
  未以“旧公开路径”理由保留任何编辑/同步能力。
- **`ClientRenderRegistry.clearAll()` 生产调用者仍为 0**（票 26 删除 `ClientRenderPlugin`
  后的既定状态，本票仅把候选批次纳入该诊断清理面）。

## 3. 留给维护者裁定的残留项（本票不删除）

1. **旧 UI 遗留 lang key**：`nekojs.gui.dashboard.btn.log` / `btn.copy` / `btn.locate` /
   `target` 在代码中 0 引用（旧报错界面时代的按钮文案）。删除它们是发布性 lang 内容变更，
   需维护者确认；本票未动。
2. **只读 GUI 的“打开日志”动作**：专项产品决定 §3 把“打开日志”列为只读报告保留能力，但
   已获用户批准的 blue-a-2 原生界面元素清单（同文 §9：左右分栏/蓝色主题/等高卡片/左侧收起/
   搜索/复制路径全文/校验 VS Code 打开/窄状态栏）未含日志入口，现有原生界面也未实现。
   是否补该入口（以及放哪里）属 UI 范围裁定，归维护者；票 27 AC1 因此保持部分满足。
3. **结构化 open-action 上 wire**：GUI 的本地定位动作已在 seam 层消费票 30 record
   （`DiagnosticOpenAction`），但 packet 仍按票 30 冻结的六字段 wire 传输 record 投影
   （`fullDetails` 过渡快照不变）。若未来要在 wire 上携带结构化 action，属 wire 契约变更，
   需旧新 diff + 维护者审阅。

## 4. 删除条件（沿用票据 Human input note）

本票未产生新的“待删除旧路径”。上节三项如获维护者 sign-off，可分别走：lang key 清理（发布
审阅）、日志入口 UI 授权（Astra 视觉流程）、wire 演进（golden 旧新 diff）。在任何一项获
得维护者确认前，维持现状即为本票交付状态。
