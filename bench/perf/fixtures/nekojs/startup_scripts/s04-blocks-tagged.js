import { StartupEvents } from 'nekojs:event/versioned';

// perf02 startup fixture 4/10：带 tag 的 block 注册 ×3
console.info('PERF02-STARTUP s04 blocks-tagged load');
StartupEvents.registry('block', event => {
    event.create('nekojs:perf02_block_04', build => {
        build.hardness = 3.0;
        build.requiresTool = true;
        build.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool');
    });
    event.create('nekojs:perf02_block_05', build => {
        build.hardness = 2.0;
        build.requiresTool = true;
        build.tag('minecraft:mineable/axe');
    });
    event.create('nekojs:perf02_block_06', build => {
        build.hardness = 4.0;
        build.requiresTool = false;
        build.tag('minecraft:mineable/shovel');
    });
});
