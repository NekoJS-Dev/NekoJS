// Ticket 25 fixture: DataMap 只读查询 binding 行为（命中 / 缺失 / 空值 / 类型转换 / 只读）。
// 载体：/nekojs test（真实注册表 + 数据包加载后的 NeoForge 26.1.2 专用服务器）。预期全绿。
// 前置：无（data map 由注册表/数据包驱动，不需要预置世界状态）。
//
// 脚本可见语义（游戏内实读，见 baseline/2026-09-12-query-tools/REPORT.md §5）：
// - JS null/undefined 经 ItemStackAdapter 映射为 ItemStack.EMPTY（引擎全域转换约定，
//   非本域特有）→ 走「缺失」分支返回 null，不是错误。Java 侧的 null 守卫面向直接
//   Java 调用者（单元层 DataMapQueryBindingTest 断言其带域+入口），脚本层不可达。
// - 非法输入（数值 / 普通对象 / 未注册 item id）由引擎转换层抛普通错误，消息含
//   入口名（furnaceFuel / compostable）与目标类型；脚本源位置由统一错误管线补。
// - 命中返回不可变快照值（Integer / Float），不暴露可变 registry view；方法是纯读。

Test.section('DataMap.query');

// ---- 命中：既有平台 data map 快照 ----
Test.assertEquals(1600, DataMap.furnaceFuel(Item.of('minecraft:coal')),
  'hit: coal burnTime from the vanilla furnace_fuels data map');
const appleChance = DataMap.compostable(Item.of('minecraft:apple'));
Test.assertTrue(typeof appleChance === 'number' && appleChance > 0 && appleChance <= 1,
  'hit: apple compostable chance is a snapshot number in (0,1], got: ' + appleChance);

// ---- 缺失：非燃料 / 不可堆肥 -> null（公开语义，供 ?? 兜底）----
const stone = Item.of('minecraft:stone');
Test.assertEquals(null, DataMap.furnaceFuel(stone), 'miss: stone is not fuel -> null');
Test.assertEquals(null, DataMap.compostable(stone), 'miss: stone is not compostable -> null');

// ---- 类型转换（成功向）：string item id -> ItemStack 经引擎类型包装 ----
Test.assertEquals(1600, DataMap.furnaceFuel('minecraft:coal'),
  'coercion: string item id -> ItemStack -> same burnTime');

// ---- 类型转换（失败向）/ 未注册 id：普通错误，不静默 ----
Test.assertThrows(() => DataMap.furnaceFuel(42), 'invalid: number is not an ItemStack -> error');
Test.assertThrows(() => DataMap.furnaceFuel({}), 'invalid: plain object is not an ItemStack -> error');
Test.assertThrows(() => DataMap.furnaceFuel('minecraft:not_an_item'),
  'invalid: unregistered item id -> error');

// ---- 空值：null / undefined 经引擎适配为 ItemStack.EMPTY -> AIR -> 缺失 null ----
Test.assertEquals(null, DataMap.furnaceFuel(null), 'empty: JS null -> ItemStack.EMPTY -> null');
Test.assertEquals(null, DataMap.furnaceFuel(undefined), 'empty: undefined -> ItemStack.EMPTY -> null');
Test.assertEquals(null, DataMap.compostable(null), 'empty: JS null -> ItemStack.EMPTY -> null');

// ---- 只读：只有查询函数，无写入面（registry mutation 归 Registry Runtime）----
Test.assertTrue(typeof DataMap.furnaceFuel === 'function', 'readonly: furnaceFuel is a query function');
Test.assertTrue(typeof DataMap.compostable === 'function', 'readonly: compostable is a query function');
Test.assertEquals(DataMap.furnaceFuel(Item.of('minecraft:coal')), DataMap.furnaceFuel('minecraft:coal'),
  'readonly: repeated query of the same entry returns the same snapshot');
Test.assertEquals(appleChance, DataMap.compostable('minecraft:apple'),
  'readonly: repeated query of the same entry returns the same snapshot (compostable)');

Test.summary();
