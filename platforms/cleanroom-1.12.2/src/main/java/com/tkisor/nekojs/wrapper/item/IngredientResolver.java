package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 Ingredient 解析：支持 item:id / ore:name / @mod 前缀以及对象形式。
 * 简化版，不含 NeoForge 1.21.1 的 Holder/HolderSet 系统。
 */
public final class IngredientResolver {
    private IngredientResolver() {}

    public static Ingredient fromString(String raw) {
        String s = normalizeRaw(raw);
        if (s.startsWith("ore:")) {
            return new OreIngredient(s.substring(4));
        }
        char c = s.charAt(0);
        if (c == '*') {
            // Wildcard - match all items (use OreDictionary wildcard)
            return Ingredient.EMPTY; // TODO: implement wildcard matching
        }
        if (c == '@') {
            // @mod namespace - all items from a mod
            String namespace = s.substring(1);
            List<ItemStack> stacks = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = item.getRegistryName();
                if (id != null && id.getNamespace().equals(namespace)) {
                    stacks.add(new ItemStack(item));
                }
            }
            return stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.fromStacks(stacks.toArray(new ItemStack[0]));
        }

        return fromItemOrOreId(s);
    }

    private static Ingredient fromItemOrOreId(String s) {
        ResourceLocation location;
        try {
            location = new ResourceLocation(s);
        } catch (Exception e) {
            throw new ValueConversionException(Ingredient.class, "item id or ore:name", s,
                    "invalid identifier: " + s);
        }

        // Try as item first
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item != null) {
            return Ingredient.fromItem(item);
        }

        // Fallback to OreDictionary
        if (OreDictionary.doesOreNameExist(s)) {
            return new OreIngredient(s);
        }

        throw new ValueConversionException(Ingredient.class, "registered item or ore name", s,
                "item or ore not found: " + s);
    }

    public static Ingredient fromNekoId(NekoId id) {
        return fromString(id.toString());
    }

    public static Ingredient fromItem(Item item) {
        return Ingredient.fromItem(item);
    }

    public static Ingredient fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Ingredient.EMPTY;
        return Ingredient.fromStacks(stack);
    }

    public static Ingredient fromValue(Value value) {
        if (value == null || value.isNull()) return Ingredient.EMPTY;
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof Ingredient ingredient) return ingredient;
            if (obj instanceof ItemStack stack) return fromStack(stack);
            if (obj instanceof Item item) return fromItem(item);
            if (obj instanceof NekoId id) return fromNekoId(id);
            if (obj == null) return Ingredient.EMPTY;
        }
        if (value.hasArrayElements()) return compound(value);
        if (value.hasMembers()) return fromObject(value);
        throw new ValueConversionException(Ingredient.class,
                "item / item id / ingredient object", value, "unsupported ingredient value");
    }

    private static Ingredient fromObject(Value value) {
        if (value.hasMember("item")) return fromValue(value.getMember("item"));
        if (value.hasMember("ore")) {
            String oreName = value.getMember("ore").asString();
            return OreDictionary.doesOreNameExist(oreName)
                    ? new OreIngredient(oreName)
                    : Ingredient.EMPTY;
        }
        if (value.hasMember("mod")) {
            return fromString("@" + value.getMember("mod").asString());
        }
        throw new ValueConversionException(Ingredient.class,
                "item/ore/mod field", value, "no recognized field in ingredient object");
    }

    private static Ingredient compound(Value value) {
        if (!value.hasArrayElements()) return Ingredient.EMPTY;
        List<ItemStack> stacks = new ArrayList<>();
        for (long i = 0; i < value.getArraySize(); i++) {
            Ingredient ing = fromValue(value.getArrayElement(i));
            for (ItemStack stack : ing.getMatchingStacks()) {
                stacks.add(stack);
            }
        }
        return stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.fromStacks(stacks.toArray(new ItemStack[0]));
    }

    public static List<Ingredient> combine(List<Ingredient> alternatives) {
        return alternatives.stream().filter(i -> i != null && i != Ingredient.EMPTY).toList();
    }

    public static String normalizeItemId(String raw) {
        String id = normalizeRaw(raw);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static String normalizeRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValueConversionException(Ingredient.class, "non-blank string", raw,
                    "ingredient id cannot be empty");
        }
        return raw.trim();
    }
}
