package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.wrapper.event.player.PlayerChatEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLoggedInEventJS;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventHandler<PlayerLoggedInEventJS> LOGGED_IN =
            GROUP.server("loggedIn", () -> PlayerLoggedInEventJS.class);
    EventHandler<PlayerChatEventJS> CHAT =
            GROUP.server("chat", () -> PlayerChatEventJS.class);
}