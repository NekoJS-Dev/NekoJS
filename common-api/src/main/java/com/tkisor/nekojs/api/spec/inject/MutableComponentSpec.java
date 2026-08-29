package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

import org.jetbrains.annotations.Nullable;

/**
 * MutableComponent 跨平台统一扩展规范（NF_ONLY——CR 1.12.2 的 ITextComponent 暂未实现对应扩展）。
 *
 * <p>各 NF 平台的 {@code MutableComponentExtension} 必须 {@code extends MutableComponentSpec}。
 *
 * <p><b>覆盖范围</b>：颜色、样式（bold/italic/underlined/strikethrough/obfuscated）、insertion、
 * hasStyle、font。这些方法在 NF26/NF1.21.1 上签名一致（font 统一为 String——JS 层已是 String，
 * 平台 impl 内部解析为 {@code FontDescription.Resource} 或 {@code ResourceLocation}）。
 *
 * <p><b>不</b>在本 spec 中的方法（参数含 MC 版本特定类型，host-side 便利，JS 层不可用）：
 * <ul>
 *   <li>{@code color(@Nullable TextColor)} —— TextColor 是 MC 类型
 *   <li>{@code click(@Nullable ClickEvent)} / {@code hover(@Nullable HoverEvent)} —— ClickEvent/HoverEvent 是 MC 类型
 *   <li>{@code clickSuggestCommand/clickCopy/clickOpenUrl/clickOpenFile/clickChangePage} ——
 *       参数签名一致但内部 body 因 MC 版本 ClickEvent 构造差异而分歧；留作 host-side 便利
 *   <li>{@code hoverText/hoverItem/hoverEntity} —— 同上，HoverEvent 构造差异
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
     * 设置字体。{@code font} 是资源 id 字符串（如 {@code minecraft:default}），
     * 各平台 impl 内部解析为原生类型（NF26: {@code FontDescription.Resource}，NF1.21.1: {@code ResourceLocation}）。
     */
    default Object neko$font(@Nullable String font) {
        throw new UnsupportedOperationException("MutableComponentSpec.neko$font not implemented");
    }
}
