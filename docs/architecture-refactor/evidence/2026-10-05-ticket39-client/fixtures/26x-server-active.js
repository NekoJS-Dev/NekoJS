ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 16;
    item.rarity = 'epic';
  });
  event.modify('minecraft:stick', item => {
    item.setMaxStackSize(16);
    item.setRarity('epic');
  });
  console.info('[ticket39] declared phase=active items=' + event.modifiedCount);
});

BlockEvents.modification(event => {
  event.modify('minecraft:redstone_lamp', block => {
    block.lightLevel = 7;
  });
  console.info('[ticket39] declared phase=active blocks=' + event.modifiedCount);
});

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
  console.info('[ticket39] server phase=active ' + reading);
});
