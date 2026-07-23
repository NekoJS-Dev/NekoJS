package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
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

import java.util.*;
import java.util.regex.Pattern;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 Ingredient 适配器（自包含，无外部 Resolver 依赖）。
 * 支持字符串前缀：item:id / #tag(ore) / @mod / * / /regex/
 * 以及对象形式：{ item, tag, mod, regex, wildcard, any }。
 *
 * <p>1.12.2 适配：
 *   <li>使用 {@link ForgeRegistries#ITEMS} 替代 BuiltInRegistries</li>
 *   <li>使用 {@link OreIngredient} 替代 TagKey-based ingredient</li>
 *   <li>使用 {@code Ingredient.fromItem/fromStacks/fromItems} 替代 {@code Ingredient.of}</li>
 * </p>
 */
public final class IngredientAdapter implements JSTypeAdapter<Ingredient> {

    @Override
    public Class<Ingredient> getTargetClass() {
        return Ingredient.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                arrayOf(self()),
                host(ItemStack.class),
                host(Item.class),
                host(NekoId.class),
                object(
                        Slot.opt("item", string()),
                        Slot.opt("tag", string()),
                        Slot.opt("mod", string()),
                        Slot.opt("regex", string()),
                        Slot.opt("wildcard", bool()),
                        Slot.opt("any", arrayOf(self()))));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("item:id | #tag | @mod | * | /regex/ | { item?|tag?|mod?|regex?|wildcard?|any? }");
    }

    @Override
    public boolean test(Value value) {
        if (value.isNull() || value.isString() || value.hasArrayElements() || value.hasMembers()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof Ingredient || obj instanceof ItemStack || obj instanceof Item || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public Ingredient apply(Value value) {
        try {
            return fromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(Ingredient.class, "item / item id / ingredient object", value,
                e.getMessage(), e);
        }
    }

    // ===================== 核心分派 =====================

    public static Ingredient fromValue(Value value) {
        if (value == null || value.isNull()) return Ingredient.EMPTY;
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof Ingredient ingredient) return ingredient;
            if (obj instanceof ItemStack stack) return fromStack(stack);
            if (obj instanceof Item item) return fromItem(item);
            if (obj instanceof NekoId id) {
                return fromItemOrOreId(id.toString());
            }
            if (obj == null) return Ingredient.EMPTY;
        }
        if (value.hasArrayElements()) return compound(value);
        if (value.hasMembers()) return fromObject(value);
        throw new ValueConversionException(Ingredient.class,
            "item / item id / ingredient object / ingredient array", value, "unsupported ingredient value");
    }

    // ===================== 字符串解析 =====================

    private static Ingredient fromString(String raw) {
        if (raw == null || raw.trim().isEmpty())
            throw new ValueConversionException(Ingredient.class, "non-blank string", raw, "ingredient id cannot be empty");
        String s = raw.trim();
        char c = s.charAt(0);
        if (c == '*') return wildcard();
        if (c == '@') return modIngredient(s.substring(1));
        if (c == '/') {
            String body = (s.length() > 2 && s.charAt(s.length() - 1) == '/')
                ? s.substring(1, s.length() - 1) : s.substring(1);
            return regexIngredient(body);
        }
        return fromItemOrOreId(s);
    }

    private static Ingredient fromItemOrOreId(String s) {
        // 1.12.2: #tag 用 OreDictionary / OreIngredient
        if (s.startsWith("#")) {
            String oreName = s.substring(1);
            if (OreDictionary.getOres(oreName).isEmpty()) {
                throw new ValueConversionException(Ingredient.class, "existing ore dictionary name", s,
                    "ore tag not found: " + s);
            }
            return new OreIngredient(oreName);
        }
        // item id
        ResourceLocation id;
        try {
            String fullId = s.contains(":") ? s : "minecraft:" + s;
            id = new ResourceLocation(fullId);
        } catch (Exception e) {
            throw new ValueConversionException(Ingredient.class, "item id / #tag / @mod / * / /regex/", s,
                "invalid identifier: " + s);
        }
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            throw new ValueConversionException(Ingredient.class, "registered item id", s,
                "item not found: " + s);
        }
        return Ingredient.fromItem(ForgeRegistries.ITEMS.getValue(id));
    }

    // ===================== 宿主对象 =====================

    private static Ingredient fromItem(Item item) {
        // 1.12.2: Ingredient.fromItem(Item) 替代 Ingredient.of(Item)
        return Ingredient.fromItem(item);
    }

    private static Ingredient fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Ingredient.EMPTY;
        // 1.12.2: Ingredient.fromStacks(ItemStack...)
        return Ingredient.fromStacks(stack);
    }

    // ===================== 前缀处理器 =====================

    private static Ingredient wildcard() {
        // 展开所有已注册物品
        Collection<Item> allItems = ForgeRegistries.ITEMS.getValuesCollection();
        Item[] items = allItems.toArray(new Item[0]);
        return Ingredient.fromItems(items);
    }

    private static Ingredient modIngredient(String modId) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation rl = item.getRegistryName();
            if (rl != null && rl.getNamespace().equals(modId)) {
                stacks.add(new ItemStack(item));
            }
        }
        if (stacks.isEmpty()) return Ingredient.EMPTY;
        return Ingredient.fromStacks(stacks.toArray(new ItemStack[0]));
    }

    private static Ingredient regexIngredient(String regex) {
        Pattern pattern = Pattern.compile(regex);
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation rl = item.getRegistryName();
            if (rl != null && pattern.matcher(rl.toString()).find()) {
                stacks.add(new ItemStack(item));
            }
        }
        if (stacks.isEmpty()) return Ingredient.EMPTY;
        return Ingredient.fromStacks(stacks.toArray(new ItemStack[0]));
    }

    // ===================== 对象/数组 =====================

    private static Ingredient fromObject(Value value) {
        if (value.hasMember("any")) return compound(value.getMember("any"));
        if (value.hasMember("wildcard") && value.getMember("wildcard").asBoolean()) return wildcard();
        if (value.hasMember("mod")) return modIngredient(value.getMember("mod").asString());
        if (value.hasMember("regex")) return regexIngredient(value.getMember("regex").asString());
        if (value.hasMember("item")) return fromValue(value.getMember("item"));
        if (value.hasMember("tag")) {
            String tag = value.getMember("tag").asString();
            return fromString(tag.startsWith("#") ? tag : "#" + tag);
        }
        throw new ValueConversionException(Ingredient.class,
            "recognized field (item|tag|mod|regex|wildcard|any)", value,
            "no recognized field in ingredient object");
    }

    private static Ingredient compound(Value value) {
        if (!value.hasArrayElements()) {
            throw new ValueConversionException(Ingredient.class, "ingredient array", value, "expected array");
        }
        // 1.12.2: 将所有子 ingredient 的匹配栈合并为一个，用 Ingredient.fromStacks
        Set<ItemStack> allStacks = new LinkedHashSet<>();
        for (long i = 0; i < value.getArraySize(); i++) {
            Ingredient ing = fromValue(value.getArrayElement(i));
            if (!ing.equals(Ingredient.EMPTY)) {
                Collections.addAll(allStacks, ing.getMatchingStacks());
            }
        }
        if (allStacks.isEmpty()) return Ingredient.EMPTY;
        return Ingredient.fromStacks(allStacks.toArray(new ItemStack[0]));
    }
}
