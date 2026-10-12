RegistryEvents.register(event => {
  event.item('issue36:missing_song', build => {
    build.jukeboxPlayable = 'issue36:missing_song';
  });
});