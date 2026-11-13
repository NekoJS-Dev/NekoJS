package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for ItemStack, limited to the core subset.
 *
 * <p>It holds only methods CR can also implement and that have no native collision. Each
 * platform's {@code ItemStackExtension} must {@code extends ItemStackSpec}.
 *
 * <p><b>Excluded to avoid collisions:</b> these methods are not in the spec because their native
 * no-arg counterparts collide:
 * <ul>
 *   <li>{@code copy()} / {@code getItem()} — both platforms have a no-arg method of the same name
 *       with the same behavior, so JS uses the native one
 *   <li>{@code setCount(int)} — both platforms have it; chained extension uses the platform-unique
 *       name {@code setCountAndReturn}
 *   <li>{@code enchant(...)} / {@code isEnchanted()} — vanilla already has an entry point of the
 *       same name; enchanting by id and the NekoJS glow semantics use platform-unique names
 * </ul>
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface ItemStackSpec {

    /** Returns the ItemStack-owned persistent data wrapper; this data is local to the stack. */
    default Object neko$pdata() {
        throw new UnsupportedOperationException("ItemStackSpec.neko$pdata not implemented");
    }

    /** Returns a copy with the given count; the original ItemStack is not modified. */
    default Object neko$withCount(int count) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$withCount not implemented");
    }

    /** Registry id of the item in this stack (for example {@code minecraft:stone}). */
    default String neko$getId() {
        throw new UnsupportedOperationException("ItemStackSpec.neko$getId not implemented");
    }

    /**
     * Whether the stack has the given enchantment at the given level. enchId is like
     * "minecraft:sharpness".
     */
    default boolean neko$hasEnchantment(String enchId, int level) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$hasEnchantment not implemented");
    }

    /** Whether the stack is unbreakable. */
    default boolean neko$isUnbreakable() {
        throw new UnsupportedOperationException("ItemStackSpec.neko$isUnbreakable not implemented");
    }

    /** Sets whether the stack is unbreakable. */
    default void neko$setUnbreakable(boolean value) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$setUnbreakable not implemented");
    }

    /** Whether this stack matches another ItemStack (same item type). */
    default boolean neko$matches(Object other) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$matches not implemented");
    }
}
