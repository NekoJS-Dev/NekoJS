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
 * {@code ScriptEvents} 的注册实现（平台无关）：在 STARTUP 脚本里声明命名的自定义事件组，
 * 供 server/client 脚本按组监听与触发。
 *
 * <pre>
 * // startup_scripts
 * ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
 * // server_scripts
 * MyEvents.bossKilled(payload => console.log(payload.boss))
 * MyEvents.bossKilled.post({ boss: 'ender_dragon' })
 * </pre>
 *
 * <p>事件载荷是脚本自己传入的值，不绑定平台原生事件——"按类名挂原生事件"是加载器专属能力，
 * 由 {@code NativeEvents} 承担（NeoForge 面）。
 */
@Doc("Static binding 'ScriptEvents': declares named custom event groups in a STARTUP script.")
@Doc("Server/client scripts listen with group.name(callback) and fire with group.name.post(payload).")
public class ScriptEventsJS implements ScriptEventRegistrar {

    private IPluginRuntime pluginRuntime;

    /** 绑定插件运行时（平台初始化时调用，脚本不直接使用）。 */
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
