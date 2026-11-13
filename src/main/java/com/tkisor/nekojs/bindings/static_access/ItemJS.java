package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.js.type_adapter.ParseIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item factory and helpers for scripts, bound as both the {@code Item} and {@code ItemJS} globals.
 *
 * <p>Matches KubeJS semantics: {@code Item.of('minecraft:stone')} returns an {@link ItemStack}.
 * It is a wrapper object instead of static methods injected into the MC {@code Item} class,
 * because SpongePowered Mixin forbids injecting public static methods and the ASM copy approach
 * in IMixinConfigPlugin triggers a {@code SymbolTable} NPE during bootstrap
 * (see memory: mixin-static-inject-bootstrap-npe).
 */
public class ItemJS {

    /** Creates an item stack from a string id, KubeJS style; supports the {@code "1xminecraft:stone"} count syntax. */
    public ItemStack of(String id) {
        return ItemStackAdapter.stringToItemStack(id);
    }

    /** Creates an item stack from a string id and a count. */
    public ItemStack of(String id, int count) {
        return ItemStackAdapter.withCount(ItemStackAdapter.stringToItemStack(id), count);
    }

    /** Takes an existing item stack and returns it unchanged, so scripts can always call the {@code of} entry points. */
    public ItemStack of(ItemStack stack) {
        return stack;
    }

    /** Returns the given item stack with the given count. */
    public ItemStack of(ItemStack stack, int count) {
        return ItemStackAdapter.withCount(stack, count);
    }

    /** Returns the empty item stack. */
    public ItemStack empty() {
        return ItemStack.EMPTY;
    }

    /**
     * Looks up an item by string id and returns {@code null} when it does not exist. A missing
     * {@code minecraft:} prefix is added.
     */
    public Item id(String id) {
        Identifier location = ParseIds.parseItemOrBlockId(id);
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    /** Returns the registry id of an item, in {@code minecraft:stone} form. */
    public Identifier idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
