package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;

import java.util.Map;

public final class NekoEventGroups {
    private NekoEventGroups() {}

    public static Map<String, EventGroup> all() {
        return NekoPluginRuntime.current().eventGroups();
    }
}
