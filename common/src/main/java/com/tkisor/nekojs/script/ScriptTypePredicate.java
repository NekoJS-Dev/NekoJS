package com.tkisor.nekojs.script;

import java.util.function.Predicate;

public interface ScriptTypePredicate extends Predicate<ScriptType> {
    static ScriptTypePredicate any() {
        return type -> true;
    }

    static ScriptTypePredicate exact(ScriptType type) {
        return candidate -> candidate == type;
    }

    default boolean canApplyOn(ScriptType type) {
        return test(type);
    }
}
