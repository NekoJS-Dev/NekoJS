// 票 36 脚本作者试做示例 10/11：旧 global 迁移（server 侧，与 -client.js 成对）
//
// 来源：逐字复用票 10 基线示例
// docs/architecture-refactor/baseline/2026-09-16-global-state/examples/legacy-cross-type-migration-server.js
// 放置：server_scripts/（client 侧配对文件放 client_scripts/，两者要一起试做）。
// 节点：全节点（common 面，票 10 为 1.2.0 clean cutover；五节点 build 通过）。
// 公开材料：票 10 MIGRATION.md §1/§2 迁移表（旧跨类型 global → 显式 shared）。
//
// 试做验证点：跨类型共享必须显式走 shared；client 侧读 global.serverOnly 预期
// undefined（不是旧值），读 shared.handoff 预期 'explicit'（需同轮运行期）。

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
