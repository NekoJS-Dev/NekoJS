package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NekoJS.MODID, value = Dist.CLIENT)
public class ClientEventListener {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }

        ItemEvents.TOOLTIP.post(event);
    }
}