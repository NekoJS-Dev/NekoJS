import { StartupEvents } from 'nekojs:event/versioned';

// perf02 startup fixture 6/10: Register one fluid and one creative mode tab.
console.info('PERF02-STARTUP s06 fluids-tabs load');
StartupEvents.registry('fluid', event => {
    event.create('nekojs:perf02_molten_iron', build => {
        build.density = 3000;
        build.viscosity = 6000;
        build.temperature = 1500;
        build.lightLevel = 12;
    });
});

StartupEvents.registry('creative_mode_tab', event => {
    event.create('nekojs:perf02_tab', build => {
        build.title = 'Perf02 Tab';
        build.icon = 'nekojs:perf02_item_01';
        build.add('nekojs:perf02_item_01');
        build.add('nekojs:perf02_item_02');
        build.add('nekojs:perf02_block_01');
    });
});
