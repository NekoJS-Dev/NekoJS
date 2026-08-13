package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.ItemSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

@RemapByPrefix("neko$")
public interface ItemExtension extends ItemSpec {
    private Item self() {
        return (Item) this;
    }

    @Override
    default String neko$getId() {
        return BuiltInRegistries.ITEM.getKey(self()).toString();
    }
}
