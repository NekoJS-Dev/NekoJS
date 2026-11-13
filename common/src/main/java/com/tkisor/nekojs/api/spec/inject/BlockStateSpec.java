package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for BlockState (NF_ONLY: CR 1.12.2 has no matching extension
 * for {@code IBlockState} yet).
 *
 * <p>Each NF platform's {@code BlockStateExtension} must {@code extends BlockStateSpec}.
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface BlockStateSpec {

    /** Whether the block state belongs to the given tag (for example {@code minecraft:mineable/pickaxe}). */
    default boolean neko$hasTag(String tagLocation) {
        throw new UnsupportedOperationException("BlockStateSpec.neko$hasTag not implemented");
    }

    /**
     * Whether the block matches the given id or tag: a {@code #} prefix means a tag, anything else
     * must equal the block id exactly.
     */
    default boolean neko$is(String idOrTag) {
        throw new UnsupportedOperationException("BlockStateSpec.neko$is not implemented");
    }

    /** Block id (for example {@code minecraft:stone}). */
    default String neko$getId() {
        throw new UnsupportedOperationException("BlockStateSpec.neko$getId not implemented");
    }
}
