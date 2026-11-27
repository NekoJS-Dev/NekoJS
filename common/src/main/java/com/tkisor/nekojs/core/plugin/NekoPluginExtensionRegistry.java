package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.plugin.NekoExtensionPoint;

public interface NekoPluginExtensionRegistry {
    /**
     * Registers one extension point and returns a {@link NekoPluginExtensionHandle} that
     * fetches its product after the bootstrap completes.
     *
     * @throws IllegalStateException    the registration window is closed (the registry has frozen)
     * @throws IllegalArgumentException the extension point id is already used in this bootstrap, or
     *                                  the instance was not produced by
     *                                  {@link NekoExtensionPoint#builder}, which the engine needs
     *                                  to reach the collection callbacks
     */
    <R> NekoPluginExtensionHandle<R> register(NekoExtensionPoint<R> extensionPoint);
}
