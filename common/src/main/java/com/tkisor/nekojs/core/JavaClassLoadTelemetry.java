package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.catalog.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.script.ScriptType;

public final class JavaClassLoadTelemetry {
    private static volatile JavaClassLoadTelemetrySink sink = JavaClassLoadTelemetrySink.EMPTY;
    private static final ThreadLocal<ContextInfo> CURRENT = new ThreadLocal<>();

    public JavaClassLoadTelemetry() {}

    public static void setSink(JavaClassLoadTelemetrySink newSink) {
        sink = newSink == null ? JavaClassLoadTelemetrySink.EMPTY : newSink;
    }

    public static boolean isEnabled() {
        return sink != JavaClassLoadTelemetrySink.EMPTY;
    }

    public static void enter(ScriptType scriptType, String scriptId) {
        CURRENT.set(new ContextInfo(scriptType, scriptId));
    }

    public static void exit() {
        CURRENT.remove();
    }

    public static void recordAttempt(String className, boolean allowed) {
        ContextInfo info = CURRENT.get();
        if (info != null) {
            sink.recordAttempt(info.scriptType(), info.scriptId(), className, allowed);
        }
    }

    public void recordLoad(String scriptTypeName, String scriptId, String className) {
        ScriptType scriptType = ScriptType.valueOf(scriptTypeName);
        sink.recordLoad(scriptType, scriptId, className);
    }

    private record ContextInfo(ScriptType scriptType, String scriptId) {}
}
