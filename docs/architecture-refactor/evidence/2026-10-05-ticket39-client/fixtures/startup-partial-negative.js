ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => {
    item.maxStackSize = 16;
  });
  throw new Error('[ticket39-negative] intentional startup collection failure');
});

let observed = false;
ServerEvents.tickPost(() => {
  if (observed) return;
  observed = true;
  console.info('[ticket39-negative] diamondMax=' + Item.of('minecraft:diamond').maxStackSize);
});
