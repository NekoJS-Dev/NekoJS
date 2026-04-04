package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.core.NekoJSPluginManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

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
        var plugins = NekoJSPluginManager.getPlugins();
        plugins.forEach(plugin -> plugin.registerEvents(NekoEventGroups::register));
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            plugins.forEach(plugin -> plugin.registerClientEvents(NekoEventGroups::register));
        }
        initialized = true;
    }
}