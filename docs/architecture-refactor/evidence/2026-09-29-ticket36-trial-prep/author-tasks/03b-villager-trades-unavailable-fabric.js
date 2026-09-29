// 票 36 试做示例 3b：Villager Trades —— fabric 节点的显式 unavailable 变体
//
// 来源：改编自票 22 基线示例
// docs/architecture-refactor/baseline/2026-09-21-villager-trades/examples/villager-trades-unavailable-fabric.js
// 节点：26.1.2-fabric / 26.2.0-fabric（放置：server_scripts/）。
// capability 事实（票 22 REPORT AC9 + MIGRATION §1/§2）：fabric 无村民交易注册表
// mutation 适配器，装配的是显式 unavailable 的 domain owner——声明照常收集，整批在
// 联合 preflight 以 VillagerTradeUnavailableException 拒绝（原因写明 fabric 无实现），
// `/nekojs reload server` 报结构化失败（phase STATE_PLAN），旧 active 保留。这不是
// 静默 no-op，也不允许试做者去寻找隐藏替代路径。
//
// 预期（按段记录实际输出）：
//   1) 声明段：reload 失败，输出形如（票 22 示例头注原文）：
//      "[villager-trades] state-plan-preflight failed ... villager trade registry mutation
//      has no Fabric implementation in this port (no adapter is registered; the declaration
//      is rejected, not ignored)"
//   2) 查询段：见下方 TODO——这是本任务包在 HEAD 复核时发现的公开材料与实现的
//      差异，试做时必须实际验证并如实记录。
//
// TODO(HEAD 复核差异，试做时验证)：票 22 基线示例断言 fabric 上
// `VillagerTrades.query()` 返回 STALE + 'unavailable:...'；但 HEAD 源码里
// `VillagerTrades` binding 由 VillagerTradesPlugin 注册，该插件整文件 `//? if neoforge`
// 守卫（src/main/java/com/tkisor/nekojs/villager/VillagerTradesPlugin.java），fabric 侧
// FabricCorePlugin 未注册同名 binding（仅 NekoJSFabricMod 装配了 unavailable owner 的
// 查询状态）。若 fabric 上 `VillagerTrades` 确实未注册，下面的查询段预期得到
// 「未知标识符」/ReferenceError 而不是 STALE——以实跑为准；该差异本身就是
// AC4「公开材料是否足够」的试做发现，记录为待修复项而不是绕过它。

ServerEvents.tradeDeclaration(event => {
  event.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',
    result: '5x minecraft:apple'
  })
  // The declaration above is collected and then rejected as a whole; expect a reload failure
  // like: "[villager-trades] state-plan-preflight failed ... villager trade registry mutation
  // has no Fabric implementation in this port (no adapter is registered; the declaration is
  // rejected, not ignored)".
})

ServerEvents.started(event => {
  const result = VillagerTrades.query()
  console.info(result.describe())
  // Expected on Fabric per the ticket 22 baseline: status STALE, statusReason starting with
  // 'unavailable:' ('villager trade registry mutation is unavailable on this node ...'), total 0.
  // HEAD review says this binding may not be registered on Fabric at all — verify live and
  // record which of the two behaviors actually happens (see TODO above).
  if (result.status !== 'ACTIVE') {
    console.warn('villager trades are not available on this node: ' + result.statusReason)
  }
})
