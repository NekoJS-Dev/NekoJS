package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.core.NekoJSPluginManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NekoBindings {
    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();
    private static boolean initialized = false;

    private NekoBindings() {}

    static void register(Binding binding) {
        String name = binding.getName();
        if (BINDINGS.containsKey(name)) {
            throw new IllegalArgumentException("Binding 变量 '" + name + "' 已被注册！请检查是否有插件冲突。");
        }
        BINDINGS.put(name, binding);
    }

    public static synchronized Map<String, Binding> all() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(BINDINGS);
    }

    private static void initialize() {
        NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerBindings(NekoBindings::register));
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerClientBindings(NekoBindings::register));
        }
        initialized = true;
    }
}