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
        NekoJS.COMMON.scriptManagers.at(ScriptType.SERVER).flushReadyNodeTimers();
        var testSm = NekoJS.COMMON.scriptManagers.at(ScriptType.TEST);
        if (testSm != null) {
            testSm.flushReadyNodeTimers();
        }
        PDataSyncService.flush(event.getServer());
    }
}
