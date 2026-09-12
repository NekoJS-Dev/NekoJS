// datafix03 pdata fixture：对带 nekojs_pdata_target 标签的实体读写 PersistentDataJS。
// 首次 join（RCON summon 后）写 key；此后每次 join（reload 后重召唤 / 重启后从存档加载）
// 读回并打 marker——脚本侧公开观察路径，配合 RCON `data get entity` 双通道取证。
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
        return; // 无 hasTag 能力（非 Entity 扩展对象）则忽略
    }
    if (!isTarget) return;
    try {
        if (entity.pdata.contains('datafixTicket03Key')) {
            console.info('DATAFIX-PDATA-READBACK key=' + entity.pdata.getString('datafixTicket03Key')
                + ' seq=' + entity.pdata.getInt('datafixTicket03Seq'));
        } else {
            entity.pdata.putString('datafixTicket03Key', 'ticket03-persist-me');
            entity.pdata.putInt('datafixTicket03Seq', 303);
            console.info('DATAFIX-PDATA-WRITE key=' + entity.pdata.getString('datafixTicket03Key')
                + ' seq=' + entity.pdata.getInt('datafixTicket03Seq'));
        }
    } catch (err) {
        console.error('DATAFIX-PDATA-FAIL ' + err);
    }
});
console.info('DATAFIX-PDATA listener registered');
