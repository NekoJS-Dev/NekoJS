package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;

public interface RegistryEvents {
    EventGroup GROUP = EventGroup.of("RegistryEvents");

    EventBusJS<ItemRegistryEventJS, Void> ITEM =
            GROUP.startup("item", ItemRegistryEventJS.class);

    EventBusJS<BlockRegistryEventJS, Void> BLOCK = GROUP.startup("block", BlockRegistryEventJS.class);

    EventBusJS<EntityTypeRegistryEventJS, Void> ENTITY_TYPE = GROUP.startup("entityType", EntityTypeRegistryEventJS.class);
}