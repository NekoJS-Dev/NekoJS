package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;

/**
 * Registration side of {@code ScriptEvents}, platform independent: a STARTUP script declares
 * named custom event groups, and server/client scripts listen to and fire them by group.
 *
 * <pre>
 * // startup_scripts
 * ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
 * // server_scripts
 * MyEvents.bossKilled(payload => console.log(payload.boss))
 * MyEvents.bossKilled.post({ boss: 'ender_dragon' })
 * </pre>
 *
 * <p>The payload is whatever value the script passes in. These events are not tied to native
 * platform events; attaching a native event by class name is a loader-specific feature handled
 * by {@code NativeEvents} (the NeoForge side).
 */
@Doc("Static binding 'ScriptEvents': declares named custom event groups in a STARTUP script.")
@Doc("Server/client scripts listen with group.name(callback) and fire with group.name.post(payload).")
public class ScriptEventsJS implements ScriptEventRegistrar {

    private IPluginRuntime pluginRuntime;

    /** Binds the plugin runtime; called during platform init, scripts never call it directly. */
    public void bindRuntime(IPluginRuntime pluginRuntime) {
        if (pluginRuntime == null) {
            throw new IllegalArgumentException("pluginRuntime == null");
        }
        this.pluginRuntime = pluginRuntime;
    }

    private IPluginRuntime pluginRuntime() {
        if (pluginRuntime == null) {
            throw new IllegalStateException("ScriptEventsJS runtime has not been bound yet");
        }
        return pluginRuntime;
    }

    @Doc("Declares a custom script event that scripts of the target type can listen to and post.")
    @Param(name = "targetType", value = "which scripts may listen: 'server' or 'client'")
    @Param(name = "groupName", value = "group name; must be a valid JS identifier, e.g. 'MyEvents'")
    @Param(name = "eventName", value = "event name inside the group; must be a valid JS identifier")
    @Param(name = "sourceScriptId", value = "registering script id, used to clean up on reload")
    @Override
    public void register(ScriptType targetType, String groupName, String eventName, String sourceScriptId) {
        validateName("group", groupName);
        validateName("name", eventName);
        IPluginRuntime runtime = pluginRuntime();
        ScriptEventRegistry.validateAvailable(runtime, targetType, groupName, eventName);

        EventBusJS<Object, Void> bus = EventBusJS.of(Object.class, false);
        bus.metadata(groupName, eventName);
        ScriptEventRegistry.register(runtime, new ScriptEventDefinition(
                groupName,
                eventName,
                targetType,
                sourceScriptId,
                bus,
                () -> {}
        ));
        NekoJS.LOGGER.debug("Script event registered: {}.{} for {}", groupName, eventName, targetType);
    }

    private static void validateName(String field, String value) {
        if (value == null || !value.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("ScriptEvents " + field + " must be a valid JS identifier: " + value);
        }
    }
}
