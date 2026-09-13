// Ticket 25 诊断脚本 v4（临时探针）：确认 includesEntities 是命中为 0 的门闸。
// 前置同 diag3（RCON forceload + 带 tag 的 summon）。

Test.section('q25.diag4');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const server = ServerHooks.getCurrentServer();
const level = server.overworld();
const source = server.createCommandSourceStack();
const pos = source.getPosition();

Test.assertTrue(true, 'diag4: allEntities=' + level.getAllEntities().size());

function probe(label, selector) {
  let result;
  try {
    result = EntitySelectors.find(level, selector, pos.x, pos.y, pos.z).size();
  } catch (e) {
    result = 'THREW: ' + e.message;
  }
  Test.assertTrue(true, 'diag4: ' + label + ' -> ' + result);
}

// presets：allEntities() 内建 includesEntities=true
probe('allEntities().limit(16).create()', EntitySelectors.allEntities().limit(16).create());
probe('allEntities().tag(q25f).limit(16).create()', EntitySelectors.allEntities().tag('q25f').limit(16).create());
probe('allEntities().type(cow).limit(16).create()', EntitySelectors.allEntities().type('minecraft:cow').limit(16).create());
probe('allEntities().tag(q25f).type(cow).limit(16).create()',
  EntitySelectors.allEntities().tag('q25f').type('minecraft:cow').limit(16).create());
probe('allEntities().tag(q25f_c1).limit(16).create()',
  EntitySelectors.allEntities().tag('q25f_c1').limit(16).create());
probe('allEntities().tag(q25f).limit(2).create()', EntitySelectors.allEntities().tag('q25f').limit(2).create());
probe('allEntities().tag(q25f).distanceBelow(8).limit(16).create()',
  EntitySelectors.allEntities().tag('q25f').distanceBelow(8).limit(16).create());
probe('allEntities().tag(q25f).distanceAbove(8).limit(16).create()',
  EntitySelectors.allEntities().tag('q25f').distanceAbove(8).limit(16).create());
probe('allEntities().tag(q25f).orderNearest().limit(1).create()',
  EntitySelectors.allEntities().tag('q25f').orderNearest().limit(1).create());
probe('allEntities().tag(q25f).type(pig,true).limit(16).create()',
  EntitySelectors.allEntities().tag('q25f').type('minecraft:pig', true).limit(16).create());
probe('allEntities().tag(q25f).type(pig).limit(16).create()',
  EntitySelectors.allEntities().tag('q25f').type('minecraft:pig').limit(16).create());
probe('allEntities().type(cow).tag(q25f).limit(16).create() [order swapped]',
  EntitySelectors.allEntities().type('minecraft:cow').tag('q25f').limit(16).create());
probe('allPlayers().limit(16).create()', EntitySelectors.allPlayers().limit(16).create());

// builder()/create() 默认（includesEntities 默认值）
probe('builder().type(cow).limit(16).create()', EntitySelectors.builder().type('minecraft:cow').limit(16).create());
probe('create(b => b.type(cow).limit(16))', EntitySelectors.create(b => b.type('minecraft:cow').limit(16)));
probe('create(b => b.limit(16))', EntitySelectors.create(b => b.limit(16)));
probe('nearestEntity().limit(16).create()', EntitySelectors.nearestEntity().limit(16).create());
probe('randomEntity().limit(16).create()', EntitySelectors.randomEntity().limit(16).create());

Test.summary();
