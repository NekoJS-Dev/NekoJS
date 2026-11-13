package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for Entity.
 *
 * <p>Each platform's {@code EntityExtension} must {@code extends EntitySpec}.
 *
 * <p><b>Excluded to avoid collisions:</b> these methods are <b>not</b> in this spec because they
 * collide with a no-arg method of the same name on the native NF {@code Entity}, and Graal cannot
 * dispatch them:
 * <ul>
 *   <li>{@code getId()} — NF has a native {@code int getId()}, while the neko version returns a
 *       registry id string. Each platform renames it with {@code @Remap("getRegistryId")} to avoid
 *       the collision.
 *   <li>{@code getX()/getY()/getZ()} — NF has a native no-arg {@code double getX()} with the same
 *       behavior. NF/NF121 drop the injected neko version (JS calls the native one); CR has no
 *       native getters, so it keeps the neko version.
 * </ul>
 *
 * <p>{@link #neko$getLevel()} returns {@code Object} (NF returns {@code Level}, CR returns
 * {@code World}). The spec names no concrete type because {@code common} must stay free of
 * Minecraft classes. Neither platform has a native {@code getLevel()} method (NF uses
 * {@code level()}, CR uses {@code getEntityWorld()}), so there is no collision.
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface EntitySpec {

    /** Dimension the entity is in. NF returns Level, CR returns World. */
    default Object neko$getLevel() {
        throw new UnsupportedOperationException("EntitySpec.neko$getLevel not implemented");
    }

    /** Removes or kills the entity. NF calls kill(ServerLevel), CR calls setDead(). */
    default boolean neko$kill() {
        throw new UnsupportedOperationException("EntitySpec.neko$kill not implemented");
    }

    /** Teleports the entity to the given coordinates in the same world. */
    default void neko$teleport(double x, double y, double z) {
        throw new UnsupportedOperationException("EntitySpec.neko$teleport not implemented");
    }

    /** Removes the entity (like kill but returns void). NF calls discard(), CR calls setDead(). */
    default void neko$remove() {
        throw new UnsupportedOperationException("EntitySpec.neko$remove not implemented");
    }
}
