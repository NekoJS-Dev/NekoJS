// Ticket 22 minimal runnable example: the explicit `unavailable` path.
//
// Fabric has no villager trade registry mutation adapter in this port (no
// RegisterVillagerTradesEvent equivalent), so the declaration is NOT silently ignored:
//   * the collection phase runs normally (the bus, the payload and the joint candidate
//     boundary are shared with the other nodes);
//   * the batch is rejected as a whole during the joint preflight with a
//     VillagerTradeUnavailableException naming the node;
//   * `/nekojs reload server` reports a structured failure (phase STATE_PLAN) and the old
//     active trades keep serving;
//   * VillagerTrades.query() answers STALE with reason 'unavailable:...' on that node.
//
// This is the file to run on 26.1.2-fabric / 26.2.0-fabric.

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
  // Expected on Fabric: status STALE, statusReason starting with 'unavailable:'
  // ('villager trade registry mutation is unavailable on this node ...'), total 0.
  if (result.status !== 'ACTIVE') {
    console.warn('villager trades are not available on this node: ' + result.statusReason)
  }
})
