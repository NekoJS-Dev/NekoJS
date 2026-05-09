package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.world.item.ItemStack;

public class ItemJS {
    public ItemStack of(ItemStack stack) {
        return stack;
    }

    public ItemStack of(ItemStack stack, int count) {
        return withCount(stack, count);
    }

    public ItemStack empty() {
        return ItemStack.EMPTY;
    }

    private ItemStack withCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
