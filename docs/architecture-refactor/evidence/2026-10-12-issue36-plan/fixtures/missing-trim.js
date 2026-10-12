RegistryEvents.register(event => {
  event.item('issue36:missing_trim', build => {
    build.trimMaterial = 'issue36:missing_trim';
  });
});