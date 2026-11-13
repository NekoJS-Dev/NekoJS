package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for Level (NF_ONLY: CR 1.12.2 has no matching extension for
 * {@code World} yet).
 *
 * <p>Each NF platform's {@code LevelExtension} must {@code extends LevelSpec}.
 *
 * <p><b>Return types</b>: methods declared to return {@code Object} (getBlockState/getPlayers/data)
 * are overridden with a covariant return type on each platform (NF returns {@code BlockState} /
 * {@code List<? extends Player>} / {@code AttachedData<Level>}). The spec names no concrete type,
 * which keeps {@code common} free of Minecraft classes.
 *
 * <p>These methods are <b>not</b> in the spec: they are host-side conveniences whose parameters
 * include MC-version-specific types, so the JS layer cannot use them:
 * <ul>
 *   <li>{@code spawnEntity(EntityType, ...)} — EntityType is an MC type
 *   <li>{@code spawnLightning(...)} — delegates to spawnEntity
 * </ul>
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface LevelSpec {

    /** Block state at the given coordinates. */
    default Object neko$getBlockState(int x, int y, int z) {
        throw new UnsupportedOperationException("LevelSpec.neko$getBlockState not implemented");
    }

    /** Dimension id (for example "minecraft:overworld"). */
    default String neko$getId() {
        throw new UnsupportedOperationException("LevelSpec.neko$getId not implemented");
    }

    /**
     * Sets the block at the given coordinates. {@code block} can be a BlockState, a Block or a block
     * id string.
     *
     * @return whether the block was actually changed
     */
    default boolean neko$setBlock(int x, int y, int z, Object block) {
        throw new UnsupportedOperationException("LevelSpec.neko$setBlock not implemented");
    }

    /** Day time of the world. */
    default long neko$getTime() {
        throw new UnsupportedOperationException("LevelSpec.neko$getTime not implemented");
    }

    /** Sets the day time; only takes effect on the server world. */
    default void neko$setTime(long time) {
        throw new UnsupportedOperationException("LevelSpec.neko$setTime not implemented");
    }

    /** All players currently in the world. */
    default Object neko$getPlayers() {
        throw new UnsupportedOperationException("LevelSpec.neko$getPlayers not implemented");
    }

    /** Whether it is raining. */
    default boolean neko$isRaining() {
        throw new UnsupportedOperationException("LevelSpec.neko$isRaining not implemented");
    }

    /** Sets the rain state; only takes effect on the server world. */
    default void neko$setRaining(boolean raining) {
        throw new UnsupportedOperationException("LevelSpec.neko$setRaining not implemented");
    }

    /** Whether it is daytime. */
    default boolean neko$isDay() {
        throw new UnsupportedOperationException("LevelSpec.neko$isDay not implemented");
    }

    /** In-memory data container attached to this level. */
    default Object neko$data() {
        throw new UnsupportedOperationException("LevelSpec.neko$data not implemented");
    }
}
