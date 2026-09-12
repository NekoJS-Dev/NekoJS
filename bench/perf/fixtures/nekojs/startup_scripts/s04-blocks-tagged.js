// perf02 startup fixture 4/10：带 tag 的 block 注册 ×3
console.info('PERF02-STARTUP s04 blocks-tagged load');
RegistryEvents.register(event => {
    event.block('nekojs:perf02_block_04', b => {
        b.hardness = 3.0;
        b.requiresTool = true;
        b.tag('minecraft:mineable/pickaxe', 'minecraft:needs_iron_tool');
    });
    event.block('nekojs:perf02_block_05', b => {
        b.hardness = 2.0;
        b.requiresTool = true;
        b.tag('minecraft:mineable/axe');
    });
    event.block('nekojs:perf02_block_06', b => {
        b.hardness = 4.0;
        b.requiresTool = false;
        b.tag('minecraft:mineable/shovel');
    });
});
