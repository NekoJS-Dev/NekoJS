ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 500;
    item.rarity = 'common';
  });
});

BlockEvents.modification(event => {
  event.modify('minecraft:redstone_lamp', block => {
    block.lightLevel = 2;
  });
});
