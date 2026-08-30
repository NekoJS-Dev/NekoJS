package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * BlockState 跨平台统一扩展规范（NF_ONLY——CR 1.12.2 的 IBlockState 暂未实现对应扩展）。
 *
 * <p>各 NF 平台的 {@code BlockStateExtension} 必须 {@code extends BlockStateSpec}。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface BlockStateSpec {

    /** 当前方块状态是否属于给定标签（如 {@code minecraft:mineable/pickaxe}）。 */
    default boolean neko$hasTag(String tagLocation) {
        throw new UnsupportedOperationException("BlockStateSpec.neko$hasTag not implemented");
    }

    /**
     * 判断当前方块是否匹配给定 id 或标签：以 {@code #} 前缀表示标签，否则按方块 id 精确匹配。
     */
    default boolean neko$is(String idOrTag) {
        throw new UnsupportedOperationException("BlockStateSpec.neko$is not implemented");
    }

    /** 方块 id（如 {@code minecraft:stone}）。 */
    default String neko$getId() {
        throw new UnsupportedOperationException("BlockStateSpec.neko$getId not implemented");
    }
}
