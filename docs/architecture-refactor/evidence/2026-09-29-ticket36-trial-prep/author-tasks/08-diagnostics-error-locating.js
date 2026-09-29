// 票 36 脚本作者试做示例 8/11：诊断定位（deliberate error → /nekojs error → 定位）
//
// 本示例为试做专用：故意制造两类错误，验证「错误发生 → 命令查看 → 定位到文件行」的
// 公开链路（票 30 交付：ScriptDiagnosticRecord 的 phase/owner/generation/sourcePath/
// line/column 归因；票 27 的 GUI/打开链路不在本轮范围）。放置：server_scripts/。
//
// 试做流程（按 TASKS.md 表格逐格记录实际输出）：
//   场景 A（默认启用）：回调内运行期错误。
//     1) `/nekojs reload server` —— reload 成功（回调错误不毒化 reload 候选）；
//     2) 触发一次玩家登录（进世界）；
//     3) `/nekojs error` —— 预期：`⚠ Warning: The engine currently has 1 active script error(s).`
//        （nekojs.error.tracker.warning），附打开 Error Dashboard 的可点击行；
//     4) `/nekojs view_all_errors`（仅 NeoForge 有 UI；fabric 为文本降级）—— 预期面板
//        列出本文件与出错行号，行号应指向下方 throw 所在行；fabric 的文本降级输出
//        同样要能定位到本文件。
//   场景 B（把下方「场景 B」段取消注释后重启服务器或 reload）：顶层脚本错误。
//     预期 reload 以事务语义失败（旧 active 保留），`/nekojs error` +
//     `/nekojs view_all_errors` 同样给出文件与行号。
//   记录要点：错误路径显示的是脚本的 authored 路径（相对脚本根），行号是源文件行，
//   不是编译产物行（票 30 的 source-map/阶段归因口径）；若显示的是别的（例如裸
//   generated 路径、错误行、或 Java 栈），按 AC4 记为发现。
//
// 依据：票 27/30 基线 docs/architecture-refactor/baseline/2026-09-22-diagnostics/
// （REPORT §1 frozen diagnostic record、§3 phase/source-map 矩阵、§4 验证）；
// wiki/命令.md 的 /nekojs error 与 view_all_errors 条目；README.txt 提示行
// （WorkspaceGenerator 生成）。

// ---- 场景 A：回调内运行期错误（reload 不失败，错误进公开错误集）----
PlayerEvents.loggedIn(event => {
  // 试做时保持启用：这一行抛错后，/nekojs error 应报 1 个错误，
  // view_all_errors 的 Error Dashboard 应把位置指到本文件的这一行。
  throw new Error('ticket36-trial: deliberate callback error for diagnostics')
})

// ---- 场景 B：顶层脚本错误（整批 reload 失败，旧 active 保留）----
// 试做场景 B 时取消注释下面整段（并把场景 A 段注释掉），再 `/nekojs reload server`：
// throw new Error('ticket36-trial: deliberate top-level error for diagnostics')
