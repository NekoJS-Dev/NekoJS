// 旧跨类型 global → 显式 shared 迁移（票 10 最小示例 3/4 —— server 侧）
//
// 1.2.0 之前：`global` 是进程级共享 Map，server_scripts 写的 `global.foo` 会被
// client_scripts / startup_scripts 直接读到（隐式跨类型共享）。1.2.0 起该隐式回退已删除：
// `global` 按 ScriptType 私有，跨类型共享必须显式走 `shared`。
//
//   旧写法（不再跨类型可见）：           新写法（显式共享）：
//     // server_scripts                    // server_scripts
//     global.handoff = 'x'                 shared.handoff = 'x'
//     // client_scripts                    // client_scripts
//     global.handoff   // 'x'              shared.handoff     // 'x'
//
// 同类型用法不变：server_scripts 之间继续用 global.foo 共享。
global.serverOnly = 'private-to-server'; // client/startup 读不到
shared.handoff = 'explicit';
