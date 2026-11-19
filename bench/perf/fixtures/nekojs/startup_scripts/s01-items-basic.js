import { StartupEvents } from 'nekojs:event/versioned';

// perf02 startup fixture 1/10: Register five basic items.
console.info('PERF02-STARTUP s01 items-basic load');
StartupEvents.registry('item', event => {
    event.create('nekojs:perf02_item_01', build => {
        build.maxStackSize = 64;
        build.rarity = 'common';
    });
    event.create('nekojs:perf02_item_02', build => {
        build.maxStackSize = 16;
        build.rarity = 'uncommon';
    });
    event.create('nekojs:perf02_item_03', build => {
        build.maxStackSize = 64;
        build.rarity = 'common';
    });
    event.create('nekojs:perf02_item_04', build => {
        build.maxStackSize = 1;
        build.rarity = 'rare';
    });
    event.create('nekojs:perf02_item_05', build => {
        build.maxStackSize = 64;
        build.rarity = 'common';
    });
});
