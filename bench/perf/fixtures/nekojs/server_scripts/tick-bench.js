// perf02 server fixture 2/5：tick 自测——注册 ServerEvents.tickPre，每次回调用
// process.hrtime.bigint()（宿主 System.nanoTime，单调）记录与上次的差值进内存数组，
// 每 600 个样本（约 30s）append 到 nekojs/perf-out/tick-samples.csv；服务器 stopped 时冲刷残余。
// 仅当 nekojs/perf-out/RUN_BENCH 存在时写 CSV；监听器本身在所有会话都注册（固定数据集）。
// 路径在 nekojs/ 根内：沙盒默认 allowFsWriteOutsideNekojs=false（SandboxPolicy 写裁决）。
const fs = require('fs');
const GEN = (globalThis.__perf02Gen || Date.now()) + '-tick';
const FLUSH_AT = 600;
let lastNanos = null;
let seq = 0;
let buffer = [];

function benchEnabled() {
        try { return fs.existsSync('nekojs/perf-out/RUN_BENCH'); } catch (e) { return false; }
}

function flush() {
    if (!buffer.length) return;
    const rows = buffer.join('\n') + '\n';
    try {
        fs.mkdirSync('nekojs/perf-out', { recursive: true });
        fs.appendFileSync('nekojs/perf-out/tick-samples.csv', rows);
    } catch (e) {
        console.error('PERF02-TICK flush failed: ' + e);
    }
    buffer = [];
}

// 表头只在文件首次创建时写入，append 行不带表头，避免多次会话重复表头
try {
    if (benchEnabled() && !fs.existsSync('nekojs/perf-out/tick-samples.csv')) {
        fs.mkdirSync('nekojs/perf-out', { recursive: true });
        fs.writeFileSync('nekojs/perf-out/tick-samples.csv', 'gen,seq,delta_ns\n');
    }
} catch (e) {
    console.error('PERF02-TICK header failed: ' + e);
}

console.info('PERF02-SERVER tick-bench load gen=' + GEN);

ServerEvents.tickPre(event => {
    const now = process.hrtime.bigint();
    if (lastNanos !== null) {
        const deltaNs = Number(now - lastNanos);
        seq++;
        buffer.push(GEN + ',' + seq + ',' + deltaNs);
        if (buffer.length >= FLUSH_AT) {
            if (benchEnabled()) flush();
            else buffer = []; // 非基准会话：只累计开销不落盘，丢弃旧样本防内存增长
        }
    }
    lastNanos = now;
});

ServerEvents.stopped(event => {
    if (benchEnabled()) flush();
    console.info('PERF02-TICK stopped flush, seq=' + seq);
});
