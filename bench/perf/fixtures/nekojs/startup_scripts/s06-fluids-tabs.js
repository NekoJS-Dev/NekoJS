// perf02 startup fixture 6/10：fluid ×1 + creativeModeTab ×1（README「注册流体与创造模式标签页」）
console.info('PERF02-STARTUP s06 fluids-tabs load');
RegistryEvents.register(event => {
    event.fluid('nekojs:perf02_molten_iron', b => {
        b.density = 3000;
        b.viscosity = 6000;
        b.temperature = 1500;
        b.lightLevel = 12;
    });
    event.creativeModeTab('nekojs:perf02_tab', b => {
        b.title = 'Perf02 Tab';
        b.icon = 'nekojs:perf02_item_01';
        b.add('nekojs:perf02_item_01');
        b.add('nekojs:perf02_item_02');
        b.add('nekojs:perf02_block_01');
    });
});
