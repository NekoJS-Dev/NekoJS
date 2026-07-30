package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 ItemStack 适配器。
 * ItemStack 构造用 {@code new ItemStack(item)}（1.12.2 无 getDefaultInstance()）。
 * 注册表查找使用 {@link ForgeRegistries#ITEMS}。
 */
public final class ItemStackAdapter implements JSTypeAdapter<ItemStack> {

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
    public Optional<String> syntaxDoc() {
        return Optional.of("item:id | RegistryTypes.Item | $Item | $NekoId | { item?|id?, count? }");
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOW;
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
        if (value.isString()) return stringToItemStack(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof ItemStack stack) return stack.copy();
            if (obj instanceof Item item) return itemToItemStack(item, 1);
            if (obj instanceof NekoId id) return idToItemStack(new ResourceLocation(id.namespace(), id.path()), 1);
        }
        if (value.hasMembers()) return objectToItemStack(value);
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
        ResourceLocation id = ParseIds.parseItemOrBlockId(idText);
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
        ItemStack stack = this.apply(itemValue);
        if (value.hasMember("count")) {
            return withCount(stack, parsePositiveInt(value.getMember("count"), "count"));
        }
        return stack;
    }

    private static ItemStack idToItemStack(ResourceLocation id, int count) {
        if (id.getPath().equals("air")) return ItemStack.EMPTY;
        // 1.12.2: ForgeRegistries.ITEMS.getValue(id) 替代 BuiltInRegistries.ITEM.getOptional(id)
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            throw new ValueConversionException(ItemStack.class, "registered item id", id,
                "Item not found: " + id);
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        // 1.12.2: new ItemStack(item, count) 替代 item.getDefaultInstance() + setCount
        return new ItemStack(item, count);
    }

    private static ItemStack itemToItemStack(Item item, int count) {
        ResourceLocation airId = new ResourceLocation("minecraft", "air");
        if (ForgeRegistries.ITEMS.containsKey(airId) && item == ForgeRegistries.ITEMS.getValue(airId))
            return ItemStack.EMPTY;
        // 1.12.2: new ItemStack(item, count) 替代 item.getDefaultInstance() + setCount
        return new ItemStack(item, count);
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
        String trimmed = raw.trim();
        return trimmed.isEmpty() || trimmed.equals("-") || trimmed.equals("empty")
            || trimmed.equals("minecraft:empty") || trimmed.equals("air") || trimmed.equals("minecraft:air");
    }
}
