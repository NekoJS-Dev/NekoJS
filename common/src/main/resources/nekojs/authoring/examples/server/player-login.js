// Place in server_scripts. The callback runs for future logins, not existing players.
PlayerEvents.loggedIn(event => {
    event.player.sendMessage("Welcome to NekoJS!");
});
