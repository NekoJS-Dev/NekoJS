ServerEvents.started(event => {
  if (Item.id('issue36:registry_codec') === null) throw new Error('Built-in potion component should decode without registry ops');
  for (const id of ['issue36:registry_trim','issue36:unknown_component']) {
    if (Item.id(id) !== null) throw new Error('Unsupported component item was registered: ' + id);
  }
  console.info('ISSUE36_UNSUPPORTED_COMPONENTS_REJECTED');
  event.getServer().halt(false);
});