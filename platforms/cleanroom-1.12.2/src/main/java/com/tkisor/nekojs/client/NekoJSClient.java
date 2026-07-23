package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class NekoJSClient {

    private static final AtomicBoolean SCRIPTS_LOADED = new AtomicBoolean(false);

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
    }

    public static class ClientEventHandler {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (NekoJSMod.RUNTIME_ROOT == null) return;

            // Load client scripts on first tick (after Minecraft is fully initialized)
            if (SCRIPTS_LOADED.compareAndSet(false, true)) {
                NekoJS.LOGGER.info("[client] 正在加载 CLIENT 脚本...");
                try {
                    NekoJSMod.RUNTIME_ROOT.scriptManagerOf(ScriptType.CLIENT).loadScripts();
                } catch (Throwable e) {
                    NekoJS.LOGGER.error("[client] CLIENT 脚本加载失败", e);
                }
            }

            NekoJSMod.RUNTIME_ROOT.scriptManagerOf(ScriptType.CLIENT).flushReadyNodeTimers();
        }
    }
}
