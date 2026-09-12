// datafix03 pdata fixture v3：绕开 join 时实体尚未被 id→entity 索引的窗口。
// 实测（2026-09-12，worktree @39fd5aa9）：joinLevel 回调里写 pdata 会静默丢失——
// NekoJSMod.pdataContainer(entityId) 走 level.getEntity(id)，join 事件窗口内拿不到实体，
// Access.set 空容器 no-op；put 后立刻 get 也是空 tag（证据：DATAFIX-PDATA-WRITE key= seq=0）。
// 因此：joinLevel 只捕获实体对象 + 判定模式（按持久 tag nekojs_pdata_written 区分写/读），
// 真正的 put/get 延迟到 ServerEvents.tickPre（实体已入索引）执行。
const pending = [];

EntityEvents.joinLevel(event => {
    let entity;
    try {
        entity = event.getEntity();
    } catch (err) {
        return;
    }
    if (!entity) return;
    let isTarget = false;
    try {
        isTarget = entity.hasTag('nekojs_pdata_target');
    } catch (err) {
        return;
    }
    if (!isTarget) return;
    let written = false;
    try {
        written = entity.hasTag('nekojs_pdata_written');
    } catch (err) { }
    pending.push({ entity: entity, written: written });
});

ServerEvents.tickPre(event => {
    while (pending.length > 0) {
        const job = pending.shift();
        try {
            const pd = job.entity.pdata();
            if (job.written) {
                console.info('DATAFIX-PDATA-READBACK key=' + pd.getString('datafixTicket03Key')
                    + ' seq=' + pd.getInt('datafixTicket03Seq'));
            } else {
                pd.putString('datafixTicket03Key', 'ticket03-persist-me');
                pd.putInt('datafixTicket03Seq', 303);
                try { job.entity.addTag('nekojs_pdata_written'); } catch (err) { }
                console.info('DATAFIX-PDATA-WRITE key=' + pd.getString('datafixTicket03Key')
                    + ' seq=' + pd.getInt('datafixTicket03Seq'));
            }
        } catch (err) {
            console.error('DATAFIX-PDATA-FAIL ' + err);
        }
    }
});

console.info('DATAFIX-PDATA listener registered v3');
