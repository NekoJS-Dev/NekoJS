// perf02 startup fixture 1/10：基础 item 注册 ×5（RegistryEvents.register 单一入口，builder 属性面见 wiki/注册新内容）
console.info('PERF02-STARTUP s01 items-basic load');
RegistryEvents.register(event => {
    event.item('nekojs:perf02_item_01', b => {
        b.maxStackSize = 64;
        b.rarity = 'common';
    });
    event.item('nekojs:perf02_item_02', b => {
        b.maxStackSize = 16;
        b.rarity = 'uncommon';
    });
    event.item('nekojs:perf02_item_03', b => {
        b.maxStackSize = 64;
        b.rarity = 'common';
    });
    event.item('nekojs:perf02_item_04', b => {
        b.maxStackSize = 1;
        b.rarity = 'rare';
    });
    event.item('nekojs:perf02_item_05', b => {
        b.maxStackSize = 64;
        b.rarity = 'common';
    });
});
