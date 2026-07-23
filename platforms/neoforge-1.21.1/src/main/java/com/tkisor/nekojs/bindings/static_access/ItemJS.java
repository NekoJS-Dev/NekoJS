package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import net.minecraft.world.item.ItemStack;

public class ItemJS {
    public ItemStack of(String id) {
        return ItemStackAdapter.stringToItemStack(id);
    }
    public ItemStack of(String id, int count) {
        return ItemStackAdapter.withCount(ItemStackAdapter.stringToItemStack(id), count);
    }
    public ItemStack of(ItemStack stack) {
        return stack;
    }
    public ItemStack of(ItemStack stack, int count) {
        return ItemStackAdapter.withCount(stack, count);
    }
    public ItemStack empty() {
        return ItemStack.EMPTY;
    }
}
