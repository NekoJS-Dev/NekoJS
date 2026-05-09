package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

@RemapByPrefix("neko$")
public interface ItemExtension {
    private Item self() {
        return (Item) this;
    }

    default String neko$getId() {
        return BuiltInRegistries.ITEM.getKey(self()).toString();
    }
}
