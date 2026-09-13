// Ticket 25 诊断脚本 v3（临时探针）：在 RCON 预置世界状态（forceload + summon）后，
// 实读 EntitySelectors 的命中结果与 includesEntities 默认语义。
// 前置：RCON 先执行 `forceload add -16 -16 16 16` 与带 tag 的 summon（见 bench/query/fixtures/README 或 REPORT §5）。

Test.section('q25.diag3');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const server = ServerHooks.getCurrentServer();
const level = server.overworld();
const source = server.createCommandSourceStack();
const pos = source.getPosition();
const ax = pos.x;
const ay = pos.y;
const az = pos.z;

Test.assertTrue(true, 'diag3: anchor=' + pos + ' level=' + level);
const all = level.getAllEntities();
Test.assertTrue(true, 'diag3: allEntities=' + all.size());
let tagged = 0;
let cows = 0;
for (const e of all) {
  const tags = e.entityTags();
  if (tags && tags.contains('q25f')) tagged++;
  if (String(e.getType()).indexOf('cow') >= 0) cows++;
}
Test.assertTrue(true, 'diag3: tagged(q25f)=' + tagged + ' cows=' + cows);

function sizeOf(builder) {
  let result;
  try {
    result = EntitySelectors.find(level, builder, ax, ay, az).size();
  } catch (e) {
    result = 'THREW: ' + e.message;
  }
  Test.assertTrue(true, 'diag3: [] -> ' + result);
  return result;
}

function probe(label, fn) {
  let result;
  try {
    result = fn();
  } catch (e) {
    result = 'THREW: ' + e.message;
  }
  Test.assertTrue(true, 'diag3: ' + label + ' -> ' + result);
}

probe('builder().tag(q25f).limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').limit(16))));
probe('builder().type(cow).limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.type('minecraft:cow').limit(16))));
probe('builder().tag+type(cow)+limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').type('minecraft:cow').limit(16))));
probe('builder().tag+type(cow,true)+limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').type('minecraft:cow', true).limit(16))));
probe('allEntities().tag(q25f).limit(16)', () =>
  sizeOf(EntitySelectors.allEntities().tag('q25f').limit(16)));
probe('allEntities().type(cow).limit(16)', () =>
  sizeOf(EntitySelectors.allEntities().type('minecraft:cow').limit(16)));
probe('allEntities().limit(16)', () => sizeOf(EntitySelectors.allEntities().limit(16)));
probe('builder().limit(16) [no filter]', () =>
  sizeOf(EntitySelectors.create(b => b.limit(16))));
probe('builder().tag(q25f) [no limit]', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f'))));
probe('builder().tag(q25f).limit(2)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').limit(2))));
probe('builder().tag(q25f_c1).limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f_c1').limit(16))));
probe('tag(q25f).distanceBelow(8).limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').distanceBelow(8).limit(16))));
probe('tag(q25f).distanceAbove(8).limit(16)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').distanceAbove(8).limit(16))));
probe('tag(q25f).orderNearest().limit(1)', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').orderNearest().limit(1))));
probe('tag(q25f).x/y/z box', () =>
  sizeOf(EntitySelectors.create(b => b.tag('q25f').x(-1).y(-70).z(-1).dx(3).dy(3).dz(3).limit(16))));
probe('allPlayers().limit(16)', () => sizeOf(EntitySelectors.allPlayers().limit(16)));
probe('allPlayers().create() [no limit]', () => sizeOf(EntitySelectors.allPlayers().create()));

Test.summary();
