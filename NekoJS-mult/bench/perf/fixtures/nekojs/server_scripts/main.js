// perf02 server fixture 1/5：主脚本——固定监听器集合 + 服务器生命周期标记。
// 所有采样会话（startup/reload/probe/bench）加载同一份数据集；
// perf-out/RUN_BENCH 标志文件只控制重负载自测（adapter/arith/mem CSV 落盘），不改变脚本集合。
const fs = require('fs');

function sessionGen() {
    // 会话级 gen 由 harness 在 deploy 时写入 nekojs/perf-out/GEN，四个维度共用同一值，
    // 便于跨 CSV 关联同一轮样本（早先各脚本各自 Date.now() 会让 eval 的 gen 比
    // tick/adapter/mem 早一个脚本加载周期）。
    try { return fs.readFileSync('nekojs/perf-out/GEN', 'utf8').trim(); } catch (e) { return String(Date.now()); }
}

const PERF02_GEN = sessionGen();
console.info('PERF02-SERVER main load gen=' + PERF02_GEN);

ServerEvents.started(event => {
    console.info('PERF02: server-started-marker gen=' + PERF02_GEN);
});

// 固定的普通监听器集合（reload 维度每次重载都要重新编译/注册的面）
PlayerEvents.loggedIn(event => {
    console.info('PERF02: player loggedIn');
});
LevelEvents.loaded(event => {
    console.info('PERF02: level loaded');
});
ServerEvents.tagsUpdated(event => {
    console.info('PERF02: tagsUpdated');
});

