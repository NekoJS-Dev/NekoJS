package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    // 客户端error可能不会立即显示
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasPermissions(2) && NekoJSMod.RUNTIME_ROOT.errors().count() > 0) {

                player.displayClientMessage(NekoErrorUIHelper.getErrorComponent(), false);
            }
        }
    }
}