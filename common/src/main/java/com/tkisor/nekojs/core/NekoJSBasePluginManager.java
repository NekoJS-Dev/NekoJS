package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 负责管理所有 NekoJS 基础插件。
 */
public final class NekoJSBasePluginManager {
    private static final List<NekoJSPlugin> PLUGINS = new CopyOnWriteArrayList<>();

    private NekoJSBasePluginManager() {}

    public static void register(NekoJSPlugin plugin) {
        PLUGINS.add(plugin);
    }

    public static List<NekoJSPlugin> getPlugins() {
        return Collections.unmodifiableList(PLUGINS);
    }
}
