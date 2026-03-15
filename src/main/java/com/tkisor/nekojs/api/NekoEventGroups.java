package com.tkisor.nekojs.api;

import com.tkisor.nekojs.core.NekoJSPluginManager;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NekoEventGroups {
    private static final Map<String, EventGroup> GROUPS = new LinkedHashMap<>();
    private static boolean initialized = false;

    private NekoEventGroups() {}

    static void register(EventGroup group) {
        GROUPS.put(group.name(), group);
    }

    public static synchronized Map<String, EventGroup> all() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(GROUPS);
    }

    private static void initialize() {
        NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerEvents(NekoEventGroups::register));
        initialized = true;
    }
}