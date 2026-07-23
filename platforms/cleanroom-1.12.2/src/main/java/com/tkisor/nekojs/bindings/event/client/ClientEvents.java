package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public interface ClientEvents {
    EventGroup GROUP = EventGroup.of("ClientEvents");

    EventBusJS<TickEvent.ClientTickEvent, Void> TICK =
            GROUP.client("tick", TickEvent.ClientTickEvent.class);
    EventBusJS<ClientChatReceivedEvent, Void> CHAT_RECEIVED =
            GROUP.client("chatReceived", ClientChatReceivedEvent.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(TICK)
            .bind(CHAT_RECEIVED);
}
