// Ticket 25 fixture: EntitySelectors factory/builder/query 全链路（server side）。
// 载体：/nekojs test（真实 ServerLevel + 真实实体）。预期全绿。
//
// == 世界状态前置（RCON，必须在跑本脚本之前完成；配方见 REPORT §5）==
//   1) forceload add -16 -16 16 16
//      无玩家的专用服务器不会 entity-ticking 载入区块；未 entity-ticking 区块里的召唤
//      实体会停在 PersistentEntitySectionManager 的 pendingEntities，任何 EntitySelector
//      查询都看不到它（游戏内实证：hasChunkAt=false / entityTicking=false →
//      getAllEntities()=0；同一 tick 内召唤的实体同样不可见）。
//   2) 锚点附近召唤带 tag 的实体（NoAI 固定位置，避免游荡改变距离断言）：
//        summon minecraft:cow 0   -60 0   {Tags:["q25f","q25f_c1"],NoAI:1b}
//        summon minecraft:cow 3   -60 0   {Tags:["q25f","q25f_c2"],NoAI:1b}
//        summon minecraft:cow 6   -60 0   {Tags:["q25f","q25f_c3"],NoAI:1b}
//        summon minecraft:pig 1.5 -60 1.5 {Tags:["q25f","q25f_p1"],NoAI:1b}
//        summon minecraft:bee 8   -60 8   {Tags:["q25bee"],NoAI:1b}      // typeTag 探针
//   3) 然后再执行 /nekojs test：召唤发生在更早的 tick，实体已刷入区块。
//      本脚本自身不做 summon —— /nekojs test 在服务器线程同步跑完，期间世界不 tick，
//      同 tick 内召唤的实体对选择器不可见。
//
// 期望集合由世界状态自校准（按 tag/类型现算），不受历史运行累积实体与 mob 生成影响。
//
// == 作用域语义（游戏内实读，见 REPORT §5）==
// - `builder()` / `create(cfg)` 的基座是玩家集合（`includesEntities=false`，等价 @a）；
//   `allEntities()` / `nearestEntity()` / `randomEntity()` 预设的基座是实体集合。
//   vanilla 侧同样是「按前缀决定」：EntitySelectorParser 的 includesEntities 无初值，
//   @e/@r 置 true、@a/@p 置 false；builder 无前缀，因此由基座预设给出。
// - ticket 25 修复：**显式实体类型过滤**会把作用域切到实体集合——`type('minecraft:cow')`
//   这类非玩家类型的正选、`type(..., inverse=true)` 反选、以及 `typeTag(...)`。
//   修前它们只在玩家集合上求值，于是文档示例 `create(b => b.type('minecraft:cow'))`
//   永远返回空（游戏内实证：3 头带 tag 的牛命中 0）。
// - 纯计分板 tag / team / 体积框过滤本身不决定作用域（两者都合法），沿用基座：
//   `builder().tag('x')` 查玩家，`allEntities().tag('x')` 查实体。
// - 非法输入（limit/distance/level/gamemode/type/typeTag、find 的 level/selector、create 的
//   config）得到带域+调用入口的普通错误，不含修复提示；脚本源位置由统一错误管线补。

Test.section('EntitySelectors.query');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const server = ServerHooks.getCurrentServer();
Test.assertNotNull(server, 'dedicated server is running');
const level = server.overworld();
Test.assertNotNull(level, 'overworld ServerLevel obtained');
const source = server.createCommandSourceStack();
const anchor = source.getPosition();
const ax = anchor.x;
const ay = anchor.y;
const az = anchor.z;

// ---- 前置：自校准世界里的锚点实体（缺前置时给出可执行的失败信息）----
let taggedAll = 0;
let taggedCow = 0;
let taggedC1 = 0;
let taggedP1 = 0;
let taggedBee = 0;
for (const entity of level.getAllEntities()) {
  const tags = entity.entityTags();
  const isCow = String(entity.getType()).indexOf('cow') >= 0;
  if (tags.contains('q25f')) {
    taggedAll++;
    if (isCow) taggedCow++;
  }
  if (tags.contains('q25f_c1')) taggedC1++;
  if (tags.contains('q25f_p1')) taggedP1++;
  if (tags.contains('q25bee')) taggedBee++;
}
Test.assertTrue(taggedAll >= 4 && taggedCow >= 3 && taggedC1 >= 1 && taggedP1 >= 1,
  'prerequisite: forceload + tagged summon done first (q25f=' + taggedAll + ' cows=' + taggedCow
    + ' q25f_c1=' + taggedC1 + ' q25f_p1=' + taggedP1 + '); see this file header / REPORT §5');
Test.assertTrue(taggedBee >= 1, 'prerequisite: tagged bee present (q25bee=' + taggedBee + ')');

function sizeOf(selector) {
  return EntitySelectors.find(level, selector, ax, ay, az).size();
}

// ---- factory(create) + builder + query：entity 基座上按 tag 精确命中 ----
Test.assertEquals(taggedAll, sizeOf(EntitySelectors.allEntities().tag('q25f').limit(64).create()),
  'allEntities() + tag + find: hits every tagged entity');
Test.assertEquals(taggedC1, sizeOf(EntitySelectors.create(b => b.tag('q25f_c1').limit(64)
  .type('minecraft:cow'))),
  'create(b => tag + type(cow)) + find: hits exactly the tagged cow');
Test.assertEquals(taggedCow, sizeOf(EntitySelectors.builder().type('minecraft:cow')
  .tag('q25f').limit(64).create()),
  'builder() + explicit type(cow) is entity-scoped and excludes the tagged pig');
Test.assertEquals(taggedC1, sizeOf(EntitySelectors.builder().type('minecraft:cow')
  .tag('q25f_c1').limit(64).create()),
  'builder() + type(cow) + tag narrows to the single tagged cow');

// ---- 作用域规则：纯 tag 过滤沿用基座（builder() = 玩家集合）----
Test.assertEquals(0, sizeOf(EntitySelectors.create(b => b.tag('q25f').limit(64))),
  'scope: a tag-only builder() stays on the player base (0 players online)');
Test.assertEquals(0, sizeOf(EntitySelectors.allPlayers().limit(64).create()),
  'scope: allPlayers() preset stays player-only -> empty snapshot');

// ---- 显式实体类型过滤切作用域（ticket 25 修复目标）----
Test.assertTrue(sizeOf(EntitySelectors.create(b => b.type('minecraft:cow').limit(64))) >= 1,
  'type(cow) on create() sees non-player entities (includesEntities fix)');
Test.assertTrue(sizeOf(EntitySelectors.builder().type('minecraft:cow').limit(64).create()) >= 1,
  'type(cow) on builder() sees non-player entities (includesEntities fix)');
Test.assertEquals(taggedCow,
  sizeOf(EntitySelectors.create(b => b.tag('q25f').type('minecraft:cow').limit(64))),
  'tag + type(cow) -> the tagged cows');
Test.assertEquals(taggedP1,
  sizeOf(EntitySelectors.create(b => b.tag('q25f').type('minecraft:pig').limit(64))),
  'tag + type(pig) -> the tagged pig');
Test.assertEquals(taggedAll - taggedP1,
  sizeOf(EntitySelectors.create(b => b.tag('q25f').type('minecraft:pig', true).limit(64))),
  'tag + type(pig, inverse) -> everything tagged except the pig');
Test.assertEquals(0,
  sizeOf(EntitySelectors.create(b => b.tag('q25f_c1').type('minecraft:cow', true).limit(64))),
  'tag + inverse type(cow) excludes the cow -> 0');
Test.assertEquals(0, sizeOf(EntitySelectors.create(b => b.type('minecraft:player').limit(64))),
  'type(player) stays player-scoped -> 0 players online');
Test.assertEquals(taggedBee, sizeOf(EntitySelectors.allEntities().tag('q25bee')
  .typeTag('minecraft:beehive_inhabitors').limit(64).create()),
  'typeTag(entity_type tag) hits the tagged bee');
Test.assertTrue(sizeOf(EntitySelectors.create(b => b.typeTag('minecraft:beehive_inhabitors')
  .limit(64))) >= taggedBee,
  'typeTag alone switches to the entity base (includesEntities fix)');

// ---- 距离语义：锚点到实体脚底（NoAI 固定位置）----
Test.assertEquals(taggedAll,
  sizeOf(EntitySelectors.allEntities().tag('q25f').distanceBelow(8).limit(64).create()),
  'distanceBelow(8) keeps the tagged set near the anchor');
Test.assertEquals(0,
  sizeOf(EntitySelectors.allEntities().tag('q25f').distanceAbove(8).limit(64).create()),
  'distanceAbove(8) misses the near tagged set');
Test.assertEquals(taggedBee,
  sizeOf(EntitySelectors.allEntities().tag('q25bee').distanceAbove(8).limit(64).create()),
  'distanceAbove(8) hits the far tagged bee');
Test.assertEquals(0,
  sizeOf(EntitySelectors.allEntities().tag('q25bee').distanceBelow(8).limit(64).create()),
  'distanceBelow(8) misses the far tagged bee');

// ---- limit / 顺序选择器 ----
Test.assertEquals(Math.min(2, taggedAll),
  sizeOf(EntitySelectors.allEntities().tag('q25f').limit(2).create()),
  'limit(2) caps the tagged set');
Test.assertEquals(1, sizeOf(EntitySelectors.allEntities().tag('q25f').orderNearest().limit(1).create()),
  'orderNearest().limit(1) returns exactly one');
const nearest = EntitySelectors.find(level,
  EntitySelectors.allEntities().tag('q25f').orderNearest().limit(1).create(), ax, ay, az);
Test.assertNotNull(nearest.get(0), 'orderNearest result is a materialized entity');

// ---- 非法 selector / 非法 level / 缺失输入：普通错误 ----
Test.assertThrows(() => EntitySelectors.create(null), 'create(null) -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.limit(0)), 'limit(0) -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.distance(-1, 5)), 'negative distance -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.distance(5, 1)), 'min>max distance -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.distanceAbove(-1)), 'negative distanceBelow -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.level(5, 1)), 'min>max level -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.type('nekojs:no_such_type')),
  'unknown entity type -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.typeTag('nekojs:no_such_tag')),
  'unknown entity type tag -> labeled error (not a silent empty no-op)');
Test.assertThrows(() => EntitySelectors.create(b => b.gamemode('hacker')), 'unknown gamemode -> labeled error');
Test.assertThrows(() => EntitySelectors.find(null, EntitySelectors.allPlayers().create(), 0, 0, 0),
  'null level -> labeled error');
Test.assertThrows(() => EntitySelectors.find(level, null, 0, 0, 0), 'null selector -> labeled error');

// ---- 只读：find 不改变世界状态——重复查询结果一致 ----
Test.assertEquals(taggedAll, sizeOf(EntitySelectors.allEntities().tag('q25f').limit(64).create()),
  'repeated query is side-effect free');

Test.summary();
