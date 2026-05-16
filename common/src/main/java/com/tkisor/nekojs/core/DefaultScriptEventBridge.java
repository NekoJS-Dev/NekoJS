package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;

public class DefaultScriptEventBridge implements ScriptEventBridge {
    @Override
    public void bindEvents(Value bindings, ScriptType type) {
        var values = NekoEventGroups.all().values();
        NekoJSCommon.LOGGER.info("正在为 {} 注册 {} 个事件组...", type.name(), values.size());
        for (var group : values) {
            bindings.putMember(group.name(), new EventGroupJS(group, type));
        }
    }

    @Override
    public void clearListeners(ScriptType type) {
        for (var group : NekoEventGroups.all().values()) {
            group.clearListeners(type);
        }
    }

    @Override
    public void clearListeners(ScriptType type, String scriptId) {
        for (var group : NekoEventGroups.all().values()) {
            group.clearListeners(type, scriptId);
        }
    }
}
