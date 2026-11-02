package com.tkisor.nekojs.js.type_adapter;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

/** Round-trips item IDs, counts and component patches using vanilla component codecs. */
public final class ItemStackStrings {
    private static final Pattern COUNT = Pattern.compile("^(\\d+)x\\s*(.+)$", Pattern.DOTALL);

    private ItemStackStrings() {}

    public static ItemStack parse(String text, HolderLookup.Provider registries) {
        int count = 1;
        String input = text.trim();
        var match = COUNT.matcher(input);
        if (match.matches()) {
            count = ParseIds.parsePositiveCount(match.group(1));
            input = match.group(2);
        }
        var reader = new StringReader(input);
        try {
            var parsed = new ItemParser(registries).parse(reader);
            reader.skipWhitespace();
            if (reader.canRead()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
            }
            // Preserve the existing factory's support for counts above the natural stack limit.
            return new ItemStack(parsed.item(), count, parsed.components());
        } catch (CommandSyntaxException exception) {
            throw new ValueConversionException(ItemStack.class, "item ID with component patch", text,
                    "[NEKO-4054] Invalid item component string: " + exception.getMessage(), exception);
        }
    }

    public static String format(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return "minecraft:air";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var components = new ArrayList<String>();
        for (var entry : stack.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            String key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString();
            if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
            components.add(entry.getValue().isEmpty() ? "!" + key
                    : key + "=" + encode(type, entry.getValue().orElseThrow(), registries));
        }
        Collections.sort(components);
        return (stack.getCount() > 1 ? stack.getCount() + "x " : "") + id
                + (components.isEmpty() ? "" : "[" + String.join(",", components) + "]");
    }

    private static <T> Tag encode(DataComponentType<T> type, Object value, HolderLookup.Provider registries) {
        @SuppressWarnings("unchecked") T component = (T) value;
        return type.codecOrThrow().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), component)
                .getOrThrow(message -> new ValueConversionException(ItemStack.class, "serializable component patch", type,
                        "[NEKO-4054] Cannot encode item component string: " + message));
    }
}
