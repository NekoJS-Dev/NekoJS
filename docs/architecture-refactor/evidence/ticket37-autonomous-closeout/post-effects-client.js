let ticks = 0;
let playerTicks = 0;
ClientEvents.tickPost(() => {
  ticks++;
  if (ticks % 20 !== 0 || ticks > 6000) return;
  const reading = JSON.stringify({ ticks, generation: PostEffects.activeGeneration(), declared: PostEffects.hasDefinition('ticket37:declared'), available: PostEffects.isAvailable('minecraft:invert'), current: PostEffects.current(), active: PostEffects.isActive(), installed: String(PostEffects.installed()) });
  console.info('[ticket37-postfx] ' + reading);
});
ClientEvents.postEffects(event => {
  event.register('ticket37:declared', { blurRadius: 2, blurRounds: 1 });
});
ClientEvents.playerTickPost(() => {
  playerTicks++;
  if (playerTicks === 200) console.info('[ticket37-postfx] set=' + PostEffects.set('minecraft:invert'));
  if (playerTicks === 400) console.info('[ticket37-postfx] clear=' + PostEffects.clear());
});
