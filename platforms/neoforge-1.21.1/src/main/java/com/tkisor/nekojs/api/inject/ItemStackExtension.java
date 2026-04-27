package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
// 1.21.1 引入 Unbreakable 组件类
import net.minecraft.world.item.component.Unbreakable;

import java.util.List;

/**
 * @see ItemStack
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface ItemStackExtension {

    private ItemStack self() {
        return (ItemStack) (Object) this;
    }

    default ItemLore neko$getLore() {
        return self().get(DataComponents.LORE);
    }

    default void neko$setLore(ItemLore lore) {
        self().set(DataComponents.LORE, lore);
    }

    default void neko$setLore(List<Component> lines) {
        // 1.21.1 中 ItemLore 的构造函数正确，直接传入 List 即可
        self().set(DataComponents.LORE, new ItemLore(lines));
    }

    default boolean neko$isEnchanted() {
        Boolean glint = self().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return glint != null ? glint : self().isEnchanted();
    }

    default void neko$setEnchanted(boolean enchanted) {
        if (enchanted) {
            self().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            self().remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    default boolean neko$isUnbreakable() {
        return self().has(DataComponents.UNBREAKABLE);
    }

    default void neko$setUnbreakable(boolean unbreakable) {
        if (unbreakable) {
            // 1.21.1: UNBREAKABLE 组件要求传入 Unbreakable 对象，true 表示在 tooltip 中显示 "不可破坏" 标签
            self().set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        } else {
            self().remove(DataComponents.UNBREAKABLE);
        }
    }

    default String neko$getId() {
        // 1.21.1: 改用注册表获取标准 ID，避免 toString() 带来不确定的结果
        return BuiltInRegistries.ITEM.getKey(self().getItem()).toString();
    }
}