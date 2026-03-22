package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.player.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerLoggedInEventJS, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerLoggedInEventJS.class);
    EventBusJS<PlayerChatEventJS, Void> CHAT =
            GROUP.server("chat", PlayerChatEventJS.class);
    EventBusJS<PlayerTickPostEventJS, Void> TICK_POST =
            GROUP.server("tickPost", PlayerTickPostEventJS.class);
    EventBusJS<PlayerTickPreEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", PlayerTickPreEventJS.class);
    EventBusJS<PlayerRespawnEventJS, Void> RESPAWNED =
            GROUP.server("respawned", PlayerRespawnEventJS.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(CHAT, PlayerChatEventJS::new, ServerChatEvent.class);
}