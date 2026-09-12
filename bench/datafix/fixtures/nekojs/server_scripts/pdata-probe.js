// datafix03 pdata fixture：对带 nekojs_pdata_target 标签的实体读写 PersistentDataJS。
// 注意 GraalJS host 方法调用语法：entity.pdata() 是方法（返回 PersistentDataJS），
// 必须先调用再链式 put/get（entity.pdata.contains 会把成员当 receiver，报 Message not supported）。
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
        const pd = entity.pdata();
        if (pd.contains('datafixTicket03Key')) {
            console.info('DATAFIX-PDATA-READBACK key=' + pd.getString('datafixTicket03Key')
                + ' seq=' + pd.getInt('datafixTicket03Seq'));
        } else {
            pd.putString('datafixTicket03Key', 'ticket03-persist-me');
            pd.putInt('datafixTicket03Seq', 303);
            console.info('DATAFIX-PDATA-WRITE key=' + pd.getString('datafixTicket03Key')
                + ' seq=' + pd.getInt('datafixTicket03Seq'));
        }
    } catch (err) {
        console.error('DATAFIX-PDATA-FAIL ' + err);
    }
});
console.info('DATAFIX-PDATA listener registered');
