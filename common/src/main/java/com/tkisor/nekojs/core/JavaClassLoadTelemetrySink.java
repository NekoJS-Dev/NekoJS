package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;

/**
 * Receiver of Java class-load telemetry observations.
 *
 * <p>Privacy scope of the observed fields (ticket 30): implementations receive the script
 * type, the script id (engine-internal authored path identity such as
 * {@code server_scripts/foo.js}), the engine-side Java class name and the allow decision.
 * Script contents, absolute user paths and environment information are never part of the
 * observation surface. Implementations are expected to bound what they retain; see
 * {@link JavaClassLoadTelemetryRecorder} for the reference bounded implementation.
 */
public interface JavaClassLoadTelemetrySink {
    JavaClassLoadTelemetrySink EMPTY = new JavaClassLoadTelemetrySink() {};

    default void recordAttempt(ScriptType scriptType, String scriptId, String className, boolean allowed) {}

    default void recordLoad(ScriptType scriptType, String scriptId, String className) {}
}
