// perf02 startup fixture 5/10：entityType 注册 ×2（builder 面见 wiki/注册新内容 §entityType）
console.info('PERF02-STARTUP s05 entitytypes load');
RegistryEvents.register(event => {
    event.entityType('nekojs:perf02_mob_a', b => {
        b.category = 'monster';
        b.size(0.6, 1.8);
        b.spawnEgg(0x8a6f4d, 0xffe2b3);
        b.attributes(a => a.maxHealth(18).movementSpeed(0.3).attackDamage(4));
        b.goals(g => g.floatInWater(0).target(2, 'minecraft:player', true).meleeAttack(4, 1.2, false));
    });
    event.entityType('nekojs:perf02_mob_b', b => {
        b.category = 'creature';
        b.size(0.9, 0.9);
        b.spawnEgg(0x4d8a6f, 0xb3ffe2);
        b.attributes(a => a.maxHealth(10).movementSpeed(0.25));
        b.goals(g => g.floatInWater(0));
    });
});
