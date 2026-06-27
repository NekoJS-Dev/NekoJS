package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.inject.ItemStackExtension;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public final class IngredientResolver {
    private IngredientResolver() {}

    public static Ingredient fromString(String raw) {
        String id = normalizeIngredientId(raw);
        Identifier location = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid ingredient id: " + raw);
        }

        if (id.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, location);
            var tag = BuiltInRegistries.ITEM.get(tagKey);
            if (tag.isEmpty()) {
                throw new IllegalArgumentException("Item tag not found: " + id);
            }
            return Ingredient.of(tag.get());
        }

        Item item = BuiltInRegistries.ITEM.getOptional(location)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
        return Ingredient.of(item);
    }

    public static Ingredient fromNekoId(NekoId id) {
        return fromString(id.toString());
    }

    public static Ingredient fromItem(Item item) {
        return Ingredient.of(item);
    }

    public static Ingredient fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Ingredient.of();
        return ((ItemStackExtension) (Object) stack).neko$asIngredient();
    }

    public static Ingredient fromIngredient(Ingredient ingredient) {
        return ingredient;
    }

    public static Ingredient fromWrapper(IngredientJS wrapper) {
        return wrapper.unwrap();
    }

    public static Ingredient fromValue(Value value) {
        if (value == null || value.isNull()) {
            return Ingredient.of();
        }

        if (value.isString()) {
            return fromString(value.asString());
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof IngredientJS wrapper) return fromWrapper(wrapper);
            if (obj instanceof Ingredient ingredient) return fromIngredient(ingredient);
            if (obj instanceof ItemStack stack) return fromStack(stack);
            if (obj instanceof Item item) return fromItem(item);
            if (obj instanceof NekoId id) return fromNekoId(id);
            if (obj == null) return Ingredient.of();
        }

        if (value.hasArrayElements()) {
            List<Ingredient> ingredients = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                Ingredient ingredient = fromValue(value.getArrayElement(i));
                if (ingredient != null && !ingredient.isEmpty()) ingredients.add(ingredient);
            }
            return combine(ingredients);
        }

        if (value.hasMembers()) {
            if (value.hasMember("item")) {
                return fromValue(value.getMember("item"));
            }
            if (value.hasMember("tag")) {
                String tag = value.getMember("tag").asString();
                return fromString(tag.startsWith("#") ? tag : "#" + tag);
            }
        }

        throw new IllegalArgumentException("Unsupported ingredient value: " + value);
    }

    public static Ingredient combine(List<Ingredient> alternatives) {
        List<Ingredient> present = alternatives.stream().filter(ingredient -> ingredient != null && !ingredient.isEmpty()).toList();
        if (present.isEmpty()) return Ingredient.of();
        if (present.size() == 1) return present.getFirst();

        List<Holder<Item>> holders = new ArrayList<>();
        for (Ingredient ingredient : present) {
            for (Holder<Item> holder : ingredient.items().toList()) {
                holders.add(holder);
            }
        }
        if (holders.isEmpty()) return Ingredient.of();
        return Ingredient.of(HolderSet.direct(holders));
    }

    public static String normalizeItemId(String raw) {
        String id = normalizeRaw(raw);
        if (id.startsWith("#")) {
            throw new IllegalArgumentException("Expected item id but got tag id: " + raw);
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    public static String normalizeTagId(String raw) {
        String id = normalizeRaw(raw);
        String tag = id.startsWith("#") ? id.substring(1) : id;
        return "#" + (tag.contains(":") ? tag : "minecraft:" + tag);
    }

    private static String normalizeIngredientId(String raw) {
        String id = normalizeRaw(raw);
        if (id.equals("*") || id.startsWith("@") || id.startsWith("%") || id.startsWith("/")) {
            throw new IllegalArgumentException("Unsupported ingredient syntax: " + raw);
        }
        return id.startsWith("#") ? normalizeTagId(id) : normalizeItemId(id);
    }

    private static String normalizeRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Ingredient id cannot be empty");
        }
        return raw.trim();
    }
}
