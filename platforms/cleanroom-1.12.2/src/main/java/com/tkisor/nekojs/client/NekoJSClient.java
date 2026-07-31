package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class NekoJSClient {

    private static final AtomicBoolean SCRIPTS_LOADED = new AtomicBoolean(false);

    /** 首 tick 是否已完成（CLIENT 脚本已加载）——mixin 据此决定是否触发生成管线。 */
    public static boolean isReady() {
        return SCRIPTS_LOADED.get();
    }

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
                // MinecraftMixin 在 init RETURN 时已把 nekojs/assets 注册为
                // FolderResourcePack；主动刷新一次让 pack 与生成的翻译在进入世界前生效。
                // 该 reload 会触发 LanguageManagerMixin → 生成管线（幂等）。
                try {
                    Minecraft.getMinecraft().refreshResources();
                } catch (Throwable e) {
                    NekoJS.LOGGER.error("[client] 初始资源刷新失败", e);
                }
            }

            NekoJSMod.RUNTIME_ROOT.scriptManagerOf(ScriptType.CLIENT).flushReadyNodeTimers();
        }
    }
}
