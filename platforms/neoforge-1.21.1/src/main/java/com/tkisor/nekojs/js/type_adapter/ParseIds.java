package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.resources.ResourceLocation;

/**
 * 物品/方块 id 解析公共逻辑，供 {@link BlockAdapter}/{@link ItemAdapter}/{@link ItemStackAdapter}
 * 复用，统一以下规则：
 * <ul>
 *   <li>trim；</li>
 *   <li>空白 -> 默认 minecraft:air；</li>
 *   <li>以 {@code #} 开头 -> 拒绝（应为 tag id 而非 item/block id）；</li>
 *   <li>无命名空间 -> 补 {@code minecraft:}；</li>
 *   <li>{@link ResourceLocation#tryParse} 失败 -> 抛 {@link ValueConversionException}。</li>
 * </ul>
 */
final class ParseIds {
    private ParseIds() {}

    static ResourceLocation parseItemOrBlockId(String raw) {
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
