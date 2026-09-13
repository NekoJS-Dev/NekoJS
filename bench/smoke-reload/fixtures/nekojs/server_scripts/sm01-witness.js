// ticket 06 烟测 fixture（bench/smoke-reload）：commit witness 模板。
//
// run-reload-smoke.ps1 会按 phase 覆写本文件为 v1..v6 版本，本文件是起始模板（v1）。
//
// 语义（为什么行内容能当 commit 判定面）：
//   * `ticket06-entry-<v>`：入口求值一次即打印。候选执行期也会打印——候选执行是真实求值，
//     这是 spec 09 user story 28 明示「不深回滚外部副作用」的范围，故它只作计数证据。
//   * `ticket06-tick-<v>`：setInterval 注册进所属 generation 的 node runtime。生产 tick flush
//     只冲刷 active generation（ScriptManager#flushReadyNodeTimers 读 this.runtime），候选的
//     timer 在 commit 前不进生产路由、失败时随候选关闭丢弃。因此该行只可能由「已提交为
//     active 的那个 generation」产生：成功 commit → 新版本 tick 出现且旧版本永久停止；
//     失败 → 该版本 tick 永不出现；双重执行 → 两个版本同时 ~1 行/秒。
//
// 失败注入（sm02-boom.js，runner 动态写入）靠 fixtures/nekojs/config/engine.toml 的
// scriptRunawayTimeoutSeconds = 2：`while(true){}` 在 ~2s 后被 runaway watchdog 关闭候选
// Context → 事务式 reload 以 phase=EXECUTION、domain=candidate-killed 失败并保留 active。
console.info('ticket06-entry-v1');
setInterval(function () {
    console.info('ticket06-tick-v1');
}, 1000);
