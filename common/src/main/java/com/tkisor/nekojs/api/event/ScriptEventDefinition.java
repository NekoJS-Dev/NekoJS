package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

public record ScriptEventDefinition(
        String groupName,
        String eventName,
        ScriptType targetType,
        String eventClassName,
        String sourceScriptId,
        EventBusJS<?, ?> bus,
        Runnable unregisterer
) {
    public boolean canApplyOn(ScriptType type) {
        return targetType.test(type);
    }

    public void clearListeners(ScriptType type) {
        bus.clearTokens(type);
    }

    public void clearListeners(ScriptType type, String scriptId) {
        bus.clearTokens(type, scriptId);
    }

    public void unregister() {
        unregisterer.run();
        for (ScriptType type : ScriptType.all()) {
            bus.clearTokens(type);
        }
    }
}
