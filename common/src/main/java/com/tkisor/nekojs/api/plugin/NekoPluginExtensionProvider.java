package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSBasePlugin;

public interface NekoPluginExtensionProvider extends NekoJSBasePlugin {
    void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry);
}
