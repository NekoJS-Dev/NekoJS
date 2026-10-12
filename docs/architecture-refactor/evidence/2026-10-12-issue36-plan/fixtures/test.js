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
Test.summary();
