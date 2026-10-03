// 票 36 脚本作者试做示例 3/11：Villager Trades add/query
//
// 来源：逐字复用票 22 基线示例
// docs/architecture-refactor/baseline/2026-09-21-villager-trades/examples/villager-trades-add-and-query.js
// 节点：26.x NeoForge / 1.21.1 capability=supported（mutation adapter 各自成对实现）；
// fabric 两节点为显式 unavailable（整批拒绝 + query 不可用，见 03b 变体）。
// 依据：票 22 REPORT §3（AC9/AC11）与 MIGRATION.md §1 能力表。示例为源码级交付，
// 未在真机执行（票 22 G2）——试做时按 TASKS.md 记录实际输出。
//
// Ticket 22 minimal runnable example: declare villager / wandering trader trades through the
// existing ServerEvents data sub-event and read the committed result through the read-only,
// generation-bound query.
//
// Place under <gameDir>/nekojs/server_scripts/ and run `/nekojs reload server` (or start the
// server). Requires a node with a villager trade adapter (26.x NeoForge, 1.21.1 NeoForge);
// see examples/villager-trades-unavailable-fabric.js for the explicit unavailable path.

// The trade set ids are node-shaped:
//   26.x NeoForge : 'minecraft:farmer/level_1'   (VILLAGER_TRADE / TRADE_SET reloadable registries)
//   1.21.1 NeoForge: 'minecraft:farmer/level_1'   (classic profession pools; also 'minecraft:wandering_trader/level_1')

ServerEvents.tradeDeclaration(event => {
  // add(tradeSet, config) — the only write this first version exposes.
  event.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',        // required; 'minecraft:emerald' means count 1
    costB: '2x minecraft:apple',         // optional secondary cost
    result: '5x minecraft:apple',        // required
    maxUses: 12,
    xp: 2,
    priceMultiplier: 0.05
  })

  // The same declaration twice in one batch collapses into one listing (idempotent).
  event.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald',
    result: '1x minecraft:book'
  })

  console.info('declared ' + event.addedCount + ' trade declaration(s)')
})

// The reload sub-event is where a script that owns a trade set releases it. Ordinary reload
// never deletes trades that are simply no longer declared: they enter the unrestored record
// instead (visible in query().unrestoredListingKeys).
ServerEvents.tradeReload(event => {
  console.info('this batch will touch ' + event.total + ' trade declaration(s)')
  // event.declareObsolete('minecraft:farmer/level_1')  // -> restored to the vanilla baseline at commit
})

// Read-only, generation-bound query. Run it after the reload finished (e.g. from a
// ServerEvents.started / tick callback), never while a candidate is being built.
ServerEvents.started(event => {
  const result = VillagerTrades.query()
  console.info(result.describe())
  if (result.status === 'ACTIVE') {
    console.info('committed generation ' + result.generation
      + ' via ' + result.adapterId
      + ': ' + result.total + ' trade(s)')
    console.info('trade sets: ' + result.tradeSetIds)
    console.info('level_1 count: ' + result.countOf('minecraft:farmer/level_1'))
    if (!result.unrestoredListingKeys.isEmpty()) {
      // Declared earlier, not declared any more: still active in the registry, recorded here
      // instead of being physically deleted by the reload.
      console.warn('unrestored listings: ' + result.unrestoredListingKeys)
    }
  } else {
    // STALE means this generation is not (or no longer) the active one, or no commit happened
    // yet: the members are deterministic empties, never another generation's data.
    console.warn('VillagerTrades.query() is stale: ' + result.statusReason)
  }
})
