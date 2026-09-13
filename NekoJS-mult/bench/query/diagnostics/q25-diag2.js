// Ticket 25 诊断脚本 v2（临时探针）：定位「召唤后实体不可见」的真实原因
// ——区块是否 entity-ticking。/nekojs test 在服务器线程同步执行，整段脚本内世界不 tick，
// 因此任何依赖「下一 tick 才刷入区块」的实体都不可见。本探针验证强制载入区块是否修复。

Test.section('q25.diag2');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const BlockPos = Java.type('net.minecraft.core.BlockPos');
const server = ServerHooks.getCurrentServer();
const level = server.overworld();
const source = server.createCommandSourceStack();
const commands = server.getCommands();

const bp = new BlockPos(0, -60, 0);
Test.assertTrue(true, 'diag2: before hasChunkAt=' + level.hasChunkAt(bp)
  + ' entityTicking=' + level.isPositionEntityTicking(bp)
  + ' allEntities=' + level.getAllEntities().size());

// 强制载入锚点区块（等价于加 ticket）：若此前区块未载入，实体进 pendingEntities 永不刷入
let forced;
try {
  forced = level.getChunk(0, 0);
} catch (e) {
  forced = 'THREW: ' + e.message;
}
Test.assertTrue(true, 'diag2: level.getChunk(0,0)=' + (forced === null ? 'null' : typeof forced)
  + ' hasChunkAt=' + level.hasChunkAt(bp)
  + ' entityTicking=' + level.isPositionEntityTicking(bp));

commands.performPrefixedCommand(source, 'summon minecraft:cow 0 -59 0 {Tags:["q25d2"]}');
Test.assertTrue(true, 'diag2: after force-load+summon allEntities=' + level.getAllEntities().size());

const f = EntitySelectors.find(level, EntitySelectors.create(b => b.tag('q25d2').limit(16)), 0, -59, 0);
Test.assertTrue(true, 'diag2: find by tag -> ' + f.size());

Test.assertTrue(true, 'diag2: spawn-chunk-radius/full chunk entity view via chunk='
  + (forced !== null && forced !== 'null' && typeof forced !== 'string'
      ? 'entitiesInChunk=' + level.getChunk(0, 0).getEntities() : 'n/a'));
