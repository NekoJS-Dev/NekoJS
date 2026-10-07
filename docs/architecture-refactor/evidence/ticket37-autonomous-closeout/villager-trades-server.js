ServerEvents.tradeDeclaration(event => {
  event.add('minecraft:farmer/level_1', { cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2, priceMultiplier: 0.05 });
});
let ticks = 0;
ServerEvents.tickPost(() => {
  ticks++;
  if (ticks % 100 !== 0 || ticks > 1200) return;
  const query = VillagerTrades.query();
  console.info('[ticket37-trades] ' + JSON.stringify({ ticks, status: String(query.status), reason: query.statusReason, generation: query.generation, adapter: query.adapterId, total: query.total, count: query.countOf('minecraft:farmer/level_1'), description: query.describe() }));
});
