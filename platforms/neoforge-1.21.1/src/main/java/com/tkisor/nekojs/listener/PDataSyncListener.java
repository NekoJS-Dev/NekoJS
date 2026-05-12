package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public final class PDataSyncListener {
    private PDataSyncListener() {}

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        NekoJS.SCRIPT_MANAGER.flushReadyNodeTimers(ScriptType.SERVER);
        NekoJS.SCRIPT_MANAGER.flushReadyNodeTimers(ScriptType.TEST);
        PDataSyncService.flush(event.getServer());
    }
}
