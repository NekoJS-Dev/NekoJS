// 26.x 村民交易修改 smoke（参考副本 —— 复制到游戏运行目录 nekojs/server_scripts/ 后生效）
// 服务器启动 / /nekojs reload server 时暂存，reload 流水线末尾刷入 minecraft:villager_trade
// 与 minecraft:trade_set 注册表。本脚本验证：
//   1) VillagerTrades.add 基本用法（'1x minecraft:emerald' 计数前缀语法）
//   2) costB 第二花费 + maxUses / xp / priceMultiplier 覆盖
//   3) 对象形式 cost { item, count } 与裸 id（默认数量 1）
//   4) 非法 trade set id 返回 false（仅告警，不中断脚本）
// 验证结果：日志应出现
//   "VillagerTrades: applied 4 trade addition(s) across 3 trade set(s) (0 skipped)"，
// 且农民 1 级交易列表出现 5x 苹果换 1 绿宝石、8x 胡萝卜换 1 绿宝石。

console.log('=== villager trades smoke ===');

// 1) 基本形式：计数前缀 '1x minecraft:emerald'，结果 '5x minecraft:apple'
VillagerTrades.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',
    result: '5x minecraft:apple',
    maxUses: 12,
    xp: 2
});

// 2) costB 第二花费 + priceMultiplier（面包师 1 级：绿宝石 + 小麦换面包）
VillagerTrades.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',
    costB: '2x minecraft:wheat',
    result: '6x minecraft:bread',
    maxUses: 8,
    xp: 1,
    priceMultiplier: 0.05
});

// 3) 对象形式 cost + 流浪商人 buying 池
VillagerTrades.add('minecraft:wandering_trader/buying', {
    cost: { item: 'minecraft:emerald', count: 3 },
    result: 'minecraft:bucket',
    maxUses: 4,
    xp: 0
});

// 4) 裸 id（默认数量 1）：武器匠 1 级煤炭换绿宝石
VillagerTrades.add('minecraft:weaponsmith/level_1', {
    cost: 'minecraft:coal',
    result: 'minecraft:emerald',
    maxUses: 16,
    xp: 2
});

// 5) 非法 trade set id：返回 false 并告警，不应抛异常
let ok = VillagerTrades.add('minecraft:not_a_profession/level_9', {
    cost: 'minecraft:emerald',
    result: 'minecraft:apple'
});
console.log('invalid set id rejected: ' + !ok);
console.log('pending trades after staging: ' + VillagerTrades.pendingCount());

console.log('=== villager trades smoke staged (applied at end of reload cycle) ===');
