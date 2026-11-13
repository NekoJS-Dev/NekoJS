// 26.x implementation. This file must not carry version guards again. The 1.21.1 implementation
// is the file of the same name under versions/1.21.1/src; keep it in sync when you change this file.
//? if neoforge {
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    // Client-side errors may not show up immediately.
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Reads the error count through the ScriptErrorReporter facade, the static reporting face of the
            // root-owned ErrorTracker that the shared assembly function installs; the code does not touch the
            // static root directly.
            int errorCount = ScriptErrorReporter.errorCount();
            if (Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && errorCount > 0) {

                player.sendSystemMessage(NekoErrorUIHelper.getErrorComponent(errorCount), false);
            }
            // Attaches the inventory listener that fires the inventoryChanged event.
            InventoryChangeListener.getOrCreate(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Re-attaches the inventory listener after a clone (dimension change or death respawn), because
        // the new player entity has a new inventoryMenu.
        InventoryChangeListener.getOrCreate(event.getEntity());
    }
}
//?}
