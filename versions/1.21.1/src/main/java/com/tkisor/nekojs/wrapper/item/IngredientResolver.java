// 1.21.1 implementation, paired with the 26.x file of the same name under the version tree's src/.
// The content is that file as evaluated on this node. Keep this file in sync when the 26.x side
// changes behavior.
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class IngredientResolver {
    private IngredientResolver() {}

    /** 26.x's DefaultedRegistry has no asLookup(); take the lookup from vanilla RegistryAccess. */
    private static final HolderLookup.RegistryLookup<Item> ITEM_LOOKUP =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.ITEM);

    /**
     * Caches compiled {@link Pattern} instances keyed by the regex source string from scripts.
     * The pattern source of {@code /regex/} syntax and {@code {regex: ...}} comes from scripts,
     * so it is not a compile-time constant and cannot be hoisted into a static final. Pattern is
     * immutable and thread-safe, so deduplicating by source string skips recompiling when the same
     * batch of recipes is parsed repeatedly. The number of entries is bounded by the number of
     * distinct regexes in scripts. An invalid regex throws PatternSyntaxException and is not cached.
     */
    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    /**
     * Tag-created ingredients to their {@link TagKey}, so serialization can rewrite them into the
     * "#id" reference form (see {@link #tagOriginOf}). This is a WeakHashMap: one recipe reload
     * creates thousands of short-lived ingredients, and they must not leak for the process
     * lifetime. {@code Ingredient#equals} compares by values, so several instances of the same tag
     * collide onto the same TagKey, which is harmless.
     */

    private static Pattern compiledRegex(String regex) {
        Pattern cached = REGEX_CACHE.get(regex);
        if (cached == null) {
            cached = Pattern.compile(regex);
            REGEX_CACHE.put(regex, cached);
        }
        return cached;
    }

    // ===================== String prefix dispatch =====================

    public static Ingredient fromString(String raw) {
        String s = normalizeRaw(raw);
        char c = s.charAt(0);
        return switch (c) {
            case '*' -> ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
            case '@' -> ingredientOfHolders(new NamespaceHolderSet<>(ITEM_LOOKUP, s.substring(1)));
            case '/' -> {
                String body = (s.length() > 2 && s.charAt(s.length() - 1) == '/')
                    ? s.substring(1, s.length() - 1) : s.substring(1);
                yield ingredientOfHolders(new RegexHolderSet<>(ITEM_LOOKUP, compiledRegex(body)));
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

    /**
     * A true wildcard matching every registered item. The live {@code AnyHolderSet}
     * reflects registry changes immediately.
     */
    public static Ingredient wildcard() {
        return ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
    }

    /**
     * Returns the {@link TagKey} of a tag ingredient when serializing recipe JSON. The
     * HolderSet.Named that {@code #tag} resolves to is bound to the owner from BuiltInRegistries,
     * while recipe serialization uses the RegistryOps of the current data pack load; the two
     * wrapper instances differ, so the owner check in {@code HolderSetCodec} always rejects them
     * ("is not valid in current registry set", which in game makes a shaped recipe that uses a
     * {@code #tag} ingredient fail). Serialization therefore rewrites the ingredient into the
     * vanilla-valid {@code {"tag": ...}} reference form, and the current RegistryOps resolves the
     * tag again when the recipe is parsed. Returns {@code null} for an ingredient that did not
     * come from a tag.
     */

    /** Negated ingredient: returns a DifferenceIngredient that matches every item except {@code excluded}. */
    public static Ingredient not(Ingredient excluded) {
        return DifferenceIngredient.of(wildcard(), excluded);
    }

    // ===================== fromValue single entry point (string/object/array/host) =====================

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

    // ===================== Object dispatch (declarative, goes beyond KubeJS) =====================

    private static Ingredient fromObject(Value value) {
        // Combinators and predicates take precedence over plain fields
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
                compiledRegex(value.getMember("regex").asString())));
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
        return CompoundIngredient.of(list.toArray(new Ingredient[0]));
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

    // ===================== Legacy combine used by IngredientJS.or() (kept for compatibility) =====================

    public static Ingredient combine(List<Ingredient> alternatives) {
        List<Ingredient> present = alternatives.stream().filter(i -> i != null).toList();
        if (present.isEmpty()) return Ingredient.of();
        if (present.size() == 1) return present.getFirst();
        List<Holder<Item>> holders = new ArrayList<>();
        for (Ingredient ingredient : present) {
            // No equivalent non-deprecated API for items() (getValues().stream() does not expand custom
            // ingredients), so keep using it.
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) holders.add(stack.getItemHolder());
            }
        }
        if (holders.isEmpty()) return Ingredient.of();
        // holders is a List<Holder<Item>>, so it must be unwrapped to Item[] first; otherwise
        // toArray(new Item[0]) throws arraycopy: element type mismatch because the elements are Holder.
        return Ingredient.of(holders.stream().map(Holder::value).toArray(Item[]::new));
    }

    // ===================== Legacy id normalize (API kept, IngredientJS may use it) =====================

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
