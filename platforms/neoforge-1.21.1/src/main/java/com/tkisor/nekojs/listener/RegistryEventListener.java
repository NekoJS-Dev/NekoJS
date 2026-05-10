package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.RegistryEvents;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class RegistryEventListener {
    private RegistryEventListener() {}

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            BlockRegistryEventJS eventJS = new BlockRegistryEventJS(event);
            RegistryEvents.BLOCK.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            ItemRegistryEventJS eventJS = new ItemRegistryEventJS(event);
            RegistryEvents.ITEM.post(eventJS);
            eventJS.registerAll();

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.forEach((location, block) -> {
                event.register(Registries.ITEM, location, () -> {
                    Item.Properties props = new Item.Properties();
                    return new BlockItem(block, props);
                });
            });

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.clear();
        }
    }
}
