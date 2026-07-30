package com.tkisor.nekojs.api.module;

public enum InactiveReason {
    SCOPE_MISMATCH,
    CAPABILITY_UNAVAILABLE,
    MISSING_MODULE_DEPENDENCY,
    DEPENDENCY_INACTIVE,
    MODULE_VERSION_MISMATCH
}
