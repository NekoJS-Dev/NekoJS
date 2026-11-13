package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension specification for Player.
 *
 * <p>Each platform's {@code PlayerExtension} must {@code extends PlayerSpec} and override each
 * {@code neko$} method with its own {@code default} implementation. The {@code default} bodies in
 * this interface throw {@link UnsupportedOperationException}, so an extension that misses a method
 * fails at runtime, and {@code SpecCoverageProcessor} reports it at compile time.
 *
 * <p><b>Semantics that must not change:</b>
 * <ul>
 *   <li>{@link #neko$addXpLevels(int)} adds levels, not experience points.
 *       NF implements {@code giveExperienceLevels}, CR implements {@code addExperienceLevel}.
 *   <li>{@link #neko$addXpPoints(int)} adds experience points, not levels.
 *       NF implements {@code giveExperiencePoints}, CR implements {@code addExperience}.
 * </ul>
 * There is no combined {@code neko$addXp(int)} because the name means different things on the two
 * platforms (NF = Levels, CR = Points).
 *
 * <p><b>Excluded methods:</b> the following are <b>not</b> in this spec because their zero-argument
 * names collide with native {@code Player} methods; Graal cannot dispatch such a call and throws
 * "Multiple applicable overloads":
 * <ul>
 *   <li>{@code isCreative()}: both platforms already have a native zero-argument
 *       {@code isCreative()} with the same behavior, so JS calls that directly.
 *   <li>{@code sendMessage(Object)}: CR has a native {@code sendMessage(ITextComponent)},
 *       which collides at zero arguments.
 * </ul>
 *
 * <p><b>Parameter types:</b> {@code give(Object)} takes the platform-native ItemStack and Graal
 * converts it on the JS side. The spec names no MC types to stay within the common-api
 * constraints.
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface PlayerSpec {

    /** Returns whether this player is a server operator. */
    default boolean neko$isOp() {
        throw new UnsupportedOperationException("PlayerSpec.neko$isOp not implemented");
    }

    /** Gives an item to the player's inventory (drops if full). The argument is a platform-native ItemStack. */
    default void neko$give(Object stack) {
        throw new UnsupportedOperationException("PlayerSpec.neko$give not implemented");
    }

    /** Sets the game mode: survival, creative, adventure, or spectator. */
    default void neko$setGamemode(String gamemode) {
        throw new UnsupportedOperationException("PlayerSpec.neko$setGamemode not implemented");
    }

    /** Returns the current game mode name. */
    default String neko$getGamemode() {
        throw new UnsupportedOperationException("PlayerSpec.neko$getGamemode not implemented");
    }

    /** Adds experience [Levels] (not points). Platforms must implement this as adding levels. */
    default void neko$addXpLevels(int levels) {
        throw new UnsupportedOperationException("PlayerSpec.neko$addXpLevels not implemented");
    }

    /** Adds experience [Points] (not levels). Platforms must implement this as adding experience points. */
    default void neko$addXpPoints(int points) {
        throw new UnsupportedOperationException("PlayerSpec.neko$addXpPoints not implemented");
    }

    /** Returns the current experience level. */
    default int neko$getXpLevel() {
        throw new UnsupportedOperationException("PlayerSpec.neko$getXpLevel not implemented");
    }

    /** Sets the experience level. */
    default void neko$setXpLevel(int level) {
        throw new UnsupportedOperationException("PlayerSpec.neko$setXpLevel not implemented");
    }

    /** Kicks the player. 'reason' accepts a String or a platform-native Component. */
    default void neko$kick(Object reason) {
        throw new UnsupportedOperationException("PlayerSpec.neko$kick not implemented");
    }
}
