package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS;

/** Startup capability subscriptions collected after Fabric's object registries have drained. */
public interface CapabilityEvents {
    EventGroup GROUP = EventGroup.of("CapabilityEvents");
    EventBusJS<CapabilityRegistryEventJS, Void> REGISTER =
            GROUP.startup("register", CapabilityRegistryEventJS.class);

    static void postAndApply() {
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        REGISTER.postForCollection(event);
        event.apply();
    }
}
