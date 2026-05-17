package com.tkisor.nekojs.core.module.esm;

public enum NekoEsmModuleState {
    NEW,
    LINKING,
    LINKED,
    EVALUATING,
    EVALUATED,
    EVALUATING_ASYNC,
    EVALUATED_ASYNC,
    FAILED
}
