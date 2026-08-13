package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS;

/** 能力（Capability）注册事件组（startup 脚本，mod bus 的 RegisterCapabilitiesEvent 时触发）。 */
public interface CapabilityEvents {
    EventGroup GROUP = EventGroup.of("CapabilityEvents");

    /** 注册 capability provider（见 {@link CapabilityRegistryEventJS}）。 */
    EventBusJS<CapabilityRegistryEventJS, Void> REGISTER =
            GROUP.startup("register", CapabilityRegistryEventJS.class);
}
