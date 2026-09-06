// 26.x 实现。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，改本文件行为时须同步它。
// 加载器分叉共三处（enchantById/hasEnchantment 的动态附魔注册表服务端访问、
// componentIngredient 的组件匹配成分），均为行内 loader 守卫——对侧编译单元整段
// 消失（guardLint 规则 7 豁免），无整文件守卫。
package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.ItemStackSpec;
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
        // 26.x 移除 ItemStack#getEnchantmentLevel —— 经 ItemEnchantments 组件查等级
        return self().getEnchantments().getLevel(enchantment) >= level;
    }

    default Ingredient neko$asIngredient() {
        if (self().isEmpty()) return Ingredient.of();
        if (self().getComponentsPatch().isEmpty()) {
            // builtInRegistryHolder 已废弃：从注册表 wrap 等价 holder（26.x 的 ItemStack 无 getItemHolder）
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
        /* fabric 无 strict 区分：DefaultCustomIngredients.components 按整栈组件精确匹配 */
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
