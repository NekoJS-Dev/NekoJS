package com.tkisor.nekojs.api.module;

/**
 * Why a module was not activated.
 */
public enum InactiveReason {
    /** The module's scope does not match. */
    SCOPE_MISMATCH,
    /** A required capability is not available. */
    CAPABILITY_UNAVAILABLE,
    /** A module dependency is missing. */
    MISSING_MODULE_DEPENDENCY,
    /** A module it depends on was not activated. */
    DEPENDENCY_INACTIVE,
    /** The module version does not match. */
    MODULE_VERSION_MISMATCH
}
