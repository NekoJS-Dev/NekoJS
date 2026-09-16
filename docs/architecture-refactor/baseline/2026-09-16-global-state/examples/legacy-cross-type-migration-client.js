// 旧跨类型 global → 显式 shared 迁移（票 10 最小示例 3/4 —— client 侧）
//
// 旧写法 `global.handoff`（隐式读到 server 的值）在 1.2.0 得到 undefined——迁移方式：
// 改读 `shared.handoff`。旧写法的删除是有意的 clean cutover（1.2.0），没有兼容双写。
console.info('legacy global.handoff = ' + global.handoff); // undefined（隐式回退已删除）
console.info('explicit shared.handoff = ' + shared.handoff); // 'explicit'
