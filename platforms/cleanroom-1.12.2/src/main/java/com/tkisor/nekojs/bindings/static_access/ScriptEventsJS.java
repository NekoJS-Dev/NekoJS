package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.ScriptType;

/**
 * 1.12.2 ScriptEventsJS — native Forge event registration is not implemented on this platform.
 * Implements ScriptEventRegistrar so it can be passed to DefaultScriptEventBridge.
 */
public class ScriptEventsJS implements ScriptEventRegistrar {
    private com.tkisor.nekojs.api.plugin.IPluginRuntime pluginRuntime;

    public void bindRuntime(com.tkisor.nekojs.api.plugin.IPluginRuntime pluginRuntime) {
        this.pluginRuntime = pluginRuntime;
    }

    @Override
    public void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled) {
        throw new UnsupportedOperationException(
                "ScriptEvents.register is not supported on 1.12.2 (native Forge event registration is unimplemented). "
                + "Use the typed event bindings (PlayerEvents, ServerEvents, BlockEvents, ...) instead.");
    }
}
