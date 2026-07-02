package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

public interface NekoPluginExtensionProvider extends NekoJSPlugin {
    void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry);
}
