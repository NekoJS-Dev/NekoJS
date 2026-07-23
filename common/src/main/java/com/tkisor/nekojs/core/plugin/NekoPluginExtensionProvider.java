package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

public interface NekoPluginExtensionProvider extends NekoJSPlugin {
    void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry);
}
