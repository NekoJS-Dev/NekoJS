// 票 36 脚本作者试做示例 10/11（client 侧配对）：旧 global 迁移
//
// 来源：复用票 10 基线示例
// docs/architecture-refactor/baseline/2026-09-16-global-state/examples/legacy-cross-type-migration-client.js
// （在基线两行之外增加第三行 global.serverOnly 跨类型读取——验证 server 侧文件
// 头注声称的「server 私有，client/startup 读不到」。）
// 放置：client_scripts/（与 10-legacy-global-migration-server.js 成对试做）。
//
// 旧跨类型 global → 显式 shared 迁移（票 10 最小示例 3/4 —— client 侧）
//
// 旧写法 `global.handoff`（隐式读到 server 的值）在 1.2.0 得到 undefined——迁移方式：
// 改读 `shared.handoff`。旧写法的删除是有意的 clean cutover（1.2.0），没有兼容双写。
console.info('legacy global.handoff = ' + global.handoff); // undefined（隐式回退已删除）
console.info('explicit shared.handoff = ' + shared.handoff); // 'explicit'
console.info('legacy global.serverOnly = ' + global.serverOnly); // undefined（server 私有，跨类型不可见）
