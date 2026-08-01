package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.js.type_adapter.ParseIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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

    /** 按字符串 id 查物品；不存在返回 null。id 缺省 {@code minecraft:} 前缀。 */
    public Item id(String id) {
        ResourceLocation location = ParseIds.parseItemOrBlockId(id);
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    /** 取物品的注册 id（{@code minecraft:stone} 形式）。 */
    public ResourceLocation idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
