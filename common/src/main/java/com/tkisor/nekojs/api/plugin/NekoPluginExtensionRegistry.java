package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

public interface NekoPluginExtensionRegistry {
    <P extends NekoJSPlugin> void register(NekoPluginExtensionPoint<P> extensionPoint);
}
