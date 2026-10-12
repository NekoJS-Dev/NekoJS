RegistryEvents.register(event => {
  event.item('issue36:invalid_codec', build => {
    build.component('minecraft:rarity', 'not_a_rarity');
    build.texture('minecraft:item/diamond');
  });
});