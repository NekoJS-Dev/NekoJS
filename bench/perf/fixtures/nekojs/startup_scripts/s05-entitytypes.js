import { StartupEvents } from 'nekojs:event/versioned';

// perf02 startup fixture 5/10: Register two entity types.
console.info('PERF02-STARTUP s05 entitytypes load');
StartupEvents.registry('entity_type', event => {
    event.create('nekojs:perf02_mob_a', build => {
        build.category = 'monster';
        build.size(0.6, 1.8);
        build.spawnEgg(0x8a6f4d, 0xffe2b3);
        build.attributes(a => a.maxHealth(18).movementSpeed(0.3).attackDamage(4));
        build.goals(g => g.floatInWater(0).target(2, 'minecraft:player', true).meleeAttack(4, 1.2, false));
    });
    event.create('nekojs:perf02_mob_b', build => {
        build.category = 'creature';
        build.size(0.9, 0.9);
        build.spawnEgg(0x4d8a6f, 0xb3ffe2);
        build.attributes(a => a.maxHealth(10).movementSpeed(0.25));
        build.goals(g => g.floatInWater(0));
    });
});
