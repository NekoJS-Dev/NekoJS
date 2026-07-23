package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

public interface NekoPluginExtensionRegistry {
    <P extends NekoJSPlugin> void register(NekoPluginExtensionPoint<P> extensionPoint);
}
