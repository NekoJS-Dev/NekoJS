// 26.x 物品属性修改 smoke（参考副本 —— 复制到游戏运行目录 nekojs/server_scripts/ 后生效）
// 服务器启动时自动触发；游戏运行中放入后执行 /nekojs reload server 重放。
// 本脚本验证：
//   1) ItemEvents.modification 事件 + event.modify(id, item => {...}) 属性赋值语法
//   2) 组件写入路径（maxStackSize / rarity / fireResistant / maxDamage）
//   3) 校验路径（maxDamage>0 且 maxStackSize>1 应抛出清晰脚本错误且不中断服务器）
// 验证结果：日志应出现 "NekoJS item modifications applied to 2 item(s)"，
// 后续脚本里 Item.of('minecraft:diamond').maxStackSize 应为 16。

console.log('=== item modification smoke ===');

ItemEvents.modification(event => {
    // diamond：堆叠上限 + 稀有度 + 防火（26.x = DAMAGE_RESISTANT 组件）
    event.modify('minecraft:diamond', item => {
        item.maxStackSize = 16;
        item.rarity = 'epic';
        item.fireResistant = true;
    });

    // diamond_pickaxe：原 maxStackSize 为 1，可与 maxDamage 并存
    event.modify('minecraft:diamond_pickaxe', item => {
        item.maxDamage = 3000;
    });

    // 非法组合：可堆叠 + 可损耗应被拒绝（预期脚本错误，物品保持原状）
    event.modify('minecraft:iron_ingot', item => {
        item.maxStackSize = 64;
        item.maxDamage = 100;
    });
});
console.log('=== item modification smoke registered (check error panel for the expected iron_ingot rejection) ===');
