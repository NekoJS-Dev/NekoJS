// Loader smoke uses actual registered items after native component initialization.
const Test = {
  assertTrue: (value, message) => { if (!value) throw new Error(message); },
  assertFalse: (value, message) => { if (value) throw new Error(message); },
  assertEquals: (expected, actual, message) => { if (String(expected) !== String(actual)) throw new Error(message + ': ' + actual); },
  assertNotNull: (value, message) => { if (value === null || value === undefined) throw new Error(message); }
};
ServerEvents.started(event => {
// Run /nekojs test after entering the fixture world.
const Components = Java.type('net.minecraft.core.component.DataComponents');
const gem = Item.of('issue36:gem');
Test.assertTrue(String(Item.of('issue36:food').get(Components.FOOD)).includes('nutrition=4'), 'retained food callback cannot alter the collected definition');
Test.assertFalse(gem.isEmpty(), 'registered gem exists');
Test.assertEquals(16, gem.get(Components.MAX_STACK_SIZE), 'raw stack component overrides the ordinary property');
Test.assertEquals('EPIC', String(gem.get(Components.RARITY)), 'raw rarity is installed on a real stack');
Test.assertNotNull(gem.get(Components.CUSTOM_DATA), 'JSON object component is installed');
Test.assertNotNull(Item.of('issue36:record').get(Components.JUKEBOX_PLAYABLE), 'song reference resolves after registry initialization');
if ('PROVIDES_TRIM_MATERIAL' in Components) {
  Test.assertNotNull(Item.of('issue36:trim').get(Components.PROVIDES_TRIM_MATERIAL), 'trim material reference resolves');
}
Test.assertEquals('RARE', String(Item.of('issue36:block').get(Components.RARITY)), 'nested BlockItem uses the component configuration');
Test.assertTrue(Item.id('issue36:no_item') === null, 'noItem suppresses its BlockItem');
console.info('ISSUE36_NATIVE_PASS');

});

PlayerEvents.loggedIn(event => {
  const player = event.getEntity();
  const BlockPos = Java.type('net.minecraft.core.BlockPos');
  const stone = Block.id('minecraft:stone').defaultBlockState();
  const level = player.level;
  for (let x = -3; x <= 3; x++) {
    for (let z = -3; z <= 3; z++) {
      level.setBlockAndUpdate(new BlockPos(x, 200, z), stone);
    }
  }
  player.teleport(0.5, 201, 0.5);
  player.yRot = 0;
  const hand = Java.type('net.minecraft.world.InteractionHand');
  event.getEntity().setItemInHand(hand.MAIN_HAND, Item.of('issue36:gem'));
  event.getEntity().getInventory().add(Item.of('issue36:record'));
  event.getEntity().getInventory().add(Item.of('issue36:block'));
  const dropped = event.getEntity().drop(Item.of('issue36:gem'), false);
  dropped.setPos(0.5, 201, 2.5);
  dropped.setDeltaMovement(0, 0, 0);
});
