let ticks = 0;
let previous = '';
ClientEvents.playerTickPost(event => {
  ticks++;
  if (ticks % 20 !== 0) return;
  const diamond = Item.of('minecraft:diamond');
  const stick = Item.of('minecraft:stick');
  const lamp = Block.id('minecraft:redstone_lamp').defaultBlockState();
  const held = event.entity.mainHandItem;
  const heldReading = held.empty ? 'held=empty'
    : 'heldId=' + held.id + ' heldCount=' + held.count
      + ' heldMax=' + held.maxStackSize + ' heldRarity=' + held.rarity;
  const reading = 'diamondMax=' + diamond.maxStackSize + ' diamondRarity=' + diamond.rarity
    + ' stickMax=' + stick.maxStackSize + ' stickRarity=' + stick.rarity
    + ' lampDefaultLight=' + lamp.lightEmission + ' ' + heldReading;
  if (reading === previous) return;
  previous = reading;
  console.info('[ticket39] client ' + reading);
});
