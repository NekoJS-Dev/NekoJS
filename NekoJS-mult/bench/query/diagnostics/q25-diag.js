// Ticket 25 诊断脚本（临时探针，不进 fixture 集）：用于实读 EntitySelectors 的
// includesEntities 默认语义与 DataMap 的 null 参数适配行为。
// 用法：把本文件复制到 <node>/run/nekojs/test_scripts/ 后执行 /nekojs test。
// 全部断言恒真，只把观测值写进 [NekoJS Test][PASS] 日志。

Test.section('q25.diag');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const server = ServerHooks.getCurrentServer();
const level = server.overworld();
const source = server.createCommandSourceStack();
const pos = source.getPosition();
const ax = pos.x;
const ay = pos.y + 1;
const az = pos.z;
Test.assertTrue(true, 'diag: source pos=' + pos + ' level=' + level);

const commands = server.getCommands();
commands.performPrefixedCommand(source,
  'summon minecraft:cow ' + ax + ' ' + ay + ' ' + az + ' {Tags:["q25diag"]}');

// ---- 世界实体观测 ----
const all = level.getAllEntities();
Test.assertTrue(true, 'diag: level.getAllEntities().size()=' + all.size());
let tagged = 0;
let cowCount = 0;
for (const e of all) {
  const tags = e.entityTags();
  if (tags && tags.contains('q25diag')) tagged++;
  if (String(e.getType()) === 'entity.minecraft.cow') cowCount++;
}
Test.assertTrue(true, 'diag: scoreboard-tagged(q25diag) count=' + tagged + ' cow-ish count=' + cowCount);

function sizeOf(cfg) {
  return EntitySelectors.find(level, EntitySelectors.create(cfg), ax, ay, az).size();
}

// ---- includesEntities 语义探针 ----
function probe(label, cfg) {
  let result;
  try {
    result = sizeOf(cfg);
  } catch (e) {
    result = 'THREW: ' + e.message;
  }
  Test.assertTrue(true, 'diag: [' + label + '] -> ' + result);
}

function presetSize(label, builder) {
  let result;
  try {
    result = EntitySelectors.find(level, builder, ax, ay, az).size();
  } catch (e) {
    result = 'THREW: ' + e.message;
  }
  Test.assertTrue(true, 'diag: [' + label + '] -> ' + result);
}

probe('builder().tag+limit', b => b.tag('q25diag').limit(16));
probe('builder().type cow+limit', b => b.type('minecraft:cow').limit(16));
probe('builder().tag+type cow+limit', b => b.tag('q25diag').type('minecraft:cow').limit(16));
presetSize('allEntities().tag+limit', EntitySelectors.allEntities().tag('q25diag').limit(16));
presetSize('allEntities().type cow+limit', EntitySelectors.allEntities().type('minecraft:cow').limit(16));
presetSize('allEntities().limit(16)', EntitySelectors.allEntities().limit(16));

// ---- DataMap null / 缺失 / 非法输入适配行为 ----
function tryCall(label, fn) {
  let result;
  try {
    result = fn();
  } catch (e) {
    result = 'THREW(' + (e.class || e.getClass && e.getClass().getName() || 'java') + '): ' + e.message;
  }
  Test.assertTrue(true, 'diag: DataMap.' + label + ' -> ' + result);
}

tryCall('furnaceFuel(null)', () => DataMap.furnaceFuel(null));
tryCall('furnaceFuel(ItemStack.EMPTY)', () => DataMap.furnaceFuel(ItemStack.EMPTY));
tryCall('furnaceFuel(42)', () => DataMap.furnaceFuel(42));
tryCall('furnaceFuel({})', () => DataMap.furnaceFuel({}));
tryCall('furnaceFuel(undefined)', () => DataMap.furnaceFuel(undefined));
tryCall('compostable(null)', () => DataMap.compostable(null));
tryCall("furnaceFuel('minecraft:coal')", () => DataMap.furnaceFuel('minecraft:coal'));
tryCall("furnaceFuel('minecraft:not_an_item')", () => DataMap.furnaceFuel('minecraft:not_an_item'));

// ---- 错误消息全量文本（AC3 域+入口证据）----
function catchMessage(label, fn) {
  try {
    fn();
    Test.assertTrue(true, 'diag: ' + label + ' -> no error');
  } catch (e) {
    Test.assertTrue(true, 'diag: ' + label + ' -> full message = ' + e.message
      + ' ||| toString = ' + e);
  }
}
catchMessage('EntitySelectors.find(level,null,0,0,0)', () => EntitySelectors.find(level, null, 0, 0, 0));
catchMessage('EntitySelectors.find(null, selector)', () =>
  EntitySelectors.find(null, EntitySelectors.allPlayers().create(), 0, 0, 0));
catchMessage('builder().limit(0)', () => EntitySelectors.builder().limit(0));
catchMessage("builder().type('nekojs:no_such_type')", () => EntitySelectors.builder().type('nekojs:no_such_type'));
catchMessage("builder().gamemode('hacker')", () => EntitySelectors.builder().gamemode('hacker'));
catchMessage('builder().distance(-1,5)', () => EntitySelectors.builder().distance(-1, 5));

Test.summary();
