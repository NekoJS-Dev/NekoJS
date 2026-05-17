package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NekoJSPlugin;

/**
 * NekoJS 平台插件注册入口。
 */
public final class NekoJSPluginManager {
    private NekoJSPluginManager() {}

    public static void register(NekoJSPlugin plugin) {
        NekoJSBasePluginManager.register(plugin);
    }
}
