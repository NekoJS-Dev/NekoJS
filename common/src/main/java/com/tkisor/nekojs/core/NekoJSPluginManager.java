package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NekoJSPlugin 管理器。
 * <p>
 * 注册插件时会同时向 common 层的 {@link NekoJSBasePluginManager} 注册。
 * {@link #getPlugins()} 返回的是完整的 {@link NekoJSPlugin} 列表。
 * </p>
 */
public final class NekoJSPluginManager {
    private static final List<NekoJSPlugin> PLUGINS = new CopyOnWriteArrayList<>();

    private NekoJSPluginManager() {}

    public static void register(NekoJSPlugin plugin) {
        PLUGINS.add(plugin);
        NekoJSBasePluginManager.register(plugin);
    }

    public static List<NekoJSPlugin> getPlugins() {
        return Collections.unmodifiableList(PLUGINS);
    }
}
