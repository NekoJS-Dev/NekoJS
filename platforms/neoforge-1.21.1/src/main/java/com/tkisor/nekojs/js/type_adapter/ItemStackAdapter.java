package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * ItemStack 适配器（保留独立实现：string 正则 + object 解析较复杂）。
 *
 * <p>修复：
 * <ul>
 *   <li>B4：count 解析从 {@code idText.matches(...)} + {@code indexOf('x')} 改为预编译
 *       {@link Pattern}（{@code ^(\d+)x\s*(\S+)$}，容许无空格），消除脆弱的字符串切片。</li>
 *   <li>B10：{@code objectToItemStack} 不再 {@code new ItemStackAdapter().apply(...)}，
 *       复用 {@code this.apply(...)}。</li>
 *   <li>所有失败路径统一抛 {@link ValueConversionException}。</li>
 * </ul>
 */
public final class ItemStackAdapter implements JSTypeAdapter<ItemStack> {

    /** 匹配 "数量x id" 形式，空格可选（修复 B4：旧实现用 \s+ 且 indexOf('x') 脆弱）。 */
    private static final Pattern COUNT_PATTERN = Pattern.compile("^(\\d+)x\\s*(\\S+)$");

    @Override
    public Class<ItemStack> getTargetClass() {
        return ItemStack.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("Item"),
                host(Item.class),
                host(NekoId.class),
                object(
                        Slot.opt("item", registry("Item")),
                        Slot.opt("id", registry("Item")),
                        Slot.opt("count", number())));
    }

    @Override
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return HostAccess.TargetMappingPrecedence.LOW;
    }

    @Override
    public boolean test(Value value) {
        if (value == null || value.isNull() || value.isString()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof ItemStack || obj instanceof Item || obj instanceof NekoId;
        }
        return value.hasMembers() && (value.hasMember("item") || value.hasMember("id") || value.hasMember("tag"));
    }

    @Override
    public ItemStack apply(Value value) {
        if (value == null || value.isNull()) {
            return ItemStack.EMPTY;
        }

        if (value.isString()) {
            return stringToItemStack(value.asString());
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof ItemStack stack) return stack.copy();
            if (obj instanceof Item item) return item.getDefaultInstance();
            if (obj instanceof NekoId id) return idToItemStack(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()), 1);
        }

        if (value.hasMembers()) {
            return objectToItemStack(value);
        }

        throw new ValueConversionException(ItemStack.class, "string | Item | NekoId | item object", value,
            "unsupported item stack value");
    }

    public static ItemStack stringToItemStack(String raw) {
        if (isEmptyStackString(raw)) return ItemStack.EMPTY;

        String idText = raw.trim();
        int count = 1;
        Matcher m = COUNT_PATTERN.matcher(idText);
        if (m.matches()) {
            count = parsePositiveCount(m.group(1));
            idText = m.group(2).trim();
        }

        if (idText.startsWith("#")) {
            throw new ValueConversionException(ItemStack.class, "item id (not tag)", raw,
                "ItemStack cannot be created from a tag: " + raw);
        }
        if (!idText.contains(":")) {
            idText = "minecraft:" + idText;
        }

        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            throw new ValueConversionException(ItemStack.class, "item id string", raw,
                "invalid item id: " + raw);
        }
        return idToItemStack(id, count);
    }

    private ItemStack objectToItemStack(Value value) {
        if (value.hasMember("tag")) {
            throw new ValueConversionException(ItemStack.class, "item object (without 'tag')", value,
                "ItemStack cannot be created from a tag");
        }
        if (value.hasMember("item") && value.hasMember("id")) {
            throw new ValueConversionException(ItemStack.class, "item object ('item' xor 'id')", value,
                "ItemStack object cannot contain both 'item' and 'id'");
        }

        Value itemValue;
        if (value.hasMember("item")) {
            itemValue = value.getMember("item");
        } else if (value.hasMember("id")) {
            itemValue = value.getMember("id");
        } else {
            throw new ValueConversionException(ItemStack.class, "item object with 'item' or 'id'", value,
                "ItemStack object must contain 'item' or 'id'");
        }

        // B10: 复用本实例，避免 new ItemStackAdapter() 的开销与潜在状态割裂
        ItemStack stack = this.apply(itemValue);
        if (value.hasMember("count")) {
            return withCount(stack, parsePositiveInt(value.getMember("count"), "count"));
        }
        return stack;
    }

    private static ItemStack idToItemStack(ResourceLocation id, int count) {
        if (id.getPath().equals("air")) return ItemStack.EMPTY;
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(id);
        if (itemOpt.isEmpty()) {
            throw new ValueConversionException(ItemStack.class, "item id", id, "Item not found: " + id);
        }
        ItemStack stack = itemOpt.orElse(Items.AIR).getDefaultInstance();
        stack.setCount(count);
        return stack;
    }

    public static ItemStack withCount(ItemStack stack, int count) {
        parsePositiveCount(count);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private static int parsePositiveInt(Value value, String name) {
        if (!value.isNumber() || !value.fitsInInt()) {
            throw new ValueConversionException(ItemStack.class, "integer", value,
                "ItemStack " + name + " must be an integer");
        }
        return parsePositiveCount(value.asInt());
    }

    private static int parsePositiveCount(String raw) {
        try {
            return parsePositiveCount(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            throw new ValueConversionException(ItemStack.class, "integer", raw,
                "ItemStack count must be an integer: " + raw, e);
        }
    }

    private static int parsePositiveCount(int count) {
        if (count <= 0) {
            throw new ValueConversionException(ItemStack.class, "positive integer", count,
                "ItemStack count must be positive: " + count);
        }
        return count;
    }

    private static boolean isEmptyStackString(String raw) {
        if (raw == null) return true;
        return switch (raw.trim()) {
            case "", "-", "empty", "minecraft:empty", "air", "minecraft:air" -> true;
            default -> false;
        };
    }
}
