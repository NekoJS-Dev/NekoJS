// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS;

/**
 * NeoForge capability registration events. The startup listener runs once from the
 * mod-bus {@code RegisterCapabilitiesEvent}; it is not a reload-time mutation point.
 */
public interface CapabilityEvents {
    EventGroup GROUP = EventGroup.of("CapabilityEvents");

    /** Collects providers which are committed by the native registration callback. */
    EventBusJS<CapabilityRegistryEventJS, Void> REGISTER =
            GROUP.startup("register", CapabilityRegistryEventJS.class);
}
//?}
