package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;

/**
 * 1.12.2 ScriptEventsJS — native Forge event registration is not implemented on this platform.
 * Implements ScriptEventRegistrar so it can be passed to DefaultScriptEventBridge.
 */
public class ScriptEventsJS implements ScriptEventRegistrar {
    private IPluginRuntime pluginRuntime;

    /** Binds the plugin runtime used for class resolution; called during mod init. */
    public void bindRuntime(IPluginRuntime pluginRuntime) {
        this.pluginRuntime = pluginRuntime;
    }

    /** Always throws: 1.12.2 has no native event-group registration pipeline. */
    @Doc("Not supported on 1.12.2 (no native event-group registration pipeline).")
    @Doc("Use the typed event bindings (PlayerEvents, ServerEvents, BlockEvents, ...) or NativeEvents.onEvent(...) instead.")
    @Param(name = "targetType", value = "the script type the listener targets")
    @Param(name = "groupName", value = "event group name")
    @Param(name = "eventName", value = "event name within the group")
    @Param(name = "eventClass", value = "the raw event class to listen to")
    @Param(name = "priority", value = "priority name like 'NORMAL' or 'HIGH'")
    @Param(name = "receiveCancelled", value = "if true the handler also receives already-cancelled events")
    @Override
    public void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled) {
        throw new UnsupportedOperationException(
                "ScriptEvents.register is not supported on 1.12.2 (no native event-group registration pipeline). "
                + "Use the typed event bindings (PlayerEvents, ServerEvents, BlockEvents, ...) or the native "
                + "bridge NativeEvents.onEvent(...) instead.");
    }
}
