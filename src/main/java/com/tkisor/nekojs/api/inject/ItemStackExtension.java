// 26.x implementation. The 1.21.1 implementation is the file of the same name under
// versions/1.21.1/src; keep it in sync when changing behavior here.
// There are three loader forks (the server-side access to the dynamic enchantment registry in
// enchantById/hasEnchantment, and the component-matching ingredient in componentIngredient). All of
// them are inline loader guards: the other side's compilation unit disappears entirely (guardLint
// rule 7 exempts it), so there is no whole-file guard.
package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.ItemStackSpec;
import com.tkisor.nekojs.wrapper.item.PersistentDataJS;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
//? if neoforge {
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
//?}
//? if fabric {
/*import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;*/
//?}
import java.util.List;

/**
 * @see ItemStack
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface ItemStackExtension extends ItemStackSpec {

    private ItemStack self() {
        return (ItemStack) (Object) this;
    }

    /** Returns the ItemStack-owned persistent data container; it is local and is not network-synchronized. */
    default PersistentDataJS neko$pdata() {
        return new PersistentDataJS(self());
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

    @Override
    default Object neko$withCount(int count) {
        if (count <= 0 || self().isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = self().copy();
        copy.setCount(count);
        return copy;
    }

    @Remap("setCountAndReturn")
    default ItemStack neko$setCountAndReturn(int count) {
        self().setCount(count);
        return self();
    }

    default Block neko$getBlock() {
        return self().getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    default String neko$getMod() {
        return BuiltInRegistries.ITEM.getKey(self().getItem()).getNamespace();
    }

    @Remap("hasGlint")
    default boolean neko$hasGlint() {
        Boolean glint = self().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return glint != null ? glint : self().isEnchanted();
    }

    @Remap("setGlint")
    default void neko$setGlint(boolean enchanted) {
        if (enchanted) {
            self().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            self().remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    @Remap("enchantById")
    default ItemStack neko$enchantById(String id, int level) {
        Identifier parsedId = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsedId == null) {
            throw new IllegalArgumentException("Invalid enchantment id: " + id);
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsedId);
        //? if neoforge {
        Holder.Reference<Enchantment> enchantment = ServerLifecycleHooks.getCurrentServer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
        //?}
        //? if fabric {
        /*Holder.Reference<Enchantment> enchantment = FabricServerEventBindings.currentServer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);*/
        //?}
        return neko$enchant(enchantment, level);
    }

    @HideFromJS
    default ItemStack neko$enchant(Holder<Enchantment> enchantment, int level) {
        if (level <= 0) {
            throw new IllegalArgumentException("Enchantment level must be positive: " + level);
        }
        self().enchant(enchantment, level);
        return self();
    }

    @Override
    default boolean neko$hasEnchantment(String id, int level) {
        Identifier parsedId = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsedId == null) return false;
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsedId);
        //? if neoforge {
        return ServerLifecycleHooks.getCurrentServer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(key)
                .map(enchantment -> neko$hasEnchantment(enchantment, level))
                .orElse(false);
        //?}
        //? if fabric {
        /*return FabricServerEventBindings.currentServer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(key)
                .map(enchantment -> neko$hasEnchantment(enchantment, level))
                .orElse(false);*/
        //?}
    }

    default boolean neko$hasEnchantment(Holder<Enchantment> enchantment, int level) {
        // 26.x removed ItemStack#getEnchantmentLevel; the level is read from the ItemEnchantments component
        return self().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) >= level;
    }

    default Ingredient neko$asIngredient() {
        if (self().isEmpty()) return Ingredient.of();
        if (self().getComponentsPatch().isEmpty()) {
            // builtInRegistryHolder is deprecated: wrap an equivalent holder from the registry (the 26.x
            // ItemStack has no getItemHolder)
            return Ingredient.of(HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(self().getItem())));
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
        //? if neoforge {
        DataComponentMap.Builder components = DataComponentMap.builder();
        for (var entry : self().getComponentsPatch().entrySet()) {
            entry.getValue().ifPresent(value -> setComponent(components, entry.getKey(), value));
        }
        return DataComponentIngredient.of(strict, components.build(), HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(self().getItem())));
        //?}
        /* Fabric has no strict distinction: DefaultCustomIngredients.components matches the whole stack's components exactly */
        //? if fabric {
        /*return DefaultCustomIngredients.components(self());*/
        //?}
    }

    //? if neoforge {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(DataComponentMap.Builder components, DataComponentType type, Object value) {
        components.set(type, value);
    }
    //?}

    @Override
    default boolean neko$matches(Object other) {
        if (other instanceof ItemStack stack) return neko$matches(stack);
        if (other instanceof ItemLike item) return neko$matches(item);
        if (other instanceof Ingredient ingredient) return neko$matches(ingredient);
        return false;
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

    @Override
    default boolean neko$isUnbreakable() {
        return self().has(DataComponents.UNBREAKABLE);
    }

    @Override
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
