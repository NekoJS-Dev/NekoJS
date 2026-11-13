package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.entity.GoalRegisterEventJS;

/**
 * AI goal registration event group (startup scripts): registers built-in goals for entity types
 * (see {@link GoalRegisterEventJS}).
 */
public interface GoalEvents {
    EventGroup GROUP = EventGroup.of("GoalEvents");

    EventBusJS<GoalRegisterEventJS, Void> REGISTER = GROUP.startup("register", GoalRegisterEventJS.class);

    static void postRegister() {
        REGISTER.post(new GoalRegisterEventJS());
    }
}

