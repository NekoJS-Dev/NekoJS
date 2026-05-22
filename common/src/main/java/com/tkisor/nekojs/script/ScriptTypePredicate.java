package com.tkisor.nekojs.script;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface ScriptTypePredicate extends Predicate<ScriptType> {
    static ScriptTypePredicate any() {
        return type -> true;
    }

    static ScriptTypePredicate exact(ScriptType type) {
        return candidate -> candidate == type;
    }

    @Override
    @NonNull
    default ScriptTypePredicate negate() {
        return type -> !test(type);
    }

    default Stream<ScriptType> streamMatched() {
        return Arrays.stream(ScriptType.values()).filter(this);
    }

    default boolean canApplyOn(ScriptType type) {
        return test(type);
    }
}
