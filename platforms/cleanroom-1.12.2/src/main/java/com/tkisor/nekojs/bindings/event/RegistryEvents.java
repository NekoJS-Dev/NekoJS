package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;

/**
 * 1.12.2 RegistryEvents.
 *
 * <p>Each bus carries a script-facing wrapper EventJS (e.g. {@link BlockRegistryEventJS})
 * rather than the raw Forge {@code RegistryEvent.Register}. These custom events are plain
 * Java objects, NOT Forge events, so they must NOT be routed through
 * {@code EventBusForgeBridge}; instead {@code RegistryEventListener} manually constructs
 * the wrapper, posts it to the bus, and then invokes {@code registerAll()} to flush the
 * builders into the Forge registry.
 */
public interface RegistryEvents {
    EventGroup GROUP = EventGroup.of("RegistryEvents");

    EventBusJS<BlockRegistryEventJS, Void> BLOCK =
            GROUP.startup("block", BlockRegistryEventJS.class);

    EventBusJS<ItemRegistryEventJS, Void> ITEM =
            GROUP.startup("item", ItemRegistryEventJS.class);

    EventBusJS<EntityTypeRegistryEventJS, Void> ENTITY_TYPE =
            GROUP.startup("entityType", EntityTypeRegistryEventJS.class);
}
