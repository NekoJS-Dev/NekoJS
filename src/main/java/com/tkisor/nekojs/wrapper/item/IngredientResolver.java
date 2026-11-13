// 26.x implementation; no version guard should appear in this file again. The 1.21.1 implementation is the
// file of the same name under versions/1.21.1/src, so changing this file's behavior means updating it too.
package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
//? if neoforge {
import com.tkisor.nekojs.api.inject.ItemStackExtension;
//?}
import com.tkisor.nekojs.holder.NamespaceHolderSet;
import com.tkisor.nekojs.holder.PredicateHolderSet;
import com.tkisor.nekojs.holder.RegexHolderSet;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
//? if neoforge {
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
//?}
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class IngredientResolver {
    private IngredientResolver() {}

    /** The 26.x DefaultedRegistry has no asLookup(); the lookup comes from the vanilla RegistryAccess. */
    private static final HolderLookup.RegistryLookup<Item> ITEM_LOOKUP =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.ITEM);

    /**
     * Script-supplied regex source -> precompiled Pattern cache. The {@code /regex/} syntax and the {@code {regex: ...}}
     * pattern string come from scripts (not compile-time constants, so they cannot be hoisted into a static final);
     * a Pattern is immutable and thread-safe, so deduplicating by source string skips recompiling when the same
     * batch of recipes is parsed repeatedly. The size is bounded by the number of distinct regexes in the scripts.
     * An invalid regex throws PatternSyntaxException and is not cached.
     */
    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    /**
     * Tag-created ingredients -> their {@link TagKey}, so serialization can rewrite them into the "#id" reference form
     * (see {@link #tagOriginOf}). WeakHashMap: one recipe reload creates thousands of short-lived ingredients, which
     * must not leak process-wide. {@code Ingredient#equals} compares by values, so several instances of the same tag
     * collide onto the same TagKey, which is harmless.
     */
    private static final Map<Ingredient, TagKey<Item>> TAG_ORIGIN =
        Collections.synchronizedMap(new WeakHashMap<>());

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
            case '*' -> allItemsSnapshot();
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
        Identifier location = Identifier.tryParse(s.startsWith("#") ? s.substring(1) : s);
        if (location == null) {
            throw new ValueConversionException(Ingredient.class, "item id / #tag / @mod / * / /regex/", s,
                "invalid identifier: " + s);
        }
        if (s.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, location);
            var tag = BuiltInRegistries.ITEM.get(tagKey);
            if (tag.isEmpty()) {
                throw new ValueConversionException(Ingredient.class, "existing item tag", s, "item tag not found: " + s);
            }
            Ingredient ingredient = Ingredient.of(tag.get());
            TAG_ORIGIN.put(ingredient, tagKey);
            return ingredient;
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
//? if neoforge {
        return ((ItemStackExtension) (Object) stack).neko$asIngredient();
//?} else {
/*        // fabric has no ItemStackExtension injection: degrade to a plain item (dropping data components); the fabric
        // side of 26.x component ingredients lands with the ItemStackExtension port batch (see docs/fabric-port-status.md)
        return Ingredient.of(stack.getItem());
*///?}
    }

    public static Ingredient fromIngredient(Ingredient ingredient) {
        return ingredient;
    }

    public static Ingredient fromWrapper(IngredientJS wrapper) {
        return wrapper.unwrap();
    }

    /** Wildcard matching every registered item (snapshot semantics: ingredientOfHolders already flattens to Item[]). */
    public static Ingredient wildcard() {
        return allItemsSnapshot();
    }

    /**
     * Retrieves the {@link TagKey} of a tag ingredient during recipe JSON serialization: the HolderSet.Named parsed
     * from {@code #tag} is bound to the BuiltInRegistries owner, while recipe serialization uses this data pack load's
     * RegistryOps -- the two wrap different instances, so the HolderSetCodec owner check always rejects it
     * ("is not valid in current registry set", which in game always blew up on a shaped recipe with a {@code #tag}
     * ingredient). The serialization side therefore rewrites it into the vanilla-legal {@code {"tag": ...}} reference
     * form, and the current RegistryOps resolves it when parsing again. Returns {@code null} for a non-tag origin.
     */
    public static TagKey<Item> tagOriginOf(Ingredient ingredient) {
        return ingredient == null ? null : TAG_ORIGIN.get(ingredient);
    }

    /** Inverted ingredient: matches every item except the excluded ones. */
    public static Ingredient not(Ingredient excluded) {
//? if neoforge {
        return DifferenceIngredient.of(wildcard(), excluded);
//?} else {
/*        // fabric has no DifferenceIngredient: wildcard snapshot minus the excluded match set (equivalent at the expansion level)
        @SuppressWarnings("deprecation")
        var excludedItems = excluded.items().map(Holder::value).collect(java.util.stream.Collectors.toSet());
        return Ingredient.of(ITEM_LOOKUP.listElements().map(Holder::value)
                .filter(item -> !excludedItems.contains(item)).toArray(Item[]::new));
*///?}
    }

    // ===================== fromValue entry point (string/object/array/host) =====================

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

    // ===================== Object dispatch (declarative, beyond KubeJS) =====================

    private static Ingredient fromObject(Value value) {
        // Combinations and predicates take precedence over simple fields
        if (value.hasMember("filter")) {
            Value fn = value.getMember("filter");
            if (!fn.canExecute()) {
                throw new ValueConversionException(Ingredient.class,
                    "{ filter: (item)=>boolean }", value, "'filter' must be a function");
            }
            return ingredientOfHolders(new PredicateHolderSet<>(
                ITEM_LOOKUP, fn,
                holder -> new ItemStack(holder, 1, DataComponentPatch.EMPTY)));
        }
        if (value.hasMember("any")) return compound(value.getMember("any"));
        if (value.hasMember("all")) return intersection(value.getMember("all"));
        if (value.hasMember("not")) {
//? if neoforge {
            Ingredient all = ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
            return DifferenceIngredient.of(all, fromValue(value.getMember("not")));
//?} else {
/*            return not(fromValue(value.getMember("not")));
*///?}
        }
        if (value.hasMember("wildcard") && value.getMember("wildcard").asBoolean()) {
            return wildcard();
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
//? if neoforge {
        return new CompoundIngredient(list).toVanilla();
//?} else {
/*        // fabric has no CompoundIngredient: the expansion merge of combine (the neutral method below) is equivalent
        return combine(list);
*///?}
    }

    private static Ingredient intersection(Value value) {
        if (!value.hasArrayElements()) {
            throw new ValueConversionException(Ingredient.class, "ingredient array", value, "'all' must be an array");
        }
        Ingredient result = null;
        for (long i = 0; i < value.getArraySize(); i++) {
            Ingredient ing = fromValue(value.getArrayElement(i));
//? if neoforge {
            result = (result == null) ? ing : IntersectionIngredient.of(result, ing);
//?} else {
/*            result = (result == null) ? ing : intersectItems(result, ing);
*///?}
        }
        return result == null ? Ingredient.of() : result;
    }

    private static Ingredient ingredientOfHolders(HolderSet<Item> holders) {
        Item[] items = holders.stream().map(Holder::value).toArray(Item[]::new);
        return Ingredient.of(items);
    }

    /** Snapshot of all registered items (neoforge flattens a live AnyHolderSet, fabric enumerates the lookup directly -- both are snapshots). */
    private static Ingredient allItemsSnapshot() {
//? if neoforge {
        return ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
//?} else {
/*        return Ingredient.of(ITEM_LOOKUP.listElements().map(Holder::value).toArray(Item[]::new));
*///?}
    }

    /** Intersection of the match sets of two ingredients (a snapshot; the neoforge side uses IntersectionIngredient, see intersection). */
    @SuppressWarnings("deprecation")
    private static Ingredient intersectItems(Ingredient a, Ingredient b) {
        var set = b.items().map(Holder::value).collect(java.util.stream.Collectors.toSet());
        return Ingredient.of(a.items().map(Holder::value).filter(set::contains).toArray(Item[]::new));
    }

    // ===================== Legacy combine used by IngredientJS.or() (kept compatible) =====================

    public static Ingredient combine(List<Ingredient> alternatives) {
        List<Ingredient> present = alternatives.stream().filter(i -> i != null && !i.isEmpty()).toList();
        if (present.isEmpty()) return Ingredient.of();
        if (present.size() == 1) return present.getFirst();
        List<Holder<Item>> holders = new ArrayList<>();
        for (Ingredient ingredient : present) {
            // items() has no equivalent non-deprecated API (getValues().stream() does not expand custom ingredients), so it is kept on purpose
            @SuppressWarnings("deprecation")
            List<Holder<Item>> expanded = ingredient.items().toList();
            holders.addAll(expanded);
        }
        if (holders.isEmpty()) return Ingredient.of();
        // holders is List<Holder<Item>> and must be unwrapped into Item[] first, otherwise toArray(new Item[0])
        // throws arraycopy: element type mismatch because the element type is Holder.
        return Ingredient.of(holders.stream().map(Holder::value).toArray(Item[]::new));
    }

    // ===================== Legacy id normalize (kept as API, IngredientJS may use it) =====================

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

