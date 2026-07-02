package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.inject.ItemStackExtension;
import com.tkisor.nekojs.holder.NamespaceHolderSet;
import com.tkisor.nekojs.holder.PredicateHolderSet;
import com.tkisor.nekojs.holder.RegexHolderSet;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 1.21.1 Ingredient 解析：支持字符串前缀 {@code @mod} / {@code *} / {@code /regex/} /
 * {@code #tag} / item:id，以及对象形式 {@code { filter, any, all, not, mod, regex, wildcard, item, tag }}。
 * 不做 DataComponent 解析（1.21.1 component API 不同）。
 */
public final class IngredientResolver {
    private IngredientResolver() {}

    private static final HolderLookup.RegistryLookup<Item> ITEM_LOOKUP =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.ITEM);

    public static Ingredient fromString(String raw) {
        String s = normalizeRaw(raw);
        char c = s.charAt(0);
        return switch (c) {
            case '*' -> ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
            case '@' -> ingredientOfHolders(new NamespaceHolderSet<>(ITEM_LOOKUP, s.substring(1)));
            case '/' -> {
                String body = (s.length() > 2 && s.charAt(s.length() - 1) == '/')
                    ? s.substring(1, s.length() - 1) : s.substring(1);
                yield ingredientOfHolders(new RegexHolderSet<>(ITEM_LOOKUP, Pattern.compile(body)));
            }
            default -> fromItemOrTagId(s);
        };
    }

    private static Ingredient fromItemOrTagId(String s) {
        ResourceLocation location = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
        if (location == null) {
            throw new ValueConversionException(Ingredient.class, "item id / #tag / @mod / * / /regex/", s,
                "invalid identifier: " + s);
        }
        if (s.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, location);
            // 1.21.1：getTag 返回 Optional<Set<Holder<Item>>>，转 Item[] 再 Ingredient.of
            var tagSet = BuiltInRegistries.ITEM.getTag(tagKey);
            if (tagSet.isEmpty()) {
                throw new ValueConversionException(Ingredient.class, "existing item tag", s, "item tag not found: " + s);
            }
            Item[] items = tagSet.get().stream().map(Holder::value).toArray(Item[]::new);
            return Ingredient.of(items);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(location)
            .orElseThrow(() -> new ValueConversionException(Ingredient.class, "registered item id", s,
                "item not found: " + s));
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
        if (value == null || value.isNull()) return Ingredient.of();
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof IngredientJS wrapper) return fromWrapper(wrapper);
            if (obj instanceof Ingredient ingredient) return fromIngredient(ingredient);
            if (obj instanceof ItemStack stack) return fromStack(stack);
            if (obj instanceof Item item) return fromItem(item);
            if (obj instanceof NekoId id) return fromNekoId(id);
            if (obj == null) return Ingredient.of();
        }
        if (value.hasArrayElements()) return compound(value);
        if (value.hasMembers()) return fromObject(value);
        throw new ValueConversionException(Ingredient.class,
            "item / item id / ingredient object / ingredient array", value, "unsupported ingredient value");
    }

    private static Ingredient fromObject(Value value) {
        // 组合 / 谓词优先于简单字段
        if (value.hasMember("filter")) {
            Value fn = value.getMember("filter");
            if (!fn.canExecute()) {
                throw new ValueConversionException(Ingredient.class,
                    "{ filter: (item)=>boolean }", value, "'filter' must be a function");
            }
            return ingredientOfHolders(new PredicateHolderSet<>(
                ITEM_LOOKUP, fn,
                holder -> new ItemStack(holder.value(), 1)));
        }
        if (value.hasMember("any")) return compound(value.getMember("any"));
        if (value.hasMember("all")) return intersection(value.getMember("all"));
        if (value.hasMember("not")) {
            Ingredient all = ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
            return DifferenceIngredient.of(all, fromValue(value.getMember("not")));
        }
        if (value.hasMember("wildcard") && value.getMember("wildcard").asBoolean()) {
            return ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
        }
        if (value.hasMember("mod")) {
            return ingredientOfHolders(new NamespaceHolderSet<>(ITEM_LOOKUP,
                value.getMember("mod").asString()));
        }
        if (value.hasMember("regex")) {
            return ingredientOfHolders(new RegexHolderSet<>(ITEM_LOOKUP,
                Pattern.compile(value.getMember("regex").asString())));
        }
        if (value.hasMember("item")) return fromValue(value.getMember("item"));
        if (value.hasMember("tag")) {
            String tag = value.getMember("tag").asString();
            return fromString(tag.startsWith("#") ? tag : "#" + tag);
        }
        throw new ValueConversionException(Ingredient.class,
            "recognized field (item|tag|mod|regex|wildcard|filter|any|all|not)", value,
            "no recognized field in ingredient object");
    }

    private static Ingredient compound(Value value) {
        if (!value.hasArrayElements()) {
            throw new ValueConversionException(Ingredient.class, "ingredient array", value, "expected array");
        }
        List<Ingredient> list = new ArrayList<>();
        for (long i = 0; i < value.getArraySize(); i++) {
            Ingredient ing = fromValue(value.getArrayElement(i));
            if (!ing.isEmpty()) list.add(ing);
        }
        if (list.isEmpty()) return Ingredient.of();
        if (list.size() == 1) return list.get(0);
        // 1.21.1：CompoundIngredient 不继承 Ingredient，OR 组合展开成 Item[]
        List<Holder<Item>> holders = new ArrayList<>();
        for (Ingredient ing : list) {
            for (ItemStack stack : ing.getItems()) {
                if (!stack.isEmpty()) holders.add(stack.getItem().builtInRegistryHolder());
            }
        }
        if (holders.isEmpty()) return Ingredient.of();
        return Ingredient.of(holders.stream().map(Holder::value).toArray(Item[]::new));
    }

    private static Ingredient intersection(Value value) {
        if (!value.hasArrayElements()) {
            throw new ValueConversionException(Ingredient.class, "ingredient array", value, "'all' must be an array");
        }
        Ingredient result = null;
        for (long i = 0; i < value.getArraySize(); i++) {
            Ingredient ing = fromValue(value.getArrayElement(i));
            result = (result == null) ? ing : IntersectionIngredient.of(result, ing);
        }
        return result == null ? Ingredient.of() : result;
    }

    private static Ingredient ingredientOfHolders(HolderSet<Item> holders) {
        Item[] items = holders.stream().map(Holder::value).toArray(Item[]::new);
        return Ingredient.of(items);
    }

    public static Ingredient combine(List<Ingredient> alternatives) {
        List<Ingredient> present = alternatives.stream().filter(i -> i != null).toList();
        if (present.isEmpty()) return Ingredient.of();
        if (present.size() == 1) return present.getFirst();
        List<Holder<Item>> holders = new ArrayList<>();
        for (Ingredient ingredient : present) {
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) holders.add(stack.getItem().builtInRegistryHolder());
            }
        }
        if (holders.isEmpty()) return Ingredient.of();
        return Ingredient.of(holders.stream().map(Holder::value).toArray(Item[]::new));
    }

    public static String normalizeItemId(String raw) {
        String id = normalizeRaw(raw);
        if (id.startsWith("#")) {
            throw new ValueConversionException(Ingredient.class, "item id (no '#' prefix)", raw,
                "expected item id but got tag id");
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    public static String normalizeTagId(String raw) {
        String id = normalizeRaw(raw);
        String tag = id.startsWith("#") ? id.substring(1) : id;
        return "#" + (tag.contains(":") ? tag : "minecraft:" + tag);
    }

    private static String normalizeRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValueConversionException(Ingredient.class, "non-blank string", raw,
                "ingredient id cannot be empty");
        }
        return raw.trim();
    }
}
