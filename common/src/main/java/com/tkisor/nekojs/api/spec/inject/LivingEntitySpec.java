package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for LivingEntity.
 *
 * <p>Each platform's {@code LivingEntityExtension} must {@code extends LivingEntitySpec} and
 * provide a platform-specific {@code default} implementation of every {@code neko$} method. The
 * {@code default} bodies in this interface throw {@link UnsupportedOperationException}, so a method
 * a platform extension fails to override reports a clear error at runtime.
 *
 * <p><b>Excluded to avoid collisions:</b> these methods are <b>not</b> in this spec because they
 * collide with a no-arg method of the same name on the native {@code LivingEntity} /
 * {@code EntityLivingBase} of both platforms, and Graal cannot dispatch them:
 * <ul>
 *   <li>{@code getHealth()} / {@code setHealth(float)} / {@code getMaxHealth()} / {@code heal(float)}
 *       — both platforms have a native method of the same name with the same behavior, so JS uses
 *       the native one.
 *   <li>{@code getMainHandItem()} — NF has a native no-arg method of the same name (CR does not
 *       collide, but cross-platform consistency requires excluding it).
 * </ul>
 *
 * <p><b>Parameter and return types:</b> equipment getters return {@code Object} (the platform's
 * native ItemStack), and potion effects are addressed by string id. The spec names no concrete MC
 * type, which keeps {@code common} free of Minecraft classes.
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface LivingEntitySpec {

    /** Deals damage to this entity from a generic damage source. */
    default void neko$damage(float amount) {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$damage not implemented");
    }

    /** Returns the item in the off hand. */
    default Object neko$getOffHandItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getOffHandItem not implemented");
    }

    /** Returns the head equipment item. */
    default Object neko$getHeadItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getHeadItem not implemented");
    }

    /** Returns the chest equipment item. */
    default Object neko$getChestItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getChestItem not implemented");
    }

    /** Returns the legs equipment item. */
    default Object neko$getLegsItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getLegsItem not implemented");
    }

    /** Returns the feet equipment item. */
    default Object neko$getFeetItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getFeetItem not implemented");
    }

    /**
     * Adds a potion effect by string id.
     *
     * @param effectId       potion id, such as {@code "minecraft:speed"} or {@code "speed"}
     * @param duration       duration in ticks
     * @param amplifier      amplifier (0 = level I)
     * @return {@code true} if the effect exists and was added
     */
    default boolean neko$addEffect(String effectId, int duration, int amplifier) {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$addEffect not implemented");
    }

    /**
     * Removes a potion effect by string id.
     *
     * @param effectId potion id
     * @return {@code true} if the effect exists and was removed
     */
    default boolean neko$removeEffect(String effectId) {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$removeEffect not implemented");
    }
}
