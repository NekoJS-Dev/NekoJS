package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

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
            self().set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        } else {
            self().remove(DataComponents.UNBREAKABLE);
        }
    }
}