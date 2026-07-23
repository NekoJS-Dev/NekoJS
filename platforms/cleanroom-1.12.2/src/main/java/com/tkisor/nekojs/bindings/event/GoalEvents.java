package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.entity.GoalRegisterEventJS;

/**
 * 1.12.2 GoalEvents - entity AI goal registration.
 *
 * <p>{@link #postRegister()} is invoked from {@code NekoJSMod.initializeScripts()} after
 * startup scripts load, giving scripts the chance to attach goals to entity ids. The
 * collected goals are later applied by {@code GoalRegistry} either from
 * {@code NekoScriptMob.initEntityAI()} or on {@code EntityJoinWorldEvent} for vanilla mobs.
 */
public interface GoalEvents {
    EventGroup GROUP = EventGroup.of("GoalEvents");

    EventBusJS<GoalRegisterEventJS, Void> REGISTER =
            GROUP.startup("register", GoalRegisterEventJS.class);

    static void postRegister() {
        REGISTER.post(new GoalRegisterEventJS());
    }
}
