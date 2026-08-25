package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.NekoJS;
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
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class IngredientResolver {
    private IngredientResolver() {}

    /** 26.x 的 DefaultedRegistry 没有 asLookup()；从 vanilla RegistryAccess 取 lookup。 */
    private static final HolderLookup.RegistryLookup<Item> ITEM_LOOKUP =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.ITEM);

    /**
     * 脚本提供的 regex 源串 → 预编译 Pattern 缓存。{@code /regex/} 语法与 {@code {regex: ...}}
     * 的模式串来自脚本（非编译期常量，无法 hoist 成 static final）；Pattern 不可变且线程安全，
     * 按源串去重可在重复解析同一批配方时省去逐次编译。规模以脚本中不同 regex 数为上界。
     * 非法 regex 抛 PatternSyntaxException，不缓存（与原行为一致）。
     */
    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    /**
     * Tag-created ingredients → their {@link TagKey}，供序列化改写为 "#id" 引用形态（见 {@link #tagOriginOf}）。
     * WeakHashMap：一次配方 reload 会创建成千上万短命 ingredient，不能进程级泄漏。
     * {@code Ingredient#equals} 按 values 比较——同一 tag 的多个实例互撞映射到相同 TagKey，无害。
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

    // ===================== 字符串前缀分派 =====================

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
        return ((ItemStackExtension) (Object) stack).neko$asIngredient();
    }

    public static Ingredient fromIngredient(Ingredient ingredient) {
        return ingredient;
    }

    public static Ingredient fromWrapper(IngredientJS wrapper) {
        return wrapper.unwrap();
    }

    /** 匹配所有已注册物品的真 wildcard（live AnyHolderSet，注册表变化即时反映）。 */
    public static Ingredient wildcard() {
        return ingredientOfHolders(new AnyHolderSet<>(ITEM_LOOKUP));
    }

    /**
     * 配方 JSON 序列化时取回 tag ingredient 的 {@link TagKey}：{@code #tag} 解析出的
     * HolderSet.Named 绑定在 BuiltInRegistries 的 owner 上，而配方序列化用的是本次数据包
     * 加载的 RegistryOps——两者包装层实例不同，{@code HolderSetCodec} 的 owner 校验恒拒绝
     * （"is not valid in current registry set"，真机表现为 shaped 配方里写 {@code #tag}
     * 原料必炸）。序列化侧据此改写为 vanilla 合法的 {@code {"tag": ...}} 引用形态，
     * 重新 parse 时由当前 RegistryOps 自行解析。非 tag 来源返回 {@code null}。
     */
    public static TagKey<Item> tagOriginOf(Ingredient ingredient) {
        return ingredient == null ? null : TAG_ORIGIN.get(ingredient);
    }

    /** 取反 ingredient：返回匹配「除 excluded 外所有物品」的 DifferenceIngredient。 */
    public static Ingredient not(Ingredient excluded) {
        return DifferenceIngredient.of(wildcard(), excluded);
    }

    // ===================== fromValue 统一入口（字符串/对象/数组/host）=====================

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

    // ===================== 对象分派（声明式，超越 KubeJS）=====================

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
                holder -> new ItemStack(holder, 1, DataComponentPatch.EMPTY)));
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
        return new CompoundIngredient(list).toVanilla();
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

    // ===================== 给 IngredientJS.or() 用的旧 combine（保持兼容）=====================

    public static Ingredient combine(List<Ingredient> alternatives) {
        List<Ingredient> present = alternatives.stream().filter(i -> i != null && !i.isEmpty()).toList();
        if (present.isEmpty()) return Ingredient.of();
        if (present.size() == 1) return present.getFirst();
        List<Holder<Item>> holders = new ArrayList<>();
        for (Ingredient ingredient : present) {
            // items() 无等价非废弃 API（getValues().stream() 不含 custom ingredient 展开），保守保留
            @SuppressWarnings("deprecation")
            List<Holder<Item>> expanded = ingredient.items().toList();
            holders.addAll(expanded);
        }
        if (holders.isEmpty()) return Ingredient.of();
        // holders 是 List<Holder<Item>>，必须先 unwrap 成 Item[]，否则 toArray(new Item[0]) 会
        // 因元素类型是 Holder 而抛 arraycopy: element type mismatch。
        return Ingredient.of(holders.stream().map(Holder::value).toArray(Item[]::new));
    }

    // ===================== 旧的 id normalize（保留 API，IngredientJS 可能用）=====================

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
