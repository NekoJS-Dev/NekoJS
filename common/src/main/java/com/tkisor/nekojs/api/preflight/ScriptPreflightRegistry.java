package com.tkisor.nekojs.api.preflight;

/** Registration facade available only while the preflight extension point is collecting. */
@FunctionalInterface
public interface ScriptPreflightRegistry {
    void register(ScriptPreflight provider);
}
