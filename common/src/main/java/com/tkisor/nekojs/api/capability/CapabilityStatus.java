package com.tkisor.nekojs.api.capability;

/**
 * 契约能力的三态结论（ticket 09 AC4）：与 spec 04 的能力词汇一一对应。
 *
 * <ul>
 *   <li>{@link #SUPPORTED}：能力在声明条件下完全可用；</li>
 *   <li>{@link #PARTIAL}：能力在声明条件下部分可用（差异见契约 docs）；</li>
 *   <li>{@link #UNAVAILABLE}：能力在声明条件下不可用——必须显式记录并拒绝/降级，
 *       不得静默 no-op（{@code CapabilityResolver} 会以 {@code DECLARED_UNAVAILABLE}
 *       显式报告，即使存在看似可用的 provider）。</li>
 * </ul>
 *
 * <p>声明与真实外部行为一致由 contract fixture 守护：SUPPORTED/PARTIAL 声明必须在
 * 声明条件内可解析出 provider，否则解析结果同样显式落 {@code unavailable}（不激活）。
 */
public enum CapabilityStatus {
    SUPPORTED,
    PARTIAL,
    UNAVAILABLE
}
