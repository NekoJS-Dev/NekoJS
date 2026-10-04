let ticks = 0;
let previous = '';
ServerEvents.tickPost(() => {
  ticks++;
  if (ticks % 20 !== 0) return;
  const diamond = Item.of('minecraft:diamond');
  const stick = Item.of('minecraft:stick');
  const lamp = Block.id('minecraft:redstone_lamp').defaultBlockState();
  const reading = 'diamondMax=' + diamond.maxStackSize + ' diamondRarity=' + diamond.rarity
    + ' stickMax=' + stick.maxStackSize + ' stickRarity=' + stick.rarity
    + ' lampDefaultLight=' + lamp.lightEmission;
  if (reading === previous) return;
  previous = reading;
  console.info('[ticket39] server phase=baseline-or-restored ' + reading);
});
