package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.entity.GoalRegisterEventJS;

/** AI goal 注册事件组（startup 脚本）：为实体类型注册内置 goal（见 {@link GoalRegisterEventJS}）。 */
public interface GoalEvents {
    EventGroup GROUP = EventGroup.of("GoalEvents");

    EventBusJS<GoalRegisterEventJS, Void> REGISTER = GROUP.startup("register", GoalRegisterEventJS.class);

    static void postRegister() {
        REGISTER.post(new GoalRegisterEventJS());
    }
}
