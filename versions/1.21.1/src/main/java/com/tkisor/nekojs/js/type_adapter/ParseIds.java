// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.resources.ResourceLocation;

/**
 * 物品 / 方块 / 物品栈共用的 id 解析公共逻辑，消除 Block/Item/ItemStack 三处重复。
 *
 * <p>统一规则：{@code trim} → 缺省补 {@code minecraft:} 前缀 → 拒绝 {@code #} 前缀（tag id）→
 * {@link ResourceLocation#tryParse(String)}，失败抛 {@link ValueConversionException}。
 */
public final class ParseIds {
    private ParseIds() {}

    /** 解析物品/方块 id；空串返回 {@code minecraft:air}。 */
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

    /** 解析正整数计数；非法抛 {@link ValueConversionException}。 */
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
