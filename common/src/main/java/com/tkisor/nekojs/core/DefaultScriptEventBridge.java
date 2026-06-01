package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.event.ScriptEventGroupJS;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;

public class DefaultScriptEventBridge implements ScriptEventBridge {
    private final ScriptEventRegistrar scriptEventRegistrar;

    public DefaultScriptEventBridge(ScriptEventRegistrar scriptEventRegistrar) {
        this.scriptEventRegistrar = scriptEventRegistrar;
    }

    @Override
    public void bindEvents(Value bindings, ScriptType type) {
        var values = NekoPluginRuntime.current().eventGroups().values();
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
        for (var group : NekoPluginRuntime.current().eventGroups().values()) {
            group.clearListeners(type);
        }
        ScriptEventRegistry.clearListeners(type);
        if (type == ScriptType.STARTUP) {
            ScriptEventRegistry.clearDefinitions(type);
        }
    }

    @Override
    public void clearListeners(ScriptType type, String scriptId) {
        for (var group : NekoPluginRuntime.current().eventGroups().values()) {
            group.clearListeners(type, scriptId);
        }
        ScriptEventRegistry.clearListeners(type, scriptId);
        if (type == ScriptType.STARTUP) {
            ScriptEventRegistry.clearDefinitions(type, scriptId);
        }
    }
}
