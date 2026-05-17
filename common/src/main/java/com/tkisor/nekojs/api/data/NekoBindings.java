package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;

import java.util.Map;

public final class NekoBindings {
    private NekoBindings() {}

    public static Map<String, Binding> getFor(ScriptType type) {
        return NekoPluginRuntime.current().bindings(type);
    }
}
