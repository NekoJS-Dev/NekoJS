// startup_scripts/registry.js —— 启动期声明注册最小示例（ticket 15）
//
// 只使用已通过 gate 的启动注册能力：default 类型糖方法、命名类型、custom、
// register(Supplier)、setter/property parity、连带注册。服务器运行期的
// Dynamic Registry（ServerEvents.dynamicRegistry 面）是独立生命周期，不在此入口。
//
// 本示例与 RegistryStartupMinimalExampleTest 的 STARTUP_EXAMPLE 逐行一致，
// 由该测试经真实 GraalJS 按生产序列（挂监听 → 首 pass 前恰好一次收集 →
// 逐注册表抽干到节点 sink）端到端跑通。

RegistryEvents.register(event => {
  // 1) default 类型糖方法（免类型名）：registry 由方法名解析（sound_event）
  event.soundEvent('mymod:boom', b => { b.fixedRange = 16 });

  // 2) 命名类型显式传入（同一糖方法的第二形态）
  event.soundEvent('mymod:ping', 'basic', b => { b.setFixedRange(32) });

  // 3) custom：按全局唯一类型名解析注册表（类型名跨注册表歧义时会被拒绝）
  event.custom('mymod:art', 'art', b => { b.width = 32; b.height = 32 });

  // 4) 裸 Supplier 高级入口：Runtime 校验返回值（非空）、实际类型与重复 ID；
  //    不承诺任意 Supplier 副作用的指纹/回滚
  event.register('minecraft:villager_type', 'mymod:scholar', () => 'raw-scholar');

  // 5) setter/property parity：两种写法调用同一个 setter，进入同一校验、
  //    规范化与 definition fingerprint（rarity 写 'EPIC' 与 'epic' 归一化相同）
  event.item('mymod:via_property', b => { b.maxStackSize = 16; b.rarity = 'EPIC' });
  event.item('mymod:via_setter', b => { b.setMaxStackSize(16); b.setRarity('epic') });
});

// 连带注册（约定带出，按裁定）：
//   event.block('mymod:ruby_block', b => { b.hardness = 3 });
//     —— 默认预创建 BlockItem 子 builder，在 item 注册表自己的 pass 注册
//   event.block('mymod:rose', b => { b.noItem() });
//     —— noItem() 抑制连带（b.item = null 是同一写入点）
//   event.fluid('mymod:molten_iron', b => { b.noBucket() });   // NeoForge 面
// 连带条目等目标注册表自己的注册 pass；目标 pass 已过的连带会得到
// additional-target 错误且不注册（loader 注册序不可回溯）。
