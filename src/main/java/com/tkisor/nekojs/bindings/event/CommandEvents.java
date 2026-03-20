package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.command.CommandRegisterEventJS;

public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    EventBusJS<CommandRegisterEventJS, Void> REGISTER =
            GROUP.server("register", CommandRegisterEventJS.class);
}