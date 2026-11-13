// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.js.type_adapter.ParseIds;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentPatch;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import static com.tkisor.nekojs.api.AdapterInputShape.*;

public final class ItemStackAdapter implements JSTypeAdapter<ItemStack> {

    // Tolerates a missing space ("1xminecraft:stone"), so a Pattern replaces matches() plus indexOf(); the
    // indexOf approach also got it wrong when an x appeared before the namespace.
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
                raw("`${string}[${string}]`"),
                host(Item.class),
                host(NekoId.class),
                object(
                        Slot.opt("item", union(registry("Item"), raw("`${string}[${string}]`"))),
                        Slot.opt("id", union(registry("Item"), raw("`${string}[${string}]`"))),
                        Slot.opt("count", number()),
                        Slot.opt("components", raw("{ [key: string]: any }"))));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("item:id[component=value,!removed] | Nx item:id[components] | item:id | RegistryTypes.Item | $Item | $NekoId | { item?|id?, count?, components? }");
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
            if (obj instanceof NekoId id) return idToItemStack(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()), 1);
        }

        if (value.hasMembers()) return objectToItemStack(value);

        throw new ValueConversionException(ItemStack.class, "item stack value", value, "unsupported item stack value");
    }

    public static ItemStack stringToItemStack(String raw) {
        if (isEmptyStackString(raw)) return ItemStack.EMPTY;

        if (raw.indexOf('[') >= 0) {
            return ItemStackStrings.parse(raw, ItemStackStringRegistries.current());
        }

        String idText = raw.trim();
        int count = 1;
        var m = COUNT_PATTERN.matcher(idText);
        if (m.matches()) {
            count = ParseIds.parsePositiveCount(m.group(1));
            idText = m.group(2);
        }

        // The id part goes through ParseIds, which trims, adds the minecraft: prefix, rejects a tag prefix and
        // parses with tryParse.
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

        // Reuses this adapter instance instead of allocating a new one each time.
        ItemStack stack = this.apply(itemValue);
        if (value.hasMember("components")) {
            DataComponentPatch patch = DataComponentsAdapter.toPatch(value.getMember("components"));
            if (!patch.isEmpty() && !stack.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.applyComponents(patch);
                stack = copy;
            }
        }
        if (value.hasMember("count")) {
            return withCount(stack, parsePositiveInt(value.getMember("count"), "count"));
        }
        return stack;
    }

    private static ItemStack idToItemStack(ResourceLocation id, int count) {
        if (id.getPath().equals("air")) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new ValueConversionException(ItemStack.class, "registered item id", id,
                    "Item not found: " + id));
        ItemStack stack = item.getDefaultInstance();
        stack.setCount(count);
        return stack;
    }

    private static ItemStack itemToItemStack(Item item, int count) {
        if (item == BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace("air")).orElse(null)) return ItemStack.EMPTY;
        ItemStack stack = item.getDefaultInstance();
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
