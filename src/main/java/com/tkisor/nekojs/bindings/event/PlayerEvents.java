package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.player.PlayerChatEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLoggedInEventJS;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerLoggedInEventJS, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerLoggedInEventJS.class);
    EventBusJS<PlayerChatEventJS, Void> CHAT =
            GROUP.server("chat", PlayerChatEventJS.class);
}