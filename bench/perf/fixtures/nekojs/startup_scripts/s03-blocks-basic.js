import { StartupEvents } from 'nekojs:event/versioned';

// perf02 startup fixture 3/10：基础 block 注册 ×3（字段面见 wiki/注册新内容）
console.info('PERF02-STARTUP s03 blocks-basic load');
StartupEvents.registry('block', event => {
    event.create('nekojs:perf02_block_01', build => {
        build.hardness = 3.0;
        build.sound = 'stone';
        build.requiresTool = true;
    });
    event.create('nekojs:perf02_block_02', build => {
        build.hardness = 1.5;
        build.sound = 'wood';
        build.requiresTool = false;
    });
    event.create('nekojs:perf02_block_03', build => {
        build.hardness = 5.0;
        build.sound = 'metal';
        build.requiresTool = true;
        build.lightLevel = 7;
    });
});
