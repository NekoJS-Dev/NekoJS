// Place in server_scripts. The callback runs for future logins, not existing players.
// NeoForge default. On Fabric, replace the entity property below with player.
PlayerEvents.loggedIn(event => {
    event.entity.sendMessage("Welcome to NekoJS!");
});
