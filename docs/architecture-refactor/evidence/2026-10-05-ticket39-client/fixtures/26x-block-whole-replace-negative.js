BlockEvents.modification(event => {
  event.modify('minecraft:stone', block => {
    block.hardness = 2;
    block.resistance = 8;
  });
  event.modify('minecraft:stone', block => {
    block.lightLevel = 7;
  });
});

let observed = false;
ServerEvents.tickPost(() => {
  if (observed) return;
  observed = true;
  const stone = Block.id('minecraft:stone');
  const state = stone.defaultBlockState();
  console.info('[ticket39-negative] stoneHardness=' + state.getDestroySpeed(null, null)
    + ' stoneResistance=' + stone.explosionResistance + ' stoneLight=' + state.lightEmission);
});
