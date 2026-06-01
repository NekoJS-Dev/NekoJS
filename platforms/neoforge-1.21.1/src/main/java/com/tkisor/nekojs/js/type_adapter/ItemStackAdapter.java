package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

public final class ItemStackAdapter implements JSTypeAdapter<ItemStack> {

    @Override
    public Class<ItemStack> getTargetClass() {
        return ItemStack.class;
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

        throw new IllegalArgumentException("Unsupported item stack value: " + value);
    }

    public static ItemStack stringToItemStack(String raw) {
        if (isEmptyStackString(raw)) return ItemStack.EMPTY;

        String idText = raw.trim();
        int count = 1;
        if (idText.matches("^(\\d+)x\\s+(\\S+)$")) {
            int xIndex = idText.indexOf('x');
            count = parsePositiveCount(idText.substring(0, xIndex).trim());
            idText = idText.substring(xIndex + 1).trim();
        }

        if (idText.startsWith("#")) {
            throw new IllegalArgumentException("ItemStack cannot be created from a tag: " + raw);
        }
        if (!idText.contains(":")) {
            idText = "minecraft:" + idText;
        }

        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            throw new IllegalArgumentException("Invalid item id: " + raw);
        }
        return idToItemStack(id, count);
    }

    private static ItemStack objectToItemStack(Value value) {
        if (value.hasMember("tag")) {
            throw new IllegalArgumentException("ItemStack cannot be created from a tag");
        }
        if (value.hasMember("item") && value.hasMember("id")) {
            throw new IllegalArgumentException("ItemStack object cannot contain both 'item' and 'id'");
        }

        Value itemValue;
        if (value.hasMember("item")) {
            itemValue = value.getMember("item");
        } else if (value.hasMember("id")) {
            itemValue = value.getMember("id");
        } else {
            throw new IllegalArgumentException("ItemStack object must contain 'item' or 'id'");
        }

        ItemStack stack = new ItemStackAdapter().apply(itemValue);
        if (value.hasMember("count")) {
            return withCount(stack, parsePositiveInt(value.getMember("count"), "count"));
        }
        return stack;
    }

    private static ItemStack idToItemStack(ResourceLocation id, int count) {
        if (id.getPath().equals("air")) return ItemStack.EMPTY;
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(id);
        if (itemOpt.isEmpty()) {
            throw new IllegalArgumentException("Item not found: " + id);
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
            throw new IllegalArgumentException("ItemStack " + name + " must be an integer");
        }
        return parsePositiveCount(value.asInt());
    }

    private static int parsePositiveCount(String raw) {
        try {
            return parsePositiveCount(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ItemStack count must be an integer: " + raw, e);
        }
    }

    private static int parsePositiveCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("ItemStack count must be positive: " + count);
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
