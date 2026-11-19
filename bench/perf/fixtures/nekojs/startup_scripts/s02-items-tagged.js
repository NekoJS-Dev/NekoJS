import { StartupEvents } from 'nekojs:event/versioned';

// perf02 startup fixture 2/10：带 tag 的 item 注册 ×5
console.info('PERF02-STARTUP s02 items-tagged load');
StartupEvents.registry('item', event => {
    event.create('nekojs:perf02_item_06', build => {
        build.maxStackSize = 64;
        build.tag('minecraft:ingots');
    });
    event.create('nekojs:perf02_item_07', build => {
        build.maxStackSize = 64;
        build.tag('minecraft:ingots', 'c:gems');
    });
    event.create('nekojs:perf02_item_08', build => {
        build.maxStackSize = 16;
        build.tag('c:gems');
    });
    event.create('nekojs:perf02_item_09', build => {
        build.maxStackSize = 64;
    });
    event.create('nekojs:perf02_item_10', build => {
        build.maxStackSize = 64;
        build.rarity = 'epic';
    });
});
