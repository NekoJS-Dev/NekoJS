package com.tkisor.nekojs.script;

/**
 * @author ZZZank
 */
public interface WithScriptType {

    ScriptType scriptType();

    default boolean applicableFor(ScriptType type) {
        return type == ScriptType.COMMON || type == scriptType();
    }
}
