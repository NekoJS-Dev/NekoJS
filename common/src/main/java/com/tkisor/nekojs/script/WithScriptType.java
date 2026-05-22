package com.tkisor.nekojs.script;

/**
 * @author ZZZank
 */
public interface WithScriptType {

    ScriptTypePredicate scriptType();

    default boolean canApplyOn(ScriptType type) {
        return scriptType().test(type);
    }
}
