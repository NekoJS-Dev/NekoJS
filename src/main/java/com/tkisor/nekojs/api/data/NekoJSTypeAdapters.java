package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.core.NekoJSPluginManager;

import java.util.*;

public class NekoJSTypeAdapters {
    private static final List<JSTypeAdapter<?>> adapters = new ArrayList<>();

    private static boolean initialized = false;

    private NekoJSTypeAdapters() {}

    static void register(JSTypeAdapter<?> group) {
        adapters.add(group);
    }

    public static synchronized List<JSTypeAdapter<?>> all() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableList(adapters);
    }

    private static void initialize() {
        NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerAdapters(NekoJSTypeAdapters::register));
        initialized = true;
    }
}
