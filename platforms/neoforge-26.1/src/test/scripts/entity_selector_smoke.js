// EntitySelectors smoke（参考副本 —— 复制到游戏运行目录 nekojs/server_scripts/ 后生效）
// 服务器启动时自动触发；游戏运行中放入后执行 /nekojs reload server 重放。
// 本脚本验证程序化实体选择器（全局绑定 EntitySelectors，server/test 脚本可用）：
//   1) EntitySelectors.create(b => ...) 流式构建 + find(level, selector, x, y, z)
//   2) 预设工厂：nearestPlayer / allPlayers（经 .create() 物化）
//   3) 距离 / 类型 / 数量 / 排序语义（±1 AABB padding 与原版 target selector 对齐）
// 验证结果：有玩家在线时每 5 秒打印各查询命中数。

console.log('=== entity selector smoke ===');

ServerEvents.tickPre(event => {
    if (event.getServer().getTickCount() % 100 !== 0) return;

    const server = event.getServer();
    for (const player of server.getPlayerList().getPlayers()) {
        const level = player.level(); // ServerLevel

        // 1) 玩家 32 格内最多 5 头牛（按距离排序）——find 的 x/y/z 是距离量测原点
        const cows = EntitySelectors.find(level,
            EntitySelectors.create(b => b.type('minecraft:cow').distance(0, 32).limit(5)),
            player.x, player.y, player.z);
        console.log(`[selector smoke] cows within 32 of ${player.scoreboardName}: ${cows.size}`);

        // 2) 16 格内生存模式的玩家（谓词经 min/max / gamemode 过滤）
        const survivors = EntitySelectors.find(level,
            EntitySelectors.create(b => b.gamemode('survival').isAlive().distanceBelow(16)),
            player.x, player.y, player.z);
        console.log(`[selector smoke] nearby survival players: ${survivors.size}`);
    }

    // 3) 以第一名玩家为锚点的最近玩家（nearest/furthest/random 务必显式传锚点）
    const players = server.getPlayerList().getPlayers();
    if (!players.isEmpty()) {
        const anchor = players[0];
        const nearest = EntitySelectors.find(anchor.level(),
            EntitySelectors.nearestPlayer().create(),
            anchor.x, anchor.y, anchor.z);
        console.log(`[selector smoke] nearest player to ${anchor.scoreboardName}: ` +
            (nearest.isEmpty() ? '<none>' : nearest[0].scoreboardName));
    }
});

console.log('=== entity selector smoke registered (wait one cycle for query logs) ===');
