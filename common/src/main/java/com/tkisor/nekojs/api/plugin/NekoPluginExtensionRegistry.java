package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSBasePlugin;

public interface NekoPluginExtensionRegistry {
    <P extends NekoJSBasePlugin> void register(NekoPluginExtensionPoint<P> extensionPoint);
}
