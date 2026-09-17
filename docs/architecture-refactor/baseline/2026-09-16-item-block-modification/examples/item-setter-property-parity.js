// server_scripts/item-setter-property-parity.js —— 显式 setter 与 property 赋值等价（票 39 最小示例 2/3）
//
// 两种写法经 ModificationViewSurface（ProxyObject putMember → 同一 Method seam）落到
// **同一个 Java setter**，因此校验、规范化、声明与 definition fingerprint 完全一致：
//   item.maxStackSize = 16   ≡   item.setMaxStackSize(16)
//   block.hardness = 2       ≡   block.setHardness(2)
//
// 事实依据（票 39 characterization）：改造前宿主视图上的 property 写在 GraalJS 上被静默
// 丢弃（既不落字段也不落 setter），只有显式 setter 生效；现在两种写法都生效且同源。
// 越界/未知成员/类型不匹配在写入期抛出带成员名的错误（不再有静默丢弃）。
ItemEvents.modification(event => {
  // 写法 A：JavaBean-style property 赋值
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 16
    item.rarity = 'epic'
  })

  // 写法 B：显式 setter（与 A 产生同一规范化声明）
  event.modify('minecraft:emerald', item => {
    item.setMaxStackSize(16)
    item.setRarity('epic')
  })

  // 只读成员与未知成员：写入被拒绝并列出可写成员目录（错误由同一份成员目录派生）
  // item.id = 'x'            → 抛出 "has no member 'id'; known: [...]"
  // item.definitelyNotAMember = 1 → 抛出 "has no member 'definitelyNotAMember'; known: [...]"
})
