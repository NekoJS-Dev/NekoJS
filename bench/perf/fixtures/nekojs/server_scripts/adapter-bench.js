// perf02 server fixture 3/5：Adapter 查询自测——固定 6 类绑定/registry 查询组合（见 OPS），
// 20000 次迭代、每 2000 次为一个 chunk，用 performance.now()（宿主 System.nanoTime，ms 浮点）
// 计时，把每 chunk 的 ns/op 追加到 nekojs/perf-out/adapter-samples.csv。
// 触发点：脚本 load 时若存在 nekojs/perf-out/RUN_BENCH 则自跑一轮；并监听自定义事件 Perf02Bench.adapterRun
// 供手动/后续触发。语句总量 ~1.4e5，远低于 scriptStatementLimit(5e7)。
const fs = require('fs');

function benchEnabled() {
    try { return fs.existsSync('nekojs/perf-out/RUN_BENCH'); } catch (e) { return false; }
}

const OPS = 20000;
const CHUNK = 2000;

function runAdapterBench(trigger) {
    if (!benchEnabled()) return;
    const gen = (globalThis.__perf02Gen || Date.now()) + '-adapter';
    const chunks = [];
    let sink = 0;
    for (let chunk = 0; chunk < OPS / CHUNK; chunk++) {
        const t0 = performance.now();
        for (let i = 0; i < CHUNK; i++) {
            const stack = Item.of('nekojs:perf02_item_01');        // 绑定 wrapper + item registry 查询
            const item = Item.id('minecraft:stone');               // registry 查询
            const id = Item.idOf(item);                            // 反查 registry key
            sink += Utils.randomInt(1024);                         // 纯 binding 调用
            sink += JavaMath.sqrt(sink + i) | 0;                   // java: 模块 interop 调用
            if (id && stack && sink < -1) sink = 0;                // 防死代码消除
        }
        const t1 = performance.now();
        chunks.push([chunk, t1, (t1 - t0) * 1e6 / CHUNK]); // ns per op（单 op = 6 类操作组合）
    }
    let out = '';
    for (const [chunk, t1, nsPerOp] of chunks) {
        out += gen + ',' + trigger + ',' + chunk + ',' + (t1 | 0) + ',' + nsPerOp.toFixed(1) + ',' + sink + '\n';
    }
    try {
        fs.mkdirSync('nekojs/perf-out', { recursive: true });
        if (!fs.existsSync('nekojs/perf-out/adapter-samples.csv')) {
            fs.writeFileSync('nekojs/perf-out/adapter-samples.csv', 'gen,trigger,chunk,epoch_ms,ns_per_op,sink\n');
        }
        fs.appendFileSync('nekojs/perf-out/adapter-samples.csv', out);
        console.info('PERF02-ADAPTER bench done trigger=' + trigger + ' chunks=' + chunks.length);
    } catch (e) {
        console.error('PERF02-ADAPTER write failed: ' + e);
    }
}

console.info('PERF02-SERVER adapter-bench load');
Perf02Bench.adapterRun(payload => {
    runAdapterBench('event:' + (payload && payload.trigger ? payload.trigger : 'anon'));
});
// 自触发一轮：必须在 ServerEvents.started 后——load 阶段 registry 组件未绑定，
// Item.idOf 会抛 "Components not bound yet"（2026-09-12 首轮 bench 实测）。
// started 每 server 会话恰好一次，保证每次会话一个样本。
ServerEvents.started(event => {
    runAdapterBench('started');
});
