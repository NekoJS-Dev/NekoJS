package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

public interface NekoPluginExtensionRegistry {
    /**
     * Registers one extension point and returns a {@link NekoPluginExtensionHandle} that
     * fetches its product after the bootstrap completes.
     *
     * @throws IllegalStateException    the registration window is closed (the registry has frozen)
     * @throws IllegalArgumentException the extension point id is already used in this bootstrap
     */
    <P extends NekoJSPlugin, A, R> NekoPluginExtensionHandle<R> register(NekoPluginExtensionPoint<P, A, R> extensionPoint);
}
