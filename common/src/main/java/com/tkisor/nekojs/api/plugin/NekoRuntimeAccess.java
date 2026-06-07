package com.tkisor.nekojs.api.plugin;

/**
 * Static accessor for the current {@link IPluginRuntime}.
 * Set once during bootstrap by the platform; read by api-layer consumers
 * that previously depended on {@code core.plugin.NekoPluginRuntime} directly.
 */
public final class NekoRuntimeAccess {
    private static volatile IPluginRuntime runtime;

    private NekoRuntimeAccess() {}

    public static void set(IPluginRuntime rt) {
        if (runtime != null) {
            throw new IllegalStateException("NekoRuntimeAccess already initialized");
        }
        runtime = rt;
    }

    public static IPluginRuntime get() {
        if (runtime == null) {
            throw new IllegalStateException("NekoRuntimeAccess not initialized — has bootstrap run?");
        }
        return runtime;
    }
}
