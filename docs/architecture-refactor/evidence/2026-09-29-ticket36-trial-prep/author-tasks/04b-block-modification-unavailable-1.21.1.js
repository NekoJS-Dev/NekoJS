// 票 36 试做示例 4b：Block modification —— 1.21.1 节点的显式拒绝变体
//
// 节点：1.21.1（放置：server_scripts/）。
// capability 事实（票 39 REPORT §8）：`modification.block` 在 1.21.1 为 unavailable——
// 该节点没有 BlockEvents.modification 总线（platformGate 基线 1.21.1 行的 BlockEvents
// buses 列表无 modification；成对 owner 的 preflight 只接受 item）。Item 半边在 1.21.1
// 仍为 supported（四个基础属性）。
//
// 预期（显式拒绝，不是静默 no-op）：`BlockEvents.modification` 在 1.21.1 上触发
// 成员解析错误，形如（EventGroupJS.getMember）：
//   No such event bus: BlockEvents.modification
// 脚本加载/预检侧还应伴随「无此成员」类定位诊断（GlobalBindingMemberValidator /
// EventCallbackSourceValidator 面）。试做时记录实际输出；只要失败点名成员且可定位，
// 即符合 AC3 口径。本变体不是「在 1.21.1 上寻找 block 修改替代路径」的练习。
//
// 注：04 的 Item 半边（ItemEvents.modification）在 1.21.1 上可用，但复合组件段
// （food/tool）是 26.x 面——1.21.1 只有四个基础属性（maxStackSize/rarity/maxDamage/
// fireResistant 面，见票 39 MIGRATION 能力表）；在 1.21.1 上跑 04 时预期复合组件声明
// 被拒绝或不可用，同样按实际输出记录。

BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.hardness = 2.0
  })
})
