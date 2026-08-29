package com.tkisor.nekojs.api.plugin;

/**
 * Static accessor for the current {@link IPluginRuntime}.
 * Set during bootstrap by the platform; read by api-layer consumers
 * that previously depended on {@code core.plugin.NekoPluginRuntime} directly.
 * A full reload re-bootstraps and replaces the previous runtime.
 */
public final class NekoRuntimeAccess {
    private static volatile IPluginRuntime runtime;

    private NekoRuntimeAccess() {}

    public static void set(IPluginRuntime rt) {
        runtime = java.util.Objects.requireNonNull(rt, "rt");
    }

    public static IPluginRuntime get() {
        if (runtime == null) {
            throw new IllegalStateException("NekoRuntimeAccess not initialized — has bootstrap run?");
        }
        return runtime;
    }
}
