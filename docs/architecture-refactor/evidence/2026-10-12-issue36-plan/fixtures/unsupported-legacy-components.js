RegistryEvents.register(event => {
  event.item('issue36:registry_codec', build => {
    build.component('minecraft:potion_contents', {potion:'minecraft:water'});
    build.texture('minecraft:item/diamond');
  });
  event.item('issue36:registry_trim', build => {
    build.component('minecraft:trim', {material:'minecraft:diamond',pattern:'minecraft:sentry'});
    build.texture('minecraft:item/diamond');
  });
  event.item('issue36:unknown_component', build => {
    build.component('issue36:unknown_component', 1);
    build.texture('minecraft:item/diamond');
  });
});