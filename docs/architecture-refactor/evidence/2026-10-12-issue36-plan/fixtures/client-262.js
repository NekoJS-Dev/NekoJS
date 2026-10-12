// Native client smoke uses local player tags to keep phases across resource reload.
const Minecraft = Java.type('net.minecraft.client.Minecraft');
const Screenshot = Java.type('net.minecraft.client.Screenshot');
const InventoryScreen = Java.type('net.minecraft.client.gui.screens.inventory.InventoryScreen');
const ClientCompat = Java.type('com.tkisor.nekojs.platform.compat.McClientCompat');
let ticks = 0;
let opened = false; let menuTicks = 0;
ClientEvents.tickPost(event => {
  const minecraft = Minecraft.getInstance();
  if (minecraft.player === null) {
    const screen = ClientCompat.get().currentScreen();
    menuTicks++;
    if (menuTicks === 80) { console.info('ISSUE36_MENU: ' + screen); Screenshot.grab(minecraft, false); }
    if (screen !== null) {
      const children = screen.children();
      for (let i = 0; i < children.size(); i++) {
        const widget = children.get(i);
        if (menuTicks === 80 && widget.message !== undefined) console.info('ISSUE36_MENU_BUTTON: ' + widget.message.string);
        if (widget.message !== undefined && (widget.message.string === 'Create Backup and Load' || widget.message.string === 'Proceed to main menu')) widget.onPress(null);
      }
    }
    return;
  }
  ticks++;

  function shot(phase) {
    console.info('ISSUE36_SCREENSHOT_REQUESTED_' + phase);
    Screenshot.grab(minecraft, false);
  }
  if (!opened) {
    opened = true;
    ClientCompat.get().showScreen(new InventoryScreen(minecraft.player));
    console.info('ISSUE36_CLIENT_INVENTORY_READY');
  }
  if (!minecraft.player.hasTag('issue36.client.reloaded') && ticks === 60) {
    shot('FIRST_INVENTORY');
  }
  if (!minecraft.player.hasTag('issue36.client.reloaded') && ticks === 120) {
    ClientCompat.get().showScreen(null); minecraft.player.xRot = 40; minecraft.player.yRot = 0;
  }
  if (!minecraft.player.hasTag('issue36.client.reloaded') && ticks === 180) shot('FIRST_HAND');
  if (!minecraft.player.hasTag('issue36.client.reloaded') && ticks === 240) {
    minecraft.player.addTag('issue36.client.reloaded');
    console.info('ISSUE36_RESOURCE_RELOAD_REQUESTED');
    minecraft.reloadResourcePacks();
  }
  if (minecraft.player.hasTag('issue36.client.reloaded') && !minecraft.player.hasTag('issue36.client.complete') && ticks === 60) {
    shot('AFTER_RELOAD'); minecraft.player.addTag('issue36.client.complete');
  }
  if (minecraft.player.hasTag('issue36.client.complete') && ticks === 100) minecraft.stop();
});
