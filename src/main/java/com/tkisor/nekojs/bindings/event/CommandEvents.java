package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.wrapper.event.command.CommandRegisterEventJS;

public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    EventHandler<CommandRegisterEventJS> REGISTER =
            GROUP.server("register", () -> CommandRegisterEventJS.class);
}