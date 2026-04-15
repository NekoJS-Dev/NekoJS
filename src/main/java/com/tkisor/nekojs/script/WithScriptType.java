package com.tkisor.nekojs.script;

/**
 * @author ZZZank
 */
public interface WithScriptType {

    ScriptType scriptType();

    default boolean canApplyOn(ScriptType type) {
        var target = scriptType();
        // 'common' objects can be applied on any script type
        return target == ScriptType.COMMON || target == type;
    }
}
