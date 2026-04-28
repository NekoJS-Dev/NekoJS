package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.script.ScriptType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    @SubscribeEvent
    public static void onServerResourceReload(AddReloadListenerEvent event) {
        try {
            NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.SERVER);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Script overload failed: ", e);
        }
    }
}
