// 显式 shared：跨 ScriptType 共享状态的唯一入口（票 10 最小示例 2/4 —— server 侧）
//
// `shared` 是工作名（最终公开符号由 MANAGED Surface 冻结）。它沿用与 global 相同的
// 窄域 Map 语义：顶层 set/delete/clear 进候选写集、联合预检、联合提交。
// shared 只在同一 NekoJS 运行域（同一进程）内共享——它不是网络同步，不能替代
// 客户端/服务端之间的数据推送（那属于 Network/ClientData 域）。
shared.handoff = 'from-server';
shared.count = (shared.count || 0) + 1;
