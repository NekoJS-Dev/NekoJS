package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.core.NekoJSPluginManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NekoBindings {
    private static final Map<String, Object> BINDING = new LinkedHashMap<>();
    private static boolean initialized = false;

    private NekoBindings() {}

    static void register(Binding binding) {
        BINDING.put(binding.getName(), binding.getObject());
    }

    public static synchronized Map<String, Object> all() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(BINDING);
    }

    private static void initialize() {
        NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerBindings(NekoBindings::register));
        initialized = true;
    }
}
