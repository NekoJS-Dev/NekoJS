// perf02 server fixture 4/5：受控算术负载——纯 JS 固定循环，测 GraalJS 求值吞吐。
// 8 个 block × 200000 次迭代（每迭代 ~5 条语句，总量 ~8e6 语句 < scriptStatementLimit 默认 5e7；
// 单次 top-level 求值实测秒级 < scriptEvaluationTimeoutSeconds 默认 30s）。
// 每个 block 用 performance.now() 计时，ns/op 追加到 nekojs/perf-out/eval-samples.csv。
// 仅当 nekojs/perf-out/RUN_BENCH 存在时执行；脚本 load 自触发（每次 load/ reload 恰好一个样本）。
const fs = require('fs');

function sessionGen() {
    // 与其它维度共用 harness 写的会话 gen（见 main.js / README）
    try { return fs.readFileSync('nekojs/perf-out/GEN', 'utf8').trim(); } catch (e) { return String(Date.now()); }
}

function benchEnabled() {
    try { return fs.existsSync('nekojs/perf-out/RUN_BENCH'); } catch (e) { return false; }
}

const BLOCKS = 8;
const ITERS = 200000;

function runArithBench(trigger) {
    if (!benchEnabled()) return;
    const gen = sessionGen() + '-arith';
    let x = 1;
    let out = '';
    for (let b = 0; b < BLOCKS; b++) {
        const t0 = performance.now();
        for (let i = 0; i < ITERS; i++) {
            x = (x * 1103515245 + 12345) & 0x7fffffff;
            x ^= i >>> 3;
            x = (x + (x << 5)) & 0x7fffffff;
            x = x % 2147483647;
            if (x > 2147480000) x = x - 2147480000;
        }
        const t1 = performance.now();
        const nsPerOp = ((t1 - t0) * 1e6) / ITERS;
        out += gen + ',' + trigger + ',' + b + ',' + (t1 | 0) + ',' + ITERS + ',' + nsPerOp.toFixed(1) + ',' + x + '\n';
    }
    try {
        fs.mkdirSync('nekojs/perf-out', { recursive: true });
        if (!fs.existsSync('nekojs/perf-out/eval-samples.csv')) {
            fs.writeFileSync('nekojs/perf-out/eval-samples.csv', 'gen,trigger,block,epoch_ms,iters,ns_per_op,sink\n');
        }
        fs.appendFileSync('nekojs/perf-out/eval-samples.csv', out);
        console.info('PERF02-ARITH bench done trigger=' + trigger + ' blocks=' + BLOCKS + ' sink=' + x);
    } catch (e) {
        console.error('PERF02-ARITH write failed: ' + e);
    }
}

console.info('PERF02-SERVER arith-bench load');
runArithBench('load');
