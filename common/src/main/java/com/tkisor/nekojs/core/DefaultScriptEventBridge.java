package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.event.ScriptEventGroupJS;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;

public class DefaultScriptEventBridge implements ScriptEventBridge {
    private final ScriptEventRegistrar scriptEventRegistrar;
    private volatile IPluginRuntime pluginRuntime;

    public DefaultScriptEventBridge(ScriptEventRegistrar scriptEventRegistrar) {
        this.scriptEventRegistrar = scriptEventRegistrar;
    }

    public void setPluginRuntime(IPluginRuntime pluginRuntime) {
        this.pluginRuntime = pluginRuntime;
    }

    private IPluginRuntime pluginRuntime() {
        IPluginRuntime rt = pluginRuntime;
        if (rt == null) {
            throw new IllegalStateException("PluginRuntime not yet initialized on DefaultScriptEventBridge");
        }
        return rt;
    }

    @Override
    public void bindEvents(Value bindings, ScriptType type) {
        var values = pluginRuntime().eventGroups().values();
        NekoJS.LOGGER.info("正在为 {} 注册 {} 个事件组...", type.name(), values.size());
        for (var group : values) {
            bindings.putMember(group.name(), new EventGroupJS(group, type));
        }
        ScriptEventRegistry.groupsFor(type).forEach((name, definitions) -> bindings.putMember(name, new ScriptEventGroupJS(name, definitions)));
    }

    @Override
    public ScriptEventRegistrar scriptEventRegistrar() {
        if (scriptEventRegistrar == null) {
            return ScriptEventBridge.super.scriptEventRegistrar();
        }
        return scriptEventRegistrar;
    }

    @Override
    public void clearListeners(ScriptType type) {
        for (var group : pluginRuntime().eventGroups().values()) {
            group.clearListeners(type);
        }
        ScriptEventRegistry.clearListeners(type);
        if (type == ScriptType.STARTUP) {
            ScriptEventRegistry.clearDefinitions(type);
        }
    }

    @Override
    public void clearListeners(ScriptType type, String scriptId) {
        for (var group : pluginRuntime().eventGroups().values()) {
            group.clearListeners(type, scriptId);
        }
        ScriptEventRegistry.clearListeners(type, scriptId);
        if (type == ScriptType.STARTUP) {
            ScriptEventRegistry.clearDefinitions(type, scriptId);
        }
    }
}
