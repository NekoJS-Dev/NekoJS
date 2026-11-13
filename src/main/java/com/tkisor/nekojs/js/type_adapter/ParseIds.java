// 26.x implementation. This file must not contain version guards again. The 1.21.1 implementation is
// the same-named file under versions/1.21.1/src, and changing behavior here requires updating it too.
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.resources.Identifier;

/**
 * Shared id parsing for items, blocks and item stacks, replacing the duplicated logic in Block, Item
 * and ItemStack.
 *
 * <p>The rules, in order: {@code trim}, add the {@code minecraft:} prefix when missing, reject a
 * {@code #} prefix (a tag id), then {@link Identifier#tryParse(String)}. A parse failure throws
 * {@link ValueConversionException}.
 */
public final class ParseIds {
    private ParseIds() {}

    /** Parses an item or block id; an empty string returns {@code minecraft:air}. */
    public static Identifier parseItemOrBlockId(String raw) {
        if (raw == null || raw.isBlank()) return Identifier.withDefaultNamespace("air");
        String id = raw.trim();
        if (id.startsWith("#")) {
            throw new ValueConversionException(Identifier.class, "item/block id (not tag)", raw,
                "expected item or block id but got tag id");
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new ValueConversionException(Identifier.class, "valid item/block id", raw,
                "invalid id syntax");
        }
        return location;
    }

    /** Parses a positive integer count; throws {@link ValueConversionException} when the value is invalid. */
    static int parsePositiveCount(String raw) {
        int count;
        try {
            count = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValueConversionException(Object.class, "positive integer", raw,
                "count must be an integer", e);
        }
        if (count <= 0) {
            throw new ValueConversionException(Object.class, "positive integer", count,
                "count must be positive");
        }
        return count;
    }
}
