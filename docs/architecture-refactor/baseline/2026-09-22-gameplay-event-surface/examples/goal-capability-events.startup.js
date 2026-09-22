// 票 24 最小可运行示例：GoalEvents / CapabilityEvents（startup 脚本，NeoForge 节点）
// 放置：<gameDir>/nekojs/startup_scripts/*.js
//
// 两个家族都是 STARTUP 面：监听器在 mod 构造期加载，posting site 在 STARTUP 脚本
// 加载完成后各触发一次（GoalEvents.postRegister / RegisterCapabilitiesEvent 回调）。
// STARTUP reload 是非事务 reset+load（票 24 REPORT 已 characterization）。

// ---- 1) GoalEvents.register：为实体类型注册内置 AI goal ----
GoalEvents.register(event => {
  // 回调形态：builder 自动 register()
  event.forType('minecraft:zombie', goals => {
    // goals.add(...) 等 builder 成员见 GoalRegistry.GoalBuilderJS
  })
  // 或显式形态：
  const builder = event.forType('minecraft:skeleton')
  // ... 配置 ...
  builder.register()
})

// ---- 2) CapabilityEvents.register：为方块实体注册标准 capability provider ----
// MVP 面：block entity + 'item' / 'energy' / 'fluid' 三类（NeoForge 专属；fabric 节点
// 该家族显式缺席，不是静默 no-op）。
CapabilityEvents.register(event => {
  event.registerBlockEntity('minecraft:chest', 'item', () => {
    // 返回 transfer API 能力实例，如 Capabilities.itemHandler(27)
    return Capabilities.itemHandler(27)
  })
})
