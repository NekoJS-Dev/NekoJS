package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NekoJSBasePlugin;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 负责管理所有 NekoJS 基础插件。
 */
public final class NekoJSBasePluginManager {
    private static final List<NekoJSBasePlugin> PLUGINS = new CopyOnWriteArrayList<>();

    private NekoJSBasePluginManager() {}

    public static void register(NekoJSBasePlugin plugin) {
        PLUGINS.add(plugin);
    }

    public static void registerScriptCompilers() {
        for (NekoJSBasePlugin plugin : PLUGINS) {
            plugin.registerScriptCompilers(ScriptCompilerRegistry.INSTANCE);
        }
    }

    public static List<NekoJSBasePlugin> getPlugins() {
        return Collections.unmodifiableList(PLUGINS);
    }
}