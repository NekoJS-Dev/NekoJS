package com.tkisor.nekojs.api.capability;

/**
 * The three-state result for a contract capability.
 *
 * <ul>
 *   <li>{@link #SUPPORTED}: the capability is fully available under the declared conditions;</li>
 *   <li>{@link #PARTIAL}: the capability is partly available under the declared conditions (the
 *       contract docs describe the differences);</li>
 *   <li>{@link #UNAVAILABLE}: the capability is not available under the declared conditions and
 *       must be recorded explicitly and rejected or degraded, never silently no-op'd
 *       ({@code CapabilityResolver} reports it as {@code DECLARED_UNAVAILABLE}, even when a
 *       provider looks usable).</li>
 * </ul>
 *
 * <p>Contract fixtures keep the declaration in line with real external behavior: a SUPPORTED or
 * PARTIAL declaration must resolve to a provider under its declared conditions, otherwise the
 * result also lands in {@code unavailable} and nothing is activated.
 */
public enum CapabilityStatus {
    SUPPORTED,
    PARTIAL,
    UNAVAILABLE
}
