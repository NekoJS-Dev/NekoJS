// 显式 shared：跨 ScriptType 共享状态的唯一入口（票 10 最小示例 2/4 —— client 侧）
//
// client_scripts 读 server_scripts 通过 shared 写入的值。global 则保持类型私有：
// 这里读不到 server 的 `global`（见 legacy-cross-type-migration.js）。
global.lastHandoff = shared.handoff;
