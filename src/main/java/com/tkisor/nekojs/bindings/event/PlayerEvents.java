package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerEvent.PlayerLoggedInEvent, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerEvent.PlayerLoggedInEvent.class);
    EventBusJS<ServerChatEvent, Void> CHAT =
            GROUP.server("chat", ServerChatEvent.class);
    EventBusJS<PlayerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", PlayerTickEvent.Post.class);
    EventBusJS<PlayerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", PlayerTickEvent.Pre.class);
    EventBusJS<PlayerEvent.PlayerRespawnEvent, Void> RESPAWNED =
            GROUP.server("respawned", PlayerEvent.PlayerRespawnEvent.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOGGED_IN)
            .bind(CHAT)
            .bind(TICK_POST)
            .bind(TICK_PRE)
            .bind(RESPAWNED);
}