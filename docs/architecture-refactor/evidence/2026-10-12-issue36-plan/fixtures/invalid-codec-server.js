ServerEvents.started(event => {
  if (Item.id('issue36:invalid_codec') !== null) throw new Error('Invalid codec item was registered');
  console.info('ISSUE36_INVALID_ITEM_REJECTED');
  event.getServer().halt(false);
});