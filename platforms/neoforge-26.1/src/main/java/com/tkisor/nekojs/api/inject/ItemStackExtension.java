package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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

    default ItemStack neko$withCount(int count) {
        if (count <= 0 || self().isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = self().copy();
        copy.setCount(count);
        return copy;
    }

    default ItemStack neko$copy() {
        return self().copy();
    }

    default boolean neko$isEmpty() {
        return self().isEmpty();
    }

    default ItemStack neko$setCount(int count) {
        self().setCount(count);
        return self();
    }

    default Item neko$getItem() {
        return self().getItem();
    }

    default Block neko$getBlock() {
        return self().getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    default String neko$getMod() {
        return BuiltInRegistries.ITEM.getKey(self().getItem()).getNamespace();
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

    default ItemStack neko$enchant(String id, int level) {
        Identifier parsedId = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsedId == null) {
            throw new IllegalArgumentException("Invalid enchantment id: " + id);
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsedId);
        Holder.Reference<Enchantment> enchantment = ServerLifecycleHooks.getCurrentServer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
        return neko$enchant(enchantment, level);
    }

    default ItemStack neko$enchant(Holder<Enchantment> enchantment, int level) {
        if (level <= 0) {
            throw new IllegalArgumentException("Enchantment level must be positive: " + level);
        }
        self().enchant(enchantment, level);
        return self();
    }

    default boolean neko$hasEnchantment(String id, int level) {
        Identifier parsedId = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsedId == null) return false;
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsedId);
        return ServerLifecycleHooks.getCurrentServer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(key)
                .map(enchantment -> neko$hasEnchantment(enchantment, level))
                .orElse(false);
    }

    default boolean neko$hasEnchantment(Holder<Enchantment> enchantment, int level) {
        return self().getEnchantments().getLevel(enchantment) >= level;
    }

    default Ingredient neko$asIngredient() {
        if (self().isEmpty()) return Ingredient.of();
        if (self().getComponentsPatch().isEmpty()) {
            return Ingredient.of(HolderSet.direct(self().getItem().builtInRegistryHolder()));
        }
        return neko$weakNBT();
    }

    default Ingredient neko$weakNBT() {
        return componentIngredient(false);
    }

    default Ingredient neko$strictNBT() {
        return componentIngredient(true);
    }

    private Ingredient componentIngredient(boolean strict) {
        DataComponentMap.Builder components = DataComponentMap.builder();
        for (var entry : self().getComponentsPatch().entrySet()) {
            entry.getValue().ifPresent(value -> setComponent(components, entry.getKey(), value));
        }
        return DataComponentIngredient.of(strict, components.build(), HolderSet.direct(self().getItem().builtInRegistryHolder()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(DataComponentMap.Builder components, DataComponentType type, Object value) {
        components.set(type, value);
    }

    default boolean neko$matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(self(), stack);
    }

    default boolean neko$matches(ItemLike item) {
        return self().is(item.asItem());
    }

    default boolean neko$matches(Ingredient ingredient) {
        return ingredient.test(self());
    }

    default boolean neko$areItemsEqual(ItemStack stack) {
        return self().getItem() == stack.getItem();
    }

    default boolean neko$areComponentsEqual(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(self(), stack);
    }

    default boolean neko$equalsIgnoringCount(ItemStack stack) {
        if (self() == stack) return true;
        if (self().isEmpty()) return stack.isEmpty();
        return ItemStack.isSameItemSameComponents(self(), stack);
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

    default String neko$getId() {
        return BuiltInRegistries.ITEM.getKey(self().getItem()).toString();
    }
}