package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.js.type_adapter.ParseIds;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Pattern;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

public final class ItemStackAdapter implements JSTypeAdapter<ItemStack> {

    // B4: 容忍无空格（"1xminecraft:stone"），用 Pattern 替代 matches()+indexOf()（后者在 namespace 前出现 x 也有 bug）
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
                        Slot.opt("count", number()))
        );
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
            if (obj instanceof Item item) return itemToItemStack(item, 1);
            if (obj instanceof NekoId id) return idToItemStack(Identifier.fromNamespaceAndPath(id.namespace(), id.path()), 1);
        }

        if (value.hasMembers()) {
            return objectToItemStack(value);
        }

        throw new ValueConversionException(ItemStack.class, "item stack value", value, "unsupported item stack value");
    }

    public static ItemStack stringToItemStack(String raw) {
        if (isEmptyStackString(raw)) return ItemStack.EMPTY;

        String idText = raw.trim();
        int count = 1;
        var m = COUNT_PATTERN.matcher(idText);
        if (m.matches()) {
            count = ParseIds.parsePositiveCount(m.group(1));
            idText = m.group(2);
        }

        // id 部分复用 ParseIds（统一 trim / minecraft: 前缀 / 拒绝 tag 前缀 / tryParse）
        Identifier id = ParseIds.parseItemOrBlockId(idText);
        return idToItemStack(id, count);
    }

    private ItemStack objectToItemStack(Value value) {
        if (value.hasMember("tag")) {
            throw new ValueConversionException(ItemStack.class, "item stack object (no 'tag')", value,
                "ItemStack cannot be created from a tag");
        }
        if (value.hasMember("item") && value.hasMember("id")) {
            throw new ValueConversionException(ItemStack.class, "item stack object", value,
                "ItemStack object cannot contain both 'item' and 'id'");
        }

        Value itemValue;
        if (value.hasMember("item")) {
            itemValue = value.getMember("item");
        } else if (value.hasMember("id")) {
            itemValue = value.getMember("id");
        } else {
            throw new ValueConversionException(ItemStack.class, "item stack object with 'item' or 'id'", value,
                "ItemStack object must contain 'item' or 'id'");
        }

        // B10: 复用当前实例，不再每次 new
        ItemStack stack = this.apply(itemValue);
        if (value.hasMember("count")) {
            return withCount(stack, parsePositiveInt(value.getMember("count"), "count"));
        }
        return stack;
    }

    private static ItemStack idToItemStack(Identifier id, int count) {
        if (id.getPath().equals("air")) return ItemStack.EMPTY;
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Holder<Item> holder = BuiltInRegistries.ITEM.get(key)
                .orElseThrow(() -> new ValueConversionException(ItemStack.class, "registered item id", id,
                    "Item not found: " + id));
        return new ItemStack(holder, count, DataComponentPatch.EMPTY);
    }

    private static ItemStack itemToItemStack(Item item, int count) {
        if (item == BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("air"))) return ItemStack.EMPTY;
        Holder<Item> holder = item.builtInRegistryHolder();
        return new ItemStack(holder, count, DataComponentPatch.EMPTY);
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
            throw new ValueConversionException(ItemStack.class, "integer " + name, value,
                "ItemStack " + name + " must be an integer");
        }
        return parsePositiveCount(value.asInt());
    }

    private static int parsePositiveCount(int count) {
        if (count <= 0) {
            throw new ValueConversionException(ItemStack.class, "positive integer count", count,
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
