package com.tkisor.nekojs.script;

/**
 * @author ZZZank
 */
public interface WithScriptType {

    ScriptType scriptType();

    default boolean canApplyOn(ScriptType type) {
        return scriptType().test(type);
    }
}
