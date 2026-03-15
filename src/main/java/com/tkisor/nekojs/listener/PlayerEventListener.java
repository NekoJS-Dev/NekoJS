package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLoggedInEventJS;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerLoggedInEventJS eventJS = new PlayerLoggedInEventJS(event);
         PlayerEvents.LOGGED_IN.post(eventJS);
    }

    @SubscribeEvent
    public static void onItemRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemRightClickEventJS eventJS = new ItemRightClickEventJS(event);
        ItemEvents.RIGHT_CLICKED.post(eventJS.getItem().getId(), eventJS);
    }

}