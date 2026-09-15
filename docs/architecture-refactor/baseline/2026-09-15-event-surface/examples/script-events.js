// NekoJS ScriptEvents 最小可运行示例（票 14 AC10）
//
// tier：managed 声明路径（ScriptEvents 注册 API 经 portable-core 契约反射承诺；
// 声明出的动态事件经同一 catalog 派生进 TS/Python declaration）。
// 可运行链路证据：common 的 ScriptEventsMinimalExampleTest 以同一脚本内容
// 按生产序列（STARTUP 声明 → ScriptEvents.post → SERVER 监听 + 触发）跑通。
//
// 本文件分两段：第一段放 startup_scripts/，第二段放 server_scripts/。

// ---- startup_scripts/events.js ----
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'));
ScriptEvents.client(event => event.register('HudEvents', 'cooldownEnded'));

// ---- server_scripts/boss.js ----
// MyEvents.bossKilled(payload => {
//   console.log('boss killed: ' + payload.boss);
// });
// MyEvents.bossKilled.post({ boss: 'ender_dragon' });
