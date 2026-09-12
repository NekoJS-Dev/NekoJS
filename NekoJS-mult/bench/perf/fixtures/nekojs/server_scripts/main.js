// perf02 server fixture 1/5：主脚本——固定监听器集合 + 服务器生命周期标记。
// 所有采样会话（startup/reload/probe/bench）加载同一份数据集；
// perf-out/RUN_BENCH 标志文件只控制重负载自测（adapter/arith/mem CSV 落盘），不改变脚本集合。
const fs = require('fs');
const perf02Gen = Date.now();
console.info('PERF02-SERVER main load gen=' + perf02Gen);

ServerEvents.started(event => {
    console.info('PERF02: server-started-marker gen=' + perf02Gen);
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

// 优雅停机兜底：bench 会话若 stdin 不可用，harness 写 nekojs/perf-out/STOP，
// 由 stopped 前的最后状态直接交给 harness 侧进程控制（这里只做标记日志）。
function perf02StopFlagExists() {
    try {
        return fs.existsSync('nekojs/perf-out/STOP');
    } catch (e) {
        return false;
    }
}
globalThis.__perf02StopFlagExists = perf02StopFlagExists;
globalThis.__perf02Gen = perf02Gen;
