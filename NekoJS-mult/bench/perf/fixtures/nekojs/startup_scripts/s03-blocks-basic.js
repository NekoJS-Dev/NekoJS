// perf02 startup fixture 3/10：基础 block 注册 ×3（字段面见 wiki/注册新内容）
console.info('PERF02-STARTUP s03 blocks-basic load');
RegistryEvents.register(event => {
    event.block('nekojs:perf02_block_01', b => {
        b.hardness = 3.0;
        b.sound = 'stone';
        b.requiresTool = true;
    });
    event.block('nekojs:perf02_block_02', b => {
        b.hardness = 1.5;
        b.sound = 'wood';
        b.requiresTool = false;
    });
    event.block('nekojs:perf02_block_03', b => {
        b.hardness = 5.0;
        b.sound = 'metal';
        b.requiresTool = true;
        b.lightLevel = 7;
    });
});
