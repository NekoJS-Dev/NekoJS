package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.util.ResourceLocation;

/**
 * 物品/方块 id 解析公共逻辑，供 {@link BlockAdapter}/{@link ItemAdapter}/{@link ItemStackAdapter}
 * 复用，统一以下规则：
 * <ul>
 *   <li>trim；</li>
 *   <li>空白 -> 默认 minecraft:air；</li>
 *   <li>以 {@code #} 开头 -> 拒绝（应为 tag id 而非 item/block id）；</li>
 *   <li>无命名空间 -> 补 {@code minecraft:}；</li>
 *   <li>{@code new ResourceLocation(id)} 抛异常 -> 抛 {@link ValueConversionException}。</li>
 * </ul>
 *
 * <p>1.12.2 适配：使用 try-catch 替代 tryParse，使用 {@code new ResourceLocation("minecraft", "air")} 替代
 * {@code ResourceLocation.withDefaultNamespace("air")}。</p>
 */
final class ParseIds {
    private ParseIds() {}

    static ResourceLocation parseItemOrBlockId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ResourceLocation("minecraft", "air");
        String id = raw.trim();
        if (id.startsWith("#")) {
            throw new ValueConversionException(ResourceLocation.class, "item/block id (not tag)", raw,
                "expected item/block id but got tag id: " + raw);
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        try {
            return new ResourceLocation(id);
        } catch (Exception e) {
            throw new ValueConversionException(ResourceLocation.class, "valid item/block id", raw,
                "invalid item/block id: " + raw);
        }
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
