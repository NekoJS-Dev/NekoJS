package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class ServerEventListener {
    @SubscribeEvent
    public static void onServerPostTick(ServerTickEvent.Post event) {
        ServerTickEventJS eventJS = new ServerTickEventJS(event, "Post");
        ServerEvents.TICK_POST.post(eventJS);
    }

    @SubscribeEvent
    public static void onServerPreTick(ServerTickEvent.Pre event) {
        ServerTickEventJS eventJS = new ServerTickEventJS(event, "Pre");
        ServerEvents.TICK_PRE.post(eventJS);
    }
}
