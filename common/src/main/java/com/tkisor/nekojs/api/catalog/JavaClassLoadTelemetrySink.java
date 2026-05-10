package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

public interface JavaClassLoadTelemetrySink {
    JavaClassLoadTelemetrySink EMPTY = new JavaClassLoadTelemetrySink() {};

    default void recordAttempt(ScriptType scriptType, String scriptId, String className, boolean allowed) {}

    default void recordLoad(ScriptType scriptType, String scriptId, String className) {}
}
