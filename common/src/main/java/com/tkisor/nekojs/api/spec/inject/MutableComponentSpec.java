package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

import org.jetbrains.annotations.Nullable;

/**
 * Cross-platform extension specification for MutableComponent. NF_ONLY: CR 1.12.2 has no matching
 * extension for its ITextComponent yet.
 *
 * <p>Every NF platform's {@code MutableComponentExtension} must {@code extends MutableComponentSpec}.
 *
 * <p><b>Covered methods:</b> color, style (bold/italic/underlined/strikethrough/obfuscated),
 * insertion, hasStyle and font. They have the same signatures on NF26 and NF1.21.1. Font takes a
 * String on both because the JS side already passes a String, and the platform impl resolves it to
 * {@code FontDescription.Resource} or {@code ResourceLocation} internally.
 *
 * <p><b>Not</b> in this spec (their parameters use MC types that differ by version; they stay
 * host-side conveniences and are unavailable from JS):
 * <ul>
 *   <li>{@code color(@Nullable TextColor)}: TextColor is an MC type
 *   <li>{@code click(@Nullable ClickEvent)} / {@code hover(@Nullable HoverEvent)}: these events
 *       are MC types
 *   <li>{@code clickSuggestCommand/clickCopy/clickOpenUrl/clickOpenFile/clickChangePage}:
 *       same parameter signatures, but the bodies diverge because ClickEvent is constructed
 *       differently on each MC version; kept as host-side conveniences
 *   <li>{@code hoverText/hoverItem/hoverEntity}: same reason, HoverEvent construction differs
 * </ul>
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface MutableComponentSpec {

    default boolean neko$hasStyle() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$hasStyle not implemented");
    }

    default Object neko$black() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$black not implemented");
    }

    default Object neko$darkBlue() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$darkBlue not implemented");
    }

    default Object neko$darkGreen() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$darkGreen not implemented");
    }

    default Object neko$darkAqua() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$darkAqua not implemented");
    }

    default Object neko$darkRed() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$darkRed not implemented");
    }

    default Object neko$darkPurple() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$darkPurple not implemented");
    }

    default Object neko$gold() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$gold not implemented");
    }

    default Object neko$gray() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$gray not implemented");
    }

    default Object neko$darkGray() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$darkGray not implemented");
    }

    default Object neko$blue() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$blue not implemented");
    }

    default Object neko$green() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$green not implemented");
    }

    default Object neko$aqua() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$aqua not implemented");
    }

    default Object neko$red() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$red not implemented");
    }

    default Object neko$lightPurple() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$lightPurple not implemented");
    }

    default Object neko$yellow() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$yellow not implemented");
    }

    default Object neko$white() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$white not implemented");
    }

    default Object neko$noColor() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$noColor not implemented");
    }

    default Object neko$bold(@Nullable Boolean value) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$bold not implemented");
    }

    default Object neko$bold() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$bold not implemented");
    }

    default Object neko$italic(@Nullable Boolean value) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$italic not implemented");
    }

    default Object neko$italic() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$italic not implemented");
    }

    default Object neko$underlined(@Nullable Boolean value) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$underlined not implemented");
    }

    default Object neko$underlined() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$underlined not implemented");
    }

    default Object neko$strikethrough(@Nullable Boolean value) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$strikethrough not implemented");
    }

    default Object neko$strikethrough() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$strikethrough not implemented");
    }

    default Object neko$obfuscated(@Nullable Boolean value) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$obfuscated not implemented");
    }

    default Object neko$obfuscated() {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$obfuscated not implemented");
    }

    default Object neko$insertion(@Nullable String insertion) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$insertion not implemented");
    }

    /**
     * Sets the font. {@code font} is a resource id string (such as {@code minecraft:default});
     * each platform impl resolves it to its native type (NF26: {@code FontDescription.Resource},
     * NF1.21.1: {@code ResourceLocation}).
     */
    default Object neko$font(@Nullable String font) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$font not implemented");
    }
}
