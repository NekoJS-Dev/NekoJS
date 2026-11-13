// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    // The client may not show an error right away.
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The error count comes through the ScriptErrorReporter facade, the static reporting side of the
            // root-owned ErrorTracker installed by the shared wiring, so nothing reads the static root directly.
            int errorCount = ScriptErrorReporter.errorCount();
            if (player.hasPermissions(2) && errorCount > 0) {

                player.displayClientMessage(NekoErrorUIHelper.getErrorComponent(errorCount), false);
            }
            // Attach the inventory listener that reports inventoryChanged.
            InventoryChangeListener.getOrCreate(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Re-attach after a clone (dimension change or death respawn): the new player entity has a new
        // inventoryMenu.
        InventoryChangeListener.getOrCreate(event.getEntity());
    }
}
