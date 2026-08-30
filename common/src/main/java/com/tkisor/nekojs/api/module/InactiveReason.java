package com.tkisor.nekojs.api.module;

/**
 * 模块未激活的原因。
 */
public enum InactiveReason {
    /** 作用域不匹配。 */
    SCOPE_MISMATCH,
    /** 所需能力不可用。 */
    CAPABILITY_UNAVAILABLE,
    /** 缺少模块依赖。 */
    MISSING_MODULE_DEPENDENCY,
    /** 依赖的模块未激活。 */
    DEPENDENCY_INACTIVE,
    /** 模块版本不匹配。 */
    MODULE_VERSION_MISMATCH
}
