// server_scripts/block-modification.js —— Block 属性修改（票 39 最小示例 3/3；26.x 面）
//
// 1.21.1 没有 BlockEvents.modification 总线（见 MIGRATION.md 能力表）：脚本在 1.21.1 上
// 会得到明确的「无此成员」错误，而不是静默 no-op。
//
// 写入面（commit 点由平台 Adapter 执行，三个副本一起更新）：
//   BlockBehaviour.Properties 声明源 + Block 副本 + 该方块每个 BlockState 副本；
//   状态相关光照函数（如红石灯的 LIT 状态）按基线整体恢复，不会被写成常量。
BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.hardness = 2.0        // ≡ block.setHardness(2.0)
    block.resistance = 8.0
    block.requiresTool = true
  })

  // 多状态方块：写进每个 state；lightLevel 恢复时回到原始 per-state 函数
  event.modify('minecraft:redstone_lamp', block => {
    block.lightLevel = 7
  })

  event.modify('minecraft:oak_fence', block => {
    block.friction = 0.9
    block.jumpFactor = 1.1
  })
})

// 客户端可见性（票 39 AC10 的显式边界，不靠隐藏漂移）：
//   * 服务端立刻生效（所有已有 BlockState 副本一起更新，服务端逻辑与掉落/光照计算随之变化）；
//   * 纯视觉结果（lightLevel 等）不自动同步给已连接的客户端——需要 relog 或区块 resync；
//   * 客户端可见性有真实 fixture 记录：见 baseline REPORT 的 capability/source-trace 表
//     （自动同步 = unsupported，显式 resync = partial，relog = supported）。
