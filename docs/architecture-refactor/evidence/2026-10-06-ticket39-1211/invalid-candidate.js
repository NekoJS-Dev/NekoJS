ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => { item.maxStackSize = 0; });
});
