package com.tkisor.nekojs.api.contract;

/**
 * Kinds of API contract, separating how stable a contract is and where it applies.
 *
 * <p>Main code branches explicitly on these kinds (see {@code JsApiSurfaceResolver} and
 * {@code VerifiedContractSet}):
 * <ul>
 *   <li>{@link #PORTABLE}: exactly one stable cross-platform contract per owner, checked by
 *       {@code requirePortable}; its hash becomes {@code ApiContractHashes.portableContractHash};</li>
 *   <li>{@link #FEATURE} / {@link #ADDON}: feature and addon extension contracts, whose
 *       compatibility hashes go into {@code ApiContractHashes.moduleContractHashes} for module
 *       resolution to compare.</li>
 * </ul>
 * {@link #PLATFORM} and {@link #SPI} are part of the layering model and have no separate branch
 * logic yet.
 */
public enum ApiContractKind {
    /** Cross-platform portable contract (exactly one per owner). */
    PORTABLE,
    /** Platform feature contract (contributes to the module contract hash). */
    FEATURE,
    /** Platform-specific contract. */
    PLATFORM,
    /** Addon extension contract (contributes to the module contract hash). */
    ADDON,
    /** Service provider interface (SPI) contract. */
    SPI
}
