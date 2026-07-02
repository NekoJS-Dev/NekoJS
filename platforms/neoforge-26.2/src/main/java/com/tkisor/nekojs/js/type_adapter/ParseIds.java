package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.resources.Identifier;

/**
 * 物品 / 方块 / 物品栈共用的 id 解析公共逻辑，消除 Block/Item/ItemStack 三处重复。
 *
 * <p>统一规则：{@code trim} → 缺省补 {@code minecraft:} 前缀 → 拒绝 {@code #} 前缀（tag id）→
 * {@link Identifier#tryParse(String)}，失败抛 {@link ValueConversionException}。
 */
final class ParseIds {
    private ParseIds() {}

    /** 解析物品/方块 id；空串返回 {@code minecraft:air}。 */
    static Identifier parseItemOrBlockId(String raw) {
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

    /** 解析正整数计数；非法抛 {@link ValueConversionException}。 */
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
