package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;

public interface JavaClassLoadTelemetrySink {
    JavaClassLoadTelemetrySink EMPTY = new JavaClassLoadTelemetrySink() {};

    default void recordAttempt(ScriptType scriptType, String scriptId, String className, boolean allowed) {}

    default void recordLoad(ScriptType scriptType, String scriptId, String className) {}
}
