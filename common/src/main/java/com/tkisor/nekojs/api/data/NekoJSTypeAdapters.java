package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;

import java.util.List;

public class NekoJSTypeAdapters {
    private NekoJSTypeAdapters() {}

    public static List<JSTypeAdapter<?>> all() {
        return NekoPluginRuntime.current().adapters();
    }
}
