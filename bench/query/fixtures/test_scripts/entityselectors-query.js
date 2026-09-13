// Ticket 25 fixture: EntitySelectors factory/builder/query 全链路（server side）。
// 载体：/nekojs test（真实 ServerLevel + 真实实体）。预期全绿。
//
// == 世界状态前置（RCON，必须在跑本脚本之前完成；配方见 REPORT §5 / bench/query/README.md）==
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
// == 作用域语义（游戏内实读 + 工单 25 双轴审查裁定，见 REPORT §5.7）==
// - `builder()` / `create(cfg)` 的基座是**玩家集合**（`includesEntities=false`，等价 @a）；
//   `allEntities()` / `nearestEntity()` / `randomEntity()` 预设的基座是**实体集合**。
//   vanilla 侧同样是「按前缀决定」：EntitySelectorParser 的 includesEntities 无初值，
//   @e/@r 置 true、@a/@p 置 false；builder 无前缀，因此由基座预设给出。
// - **审查裁定：`type(...)` / `typeTag(...)` 不切作用域**（只有玩家类型会调整该位）。
//   本票曾把「非玩家类型正选 / 任意反选 / typeTag」改为置 `includesEntities=true`，
//   让 `create(b => b.type('minecraft:cow'))` 能命中实体——审查判为**越权改公开语义**
//   （选择器作用域变化无人授权），已回退。因此文档示例风格的
//   `create(b => b.type('minecraft:cow'))` 在无玩家的服务器上命中 **0**，这是**已记录
//   缺口**（REPORT §7，owner = 维护者裁决 / domain 票），本 fixture 把它**钉成现状**。
// - **AC2 的取证路径（不改语义）**：实体命中走实体基座（`allEntities()` 等预设），
//   以及既有语义里的 `type('minecraft:player', true)`（反选玩家 = 实体集合，
//   原实现即 `isPlayerType → includesEntities=true`）——两条路径都未改语义。
//
// == 未知 type tag（characterization，回退后现状）==
// - `typeTag('nekojs:no_such_tag')` **不报错**：`resolveEntityTypeTag` 静默构造一个没有
//   成员的 TagKey，于是过滤恒假、查询恒空（silent no-op）。spec 04 禁止这种形态，属已记录
//   缺口（REPORT §7，owner = 维护者裁决）；对照面 `type('nekojs:no_such_type')` 直接报错，
//   两者不一致本身就是缺口证据。本 fixture 钉住「不抛 + 恒空」的现状。
//
// == 非法输入 ==
// - 非法输入（limit/distance/level/gamemode/type、find 的 level/selector、create 的 config）
//   得到带域+调用入口的普通错误，不含修复提示；脚本源位置由统一错误管线补。

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

// ================= AC2 (a)：实体基座（预设）+ builder + query 真实命中 =================
// 入口面：allEntities() 是 EntitySelectorsJS 的 factory 预设；.tag()/.type()/.distanceBelow()
// /.limit()/.orderNearest() 是 builder；.create() 产出 EntitySelector；find 执行查询。
Test.assertEquals(taggedAll, sizeOf(EntitySelectors.allEntities().tag('q25f').limit(64).create()),
  'allEntities() + tag + find: hits every tagged entity (factory preset + builder + query)');
Test.assertEquals(taggedCow,
  sizeOf(EntitySelectors.allEntities().tag('q25f').type('minecraft:cow').limit(64).create()),
  'allEntities() + tag + type(cow): hits exactly the tagged cows');
Test.assertEquals(taggedP1,
  sizeOf(EntitySelectors.allEntities().tag('q25f').type('minecraft:pig').limit(64).create()),
  'allEntities() + tag + type(pig): hits the tagged pig');
Test.assertEquals(taggedAll - taggedP1,
  sizeOf(EntitySelectors.allEntities().tag('q25f').type('minecraft:pig', true).limit(64).create()),
  'allEntities() + inverse type(pig): everything tagged except the pig');
Test.assertEquals(taggedBee,
  sizeOf(EntitySelectors.allEntities().tag('q25bee')
    .typeTag('minecraft:beehive_inhabitors').limit(64).create()),
  'allEntities() + typeTag(declared entity type tag): hits the tagged bee');

// ================= AC2 (b)：create(cfg) / builder() 的实体命中（既有语义路径）=================
// `type('minecraft:player', true)` 走的是**原实现就有**的分支（inverse + 玩家类型 →
// includesEntities=true），即「除玩家之外的所有实体」，不改任何语义。
const nonPlayerViaFactory = sizeOf(EntitySelectors.create(b => b.type('minecraft:player', true)
  .limit(64)));
Test.assertTrue(nonPlayerViaFactory >= taggedAll && nonPlayerViaFactory <= 64,
  'create(cfg) + type(player, inverse) runs the selector and hits every non-player entity, got: '
    + nonPlayerViaFactory + ' (tagged=' + taggedAll + ')');
Test.assertEquals(
  sizeOf(EntitySelectors.builder().type('minecraft:player', true).limit(64).create()),
  nonPlayerViaFactory,
  'create(cfg) and builder() build the same selector shape -> identical results');
Test.assertEquals(taggedAll,
  sizeOf(EntitySelectors.builder().type('minecraft:player', true).tag('q25f').limit(64).create()),
  'builder() + inverse type(player) + tag: the tagged entities (builder entry hits real entities)');

// 预设的 order 语义：nearestEntity()/randomEntity() 只取 1 个，且结果是已物化实体。
Test.assertEquals(1, sizeOf(EntitySelectors.nearestEntity().create()),
  'nearestEntity() preset returns exactly one materialized entity');
Test.assertEquals(1, sizeOf(EntitySelectors.randomEntity().create()),
  'randomEntity() preset returns exactly one materialized entity');
const nearest = EntitySelectors.find(level,
  EntitySelectors.allEntities().tag('q25f').orderNearest().limit(1).create(), ax, ay, az);
Test.assertNotNull(nearest.get(0), 'orderNearest result is a materialized entity');

// ================= AC2 (c)：玩家基座语义（无玩家时为空，语义正确）=================
Test.assertEquals(0, sizeOf(EntitySelectors.builder().tag('q25f').limit(64).create()),
  'scope: a tag-only builder() stays on the player base (0 players online) -> empty');
Test.assertEquals(0, sizeOf(EntitySelectors.create(b => b.tag('q25f').limit(64))),
  'scope: create(cfg) base is the player scope (0 players online) -> empty');
Test.assertEquals(0, sizeOf(EntitySelectors.allPlayers().limit(64).create()),
  'scope: allPlayers() preset stays player-only -> empty snapshot');
Test.assertEquals(0, sizeOf(EntitySelectors.builder().type('minecraft:player').limit(64).create()),
  'scope: type(player) stays player-scoped -> 0 players online');

// ========== characterization：非玩家类型过滤**不**切作用域（审查回退后的现状 = 缺口）==========
// 这几条是「本票只记录、不改公开语义」的书面证据；修法（显式实体类型切作用域）已实现过并
// 被审查判为越权。缺口与 owner 见 REPORT §7。
Test.assertEquals(0, sizeOf(EntitySelectors.create(b => b.type('minecraft:cow').limit(64))),
  'GAP: create(b => b.type(cow)) does NOT switch scope -> 0 (documented-example style, see REPORT §7)');
Test.assertEquals(0, sizeOf(EntitySelectors.builder().type('minecraft:cow').limit(64).create()),
  'GAP: builder().type(cow) keeps the player base -> 0');
Test.assertEquals(0, sizeOf(EntitySelectors.create(b => b.type('minecraft:cow', true).limit(64))),
  'GAP: inverse of a non-player type also keeps the player base -> 0');
Test.assertEquals(0,
  sizeOf(EntitySelectors.builder().typeTag('minecraft:beehive_inhabitors').limit(64).create()),
  'GAP: typeTag() alone keeps the player base -> 0 (entity base needed for hits)');

// ========== characterization：未知 type tag 静默接受（不抛 + 过滤恒假）==========
// 注意：`type('nekojs:no_such_type')` 走 assertThrows（下方非法输入组）——两者不一致。
let unknownTagAccepted = true;
try {
  EntitySelectors.builder().typeTag('nekojs:no_such_tag');
} catch (e) {
  unknownTagAccepted = false;
}
Test.assertTrue(unknownTagAccepted,
  'GAP: unknown entity type tag is silently accepted (no error, silent no-op, see REPORT §7)');
Test.assertEquals(0, sizeOf(EntitySelectors.builder().typeTag('nekojs:no_such_tag').limit(64).create()),
  'GAP: an unknown tag keeps the player base -> 0 (indistinguishable from a real empty filter)');
Test.assertEquals(0,
  sizeOf(EntitySelectors.allEntities().tag('q25f').typeTag('nekojs:no_such_tag').limit(64).create()),
  'GAP: an unknown tag on the entity base filters everything out -> 0 (silent, no error)');

// ================= 距离语义：锚点到实体脚底（NoAI 固定位置）=================
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

// ================= limit / 顺序选择器 =================
Test.assertEquals(Math.min(2, taggedAll),
  sizeOf(EntitySelectors.allEntities().tag('q25f').limit(2).create()),
  'limit(2) caps the tagged set');
Test.assertEquals(1, sizeOf(EntitySelectors.allEntities().tag('q25f').orderNearest().limit(1).create()),
  'orderNearest().limit(1) returns exactly one');

// ================= 非法 selector / 非法 level / 缺失输入：普通错误 =================
Test.assertThrows(() => EntitySelectors.create(null), 'create(null) -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.limit(0)), 'limit(0) -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.distance(-1, 5)), 'negative distance -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.distance(5, 1)), 'min>max distance -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.distanceAbove(-1)), 'negative distanceBelow -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.level(5, 1)), 'min>max level -> labeled error');
Test.assertThrows(() => EntitySelectors.create(b => b.type('nekojs:no_such_type')),
  'unknown entity type -> labeled error (contrast with the silent unknown tag above)');
Test.assertThrows(() => EntitySelectors.create(b => b.gamemode('hacker')), 'unknown gamemode -> labeled error');
Test.assertThrows(() => EntitySelectors.find(null, EntitySelectors.allPlayers().create(), 0, 0, 0),
  'null level -> labeled error');
Test.assertThrows(() => EntitySelectors.find(level, null, 0, 0, 0), 'null selector -> labeled error');

// ================= 只读：find 不改变世界状态——重复查询结果一致 =================
Test.assertEquals(taggedAll, sizeOf(EntitySelectors.allEntities().tag('q25f').limit(64).create()),
  'repeated query is side-effect free');

Test.summary();
