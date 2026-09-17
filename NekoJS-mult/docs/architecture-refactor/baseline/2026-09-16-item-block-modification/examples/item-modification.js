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
