package com.tkisor.nekojs.wrapper.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class PersistentDataJS extends com.tkisor.nekojs.wrapper.pdata.PersistentDataJS {
    public PersistentDataJS(ItemStack rawStack) {
        super(
                () -> rawStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(),
                tag -> saveTag(rawStack, tag)
        );
    }

    private static void saveTag(ItemStack rawStack, CompoundTag tag) {
        if (tag.isEmpty()) {
            rawStack.remove(DataComponents.CUSTOM_DATA);
        } else {
            rawStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
