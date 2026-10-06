//? if neoforge {
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public final class PDataSyncListener {
    /** loader entry 装配完成后注入的窄生命周期 handle（bind）；server tick 触发时必已就绪。 */
    private static volatile NekoRuntimeRoot runtimeRoot;

    private PDataSyncListener() {}

    /** 由 {@code NekoJSMod} 在 root 装配后调用；早于任何 server tick。 */
    public static void bind(NekoRuntimeRoot root) {
        runtimeRoot = root;
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        runtimeRoot.scriptManagerOf(ScriptType.SERVER).flushReadyNodeTimers();
        var testSm = runtimeRoot.scriptManagerOrNull(ScriptType.TEST);
        if (testSm != null) {
            testSm.flushReadyNodeTimers();
        }
        PDataSyncService.flush(event.getServer());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PDataSyncService.syncTo(event.getTarget(), player);
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PDataSyncService.clearFor(event.getTarget(), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PDataSyncService.markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        PDataSyncService.markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PDataSyncService.markDirty(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PDataSyncService.copyPlayerData(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PDataSyncService.resetServerState();
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        PDataSyncService.onEntityRemoved(event.getEntity());
    }
}
//?}
