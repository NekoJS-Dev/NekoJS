// perf02 server fixture 5/5：内存快照——每 5s 采样一次 process.memoryUsage()
// （堆内等价物：heapTotal≈Runtime.totalMemory()、heapUsed≈Runtime.totalMemory()-freeMemory()，
// 由 NekoNodeProcess 内部取自 Runtime，不经 ClassFilter）与 os.freemem()，
// 追加到 nekojs/perf-out/mem-samples.csv。定时器回调按 generation 由 runtime 在 reload/close 清理。
// 仅当 nekojs/perf-out/RUN_BENCH 存在时启动采样循环（监听器在所有会话都加载，固定数据集）。
const fs = require('fs');

function sessionGen() {
    // 与其它维度共用 harness 写的会话 gen（见 main.js / README）
    try { return fs.readFileSync('nekojs/perf-out/GEN', 'utf8').trim(); } catch (e) { return String(Date.now()); }
}

function benchEnabled() {
    try { return fs.existsSync('nekojs/perf-out/RUN_BENCH'); } catch (e) { return false; }
}

console.info('PERF02-SERVER mem-bench load');

if (benchEnabled()) {
    const gen = sessionGen() + '-mem';
    const os = require('os');
    let seq = 0;
    try {
        fs.mkdirSync('nekojs/perf-out', { recursive: true });
        if (!fs.existsSync('nekojs/perf-out/mem-samples.csv')) {
            fs.writeFileSync('nekojs/perf-out/mem-samples.csv', 'gen,seq,epoch_ms,rss_bytes,heap_total,heap_used,os_free\n');
        }
    } catch (e) {
        console.error('PERF02-MEM header failed: ' + e);
    }
    const timer = setInterval(() => {
        try {
            const mu = process.memoryUsage();
            seq++;
            fs.appendFileSync('nekojs/perf-out/mem-samples.csv',
                gen + ',' + seq + ',' + Date.now() + ',' + mu.rss + ',' + mu.heapTotal + ',' + mu.heapUsed + ',' + os.freemem() + '\n');
        } catch (e) {
            console.error('PERF02-MEM sample failed: ' + e);
        }
    }, 5000);
    // close 防漏：stopped 时停表（runtime 亦按 generation 清理）
    ServerEvents.stopped(event => {
        clearInterval(timer);
        console.info('PERF02-MEM stopped, seq=' + seq);
    });
}
