// 票 36 脚本作者试做示例 4/11：Item / Block modification（运行期属性修改）
//
// 来源：合并复用票 39 基线两份示例（内容保持原文，仅并置）：
//   docs/architecture-refactor/baseline/2026-09-16-item-block-modification/examples/item-modification.js
//   docs/architecture-refactor/baseline/2026-09-16-item-block-modification/examples/block-modification.js
// 放置：server_scripts/item-block-modification.js；`/nekojs reload server` 生效。
// 节点 capability（票 39 REPORT §8）：
//   * modification.item：五节点 supported（1.21.1 为四个基础属性，组件发布走反射）；
//   * modification.block：26.x 面（含两个 fabric 26.x）supported；1.21.1 unavailable
//     （无总线，脚本得明确「无此成员」错误——见 04b 变体）；
//   * 客户端自动同步 = unsupported（纯视觉结果需 relog 或区块 resync）。

// server_scripts/item-modification.js —— Item 属性修改（票 39 最小示例 1/3）
//
// 语义（ticket 39）：
//   * 事件面不变：ItemEvents.modification(event => ...)，payload 仍是同一个 wrapper，
//     仍然在「服务器启动收集点」与 SERVER reload 的 DOMAIN_PLAN 阶段被 post；
//   * 收集期只记录声明（inert）：event.modify(...) 不碰 live Item、默认组件或其他 generation；
//   * commit 点由平台 Adapter（26.x/1.21.1 各自的 ModificationDomainOwner）先恢复
//     NekoJS 持有基线、再按声明顺序应用完整新计划；
//   * 预检（联合 STATE_PLAN）失败 → 整批不提交，旧 active 继续服务（无部分修改）；
//   * 本脚本移除某个 event.modify 后，成功 reload 会把它改回基线（不再静默 stale）。
ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 16          // 与 item.setMaxStackSize(16) 同一 setter
    item.rarity = 'epic'            // 序列名归一（'EPIC' 亦可）
  })

  // 组件不变量在联合预检（STATE_PLAN）按「基线 + 声明」求值：可堆叠 + 可损坏必须显式声明
  // maxStackSize = 1，否则整批被拒（保留旧 active，不部分应用）
  event.modify('minecraft:golden_apple', item => {
    item.maxStackSize = 1
    item.maxDamage = 32
  })
  // fireResistant = true 需要已绑定的 server（26.x 用 DAMAGE_RESISTANT 指向 IS_FIRE tag，
  // 依赖 damage type registry）：在服务器启动收集点/事务 reload 上都已绑定；
  // 未绑定时 Adapter 预检会拒绝**整批**（不部分应用）。示例里单列一行说明，不写进本段。

  // 复合组件（26.x 面；1.21.1 只有四个基础属性，见 MIGRATION.md 的能力表）
  event.modify('minecraft:stick', item => {
    item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }
  })
  event.modify('minecraft:blaze_rod', item => {
    item.tool = { miningSpeed: 6 }
    item.attackDamage = 6
    item.attackSpeed = -2.4
  })
})

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
