package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;

public interface RegistryEvents {
    EventGroup GROUP = EventGroup.of("RegistryEvents");

    EventHandler<ItemRegistryEventJS> ITEM =
            GROUP.startup("item", () -> ItemRegistryEventJS.class);

    EventHandler<BlockRegistryEventJS> BLOCK = GROUP.startup("block", () -> BlockRegistryEventJS.class);
}