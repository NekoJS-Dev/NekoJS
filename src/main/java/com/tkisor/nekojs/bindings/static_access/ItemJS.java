package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class ItemJS {

    /**
     * 核心工厂方法
     * @param id 物品 ID (如 "minecraft:stone")
     * @param count 数量
     * @return ItemStack
     */
    public ItemStack of(String id, int count) {
        Identifier location = Identifier.parse(id);

        Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(location);

        if ((item.isEmpty()) && !id.equals("minecraft:air")) {
            throw new IllegalArgumentException("Invalid item id '" + id + "'");
        }

        return new ItemStack(item.get(), count);
    }

    /**
     * 简易重载：默认数量为 1
     */
    public ItemStack of(String id) {
        return of(id, 1);
    }
}