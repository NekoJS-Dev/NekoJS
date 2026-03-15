package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.wrapper.event.player.PlayerLoggedInEventJS;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventHandler<PlayerLoggedInEventJS> LOGGED_IN =
            GROUP.server("loggedIn", () -> PlayerLoggedInEventJS.class);
}