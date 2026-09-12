// perf02 startup fixture 8/10：纯 JS 数据结构构建（测 startup/reload 的求值负载，无注册副作用）
console.info('PERF02-STARTUP s08 data-setup load');
const LABELS = ['alpha', 'beta', 'gamma'];
const perf02Lookup = new Map();
for (let i = 0; i < 200; i++) {
    const key = 'nekojs:perf02_entry_' + i;
    perf02Lookup.set(key, {
        id: key,
        index: i,
        flags: (i * 2654435761) % 4294967296,
        labels: (i % 3) === 0 ? LABELS : ((i % 3) === 1 ? [LABELS[1], LABELS[2]] : [LABELS[2]]),
        nested: { depth: 2, payload: 'p'.repeat(16) },
    });
}
const perf02Frozen = Object.freeze({
    version: 1,
    itemCount: 200,
    index: perf02Lookup,
    createdAt: Date.now(),
});
console.info('PERF02-STARTUP s08 built ' + perf02Frozen.itemCount + ' entries');
