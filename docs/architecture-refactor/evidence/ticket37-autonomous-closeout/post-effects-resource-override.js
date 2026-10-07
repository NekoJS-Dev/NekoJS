let ticks = 0;
let playerTicks = 0;
ClientEvents.postEffects(event => {
  event.register('minecraft:invert', { blurRadius: 16, blurRounds: 1 });
  event.register('ticket37:declared', { blurRadius: 2, blurRounds: 1 });
});
ClientEvents.tickPost(() => {
  ticks++;
  if (ticks % 100 !== 0 || ticks > 24000) return;
  console.info('[ticket37-override] ' + JSON.stringify({
    ticks, generation: PostEffects.activeGeneration(),
    declared: PostEffects.hasDefinition('minecraft:invert'),
    sentinel: PostEffects.hasDefinition('ticket37:declared'),
    available: PostEffects.isAvailable('minecraft:invert'),
    current: PostEffects.current(), active: PostEffects.isActive(),
    installed: String(PostEffects.installed())
  }));
});
ClientEvents.playerTickPost(() => {
  playerTicks++;
  if (playerTicks === 60) console.info('[ticket37-override] set=' + PostEffects.set('minecraft:invert'));
  if (playerTicks === 2400) console.info('[ticket37-override] clear=' + PostEffects.clear());
});
