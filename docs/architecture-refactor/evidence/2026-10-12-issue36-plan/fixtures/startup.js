// Restart the game after changing this startup fixture.
RegistryEvents.register(event => {
  let retainedFood;
  event.item('issue36:food', build => {
    build.food(food => { food.nutrition(4); retainedFood = food; });
  });
  retainedFood.nutrition(9);
  event.item('issue36:gem', build => {
    build.maxStackSize = 32;
    build.components = {'minecraft:max_stack_size':16, 'minecraft:rarity':'epic', 'minecraft:custom_data':{issue36:1}};
    build.parentModel = 'minecraft:item/generated';
    build.texture('minecraft:item/diamond');
  });
  event.item('issue36:record', build => {
    build.jukeboxPlayable = 'minecraft:cat';
    build.texture('minecraft:item/music_disc_cat');
  });
  event.item('issue36:trim', build => {
    if ('trimMaterial' in build) build.trimMaterial = 'minecraft:diamond';
    build.texture('minecraft:item/diamond');
  });
  event.block('issue36:block', build => {
    build.item(item => {
      item.component('minecraft:rarity','rare');
      item.texture('minecraft:item/emerald');
    });
  });
  event.block('issue36:no_item', build => { build.noItem(); });
});
