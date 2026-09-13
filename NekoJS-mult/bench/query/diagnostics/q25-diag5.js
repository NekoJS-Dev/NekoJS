// Ticket 25 诊断脚本 v5（临时探针）：验证 type/typeTag 的 includesEntities 修复，
// 并实读 create(null) 等剩余非法输入的脚本可见行为。
// 前置：RCON forceload + 带 tag 的 summon（含 NoAI）（见 REPORT §5 配方）。

Test.section('q25.diag5');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const server = ServerHooks.getCurrentServer();
const level = server.overworld();
const source = server.createCommandSourceStack();
const pos = source.getPosition();

Test.assertTrue(true, 'diag5: allEntities=' + level.getAllEntities().size());

function probe(label, fn) {
  let result;
  try {
    result = fn();
  } catch (e) {
    result = 'THREW: ' + e.message;
  }
  Test.assertTrue(true, 'diag5: ' + label + ' -> ' + result);
}

probe('create(b => b.type(cow).limit(16)).size() [fix target]', () =>
  EntitySelectors.create(b => b.type('minecraft:cow').limit(16)).findEntities(source).size());
probe('builder().type(cow).limit(16).create().findEntities(source).size()', () =>
  EntitySelectors.builder().type('minecraft:cow').limit(16).create().findEntities(source).size());
probe('create(b => b.type(cow,true).limit(16)) [inverse]', () =>
  EntitySelectors.create(b => b.type('minecraft:cow', true).limit(16)).findEntities(source).size());
probe('create(b => b.typeTag(beehive_inhabitors).limit(16)) [fix target]', () =>
  EntitySelectors.create(b => b.typeTag('minecraft:beehive_inhabitors').limit(16))
    .findEntities(source).size());
probe('create(b => b.typeTag(flowers).limit(16)) [tag with no entities]', () =>
  EntitySelectors.create(b => b.typeTag('minecraft:flowers').limit(16)).findEntities(source).size());
probe('create(b => b.limit(16)) [no filter -> player-only]', () =>
  EntitySelectors.create(b => b.limit(16)).findEntities(source).size());
probe('create(b => b.gamemode(survival).limit(16)) [player-only]', () =>
  EntitySelectors.create(b => b.gamemode('survival').limit(16)).findEntities(source).size());
probe('create(b => b.level(0,100).limit(16)) [player-only]', () =>
  EntitySelectors.create(b => b.level(0, 100).limit(16)).findEntities(source).size());
probe('create(b => b.type(player).limit(16)) [player type]', () =>
  EntitySelectors.create(b => b.type('minecraft:player').limit(16)).findEntities(source).size());

// 非法输入（脚本可见行为）
probe('create(null)', () => EntitySelectors.create(null));
probe('find(level, null, 0,0,0)', () => EntitySelectors.find(level, null, 0, 0, 0));
probe('find(null, allPlayers().create())', () =>
  EntitySelectors.find(null, EntitySelectors.allPlayers().create()));
probe('builder().limit(0)', () => EntitySelectors.builder().limit(0));
probe('builder().distance(5,1)', () => EntitySelectors.builder().distance(5, 1));
probe('builder().distanceAbove(-1)', () => EntitySelectors.builder().distanceAbove(-1));
probe('builder().level(5,1)', () => EntitySelectors.builder().level(5, 1));
probe("builder().typeTag('nekojs:no_such_tag')", () => EntitySelectors.builder().typeTag('nekojs:no_such_tag'));
probe("builder().name('x').limit(16)", () =>
  EntitySelectors.create(b => b.name('x').limit(16)).findEntities(source).size());

Test.summary();
