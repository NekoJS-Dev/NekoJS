// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared id parsing for items, blocks and item stacks, so Block, Item and ItemStack no longer each
 * carry their own copy.
 *
 * <p>The rules are the same everywhere: {@code trim}, add the {@code minecraft:} prefix when it is
 * missing, reject a {@code #} prefix because that is a tag id, then call
 * {@link ResourceLocation#tryParse(String)}; if that fails, throw {@link ValueConversionException}.
 */
public final class ParseIds {
    private ParseIds() {}

    /** Parses an item or block id; an empty string returns {@code minecraft:air}. */
    public static ResourceLocation parseItemOrBlockId(String raw) {
        if (raw == null || raw.isBlank()) return ResourceLocation.withDefaultNamespace("air");
        String id = raw.trim();
        if (id.startsWith("#")) {
            throw new ValueConversionException(ResourceLocation.class, "item/block id (not tag)", raw,
                "expected item/block id but got tag id: " + raw);
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new ValueConversionException(ResourceLocation.class, "valid item/block id", raw,
                "invalid item/block id: " + raw);
        }
        return location;
    }

    /** Parses a positive integer count; throws {@link ValueConversionException} when the text is not one. */
    static int parsePositiveCount(String raw) {
        int count;
        try {
            count = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValueConversionException(Integer.class, "integer", raw,
                "count must be an integer: " + raw, e);
        }
        if (count <= 0) {
            throw new ValueConversionException(Integer.class, "positive integer", count,
                "count must be positive: " + count);
        }
        return count;
    }
}
