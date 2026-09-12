// perf02 startup fixture 2/10：带 tag 的 item 注册 ×5
console.info('PERF02-STARTUP s02 items-tagged load');
RegistryEvents.register(event => {
    event.item('nekojs:perf02_item_06', b => {
        b.maxStackSize = 64;
        b.tag('minecraft:ingots');
    });
    event.item('nekojs:perf02_item_07', b => {
        b.maxStackSize = 64;
        b.tag('minecraft:ingots', 'c:gems');
    });
    event.item('nekojs:perf02_item_08', b => {
        b.maxStackSize = 16;
        b.tag('c:gems');
    });
    event.item('nekojs:perf02_item_09', b => {
        b.maxStackSize = 64;
    });
    event.item('nekojs:perf02_item_10', b => {
        b.maxStackSize = 64;
        b.rarity = 'epic';
    });
});
