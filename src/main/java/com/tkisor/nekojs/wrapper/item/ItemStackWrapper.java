package com.tkisor.nekojs.wrapper.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 暴露给 JS 的物品堆包装器
 */
public class ItemStackWrapper {
    private final ItemStack rawStack;

    public ItemStackWrapper(ItemStack rawStack) {
        this.rawStack = rawStack;
    }

    public String getId() {
        return BuiltInRegistries.ITEM.getKey(rawStack.getItem()).toString();
    }

    public int getCount() {
        return rawStack.getCount();
    }

    public void setCount(int count) {
        rawStack.setCount(count);
    }

    public boolean isEmpty() {
        return rawStack.isEmpty();
    }

    public String getName() {
        return rawStack.getHoverName().getString();
    }

    public void setName(String name) {
        rawStack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    }

    /**
     * 获取 Lore (描述)
     * JS 用法: let lore = item.lore;
     */
    public List<String> getLore() {
        ItemLore loreComponent = rawStack.get(net.minecraft.core.component.DataComponents.LORE);
        if (loreComponent == null) return List.of();
        return loreComponent.lines().stream()
                .map(Component::getString)
                .collect(Collectors.toList());
    }

    public void setLore(List<String> lines) {
        List<Component> components = lines.stream()
                .map(Component::literal)
                .collect(Collectors.toList());
        rawStack.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(components));
    }

    public int getDamage() {
        return rawStack.getDamageValue();
    }

    public void setDamage(int damage) {
        rawStack.setDamageValue(damage);
    }

    public int getMaxDamage() {
        return rawStack.getMaxDamage();
    }

    public ItemStack getRaw() {
        return rawStack;
    }
}