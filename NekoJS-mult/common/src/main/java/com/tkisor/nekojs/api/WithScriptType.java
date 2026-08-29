package com.tkisor.nekojs.api;

/**
 * @author ZZZank
 */
public interface WithScriptType {

    ScriptTypePredicate scriptType();

    default boolean canApplyOn(ScriptType type) {
        return scriptType().test(type);
    }
}
