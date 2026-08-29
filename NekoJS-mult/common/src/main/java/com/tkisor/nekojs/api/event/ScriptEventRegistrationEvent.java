package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Value;

/**
 * {@code ScriptEvents.server/client} 的回调载荷：脚本在其中声明自定义事件。
 *
 * <pre>
 * ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
 * ScriptEvents.server(event => event.register({ group: 'MyEvents', name: 'bossKilled' }))
 * </pre>
 */
public class ScriptEventRegistrationEvent {
    private final ScriptType targetType;
    private final ScriptEventRegistrar registrar;

    public ScriptEventRegistrationEvent(ScriptType targetType, ScriptEventRegistrar registrar) {
        this.targetType = targetType;
        this.registrar = registrar;
    }

    public ScriptType targetType() {
        return targetType;
    }

    /** 对象形态：{@code { group, name }}。 */
    public void register(Object config) {
        Value cfg = config == null ? null : Value.asValue(config);
        if (cfg == null || !cfg.hasMembers()) {
            throw new IllegalArgumentException("ScriptEvents register config must be an object");
        }
        if (hasMember(cfg, "event") || hasMember(cfg, "eventClass")) {
            throw new IllegalArgumentException(
                    "ScriptEvents register config takes only group and name; custom events carry a script-provided payload");
        }
        register(cfg.getMember("group"), cfg.getMember("name"));
    }

    /** 位置形态：{@code register('MyEvents', 'bossKilled')}。 */
    public void register(Object groupName, Object eventName) {
        String group = asString(groupName, "group");
        String name = asString(eventName, "name");
        registrar.register(targetType, group, name, resolveSourceScriptId(groupName, eventName));
    }

    private static boolean hasMember(Value config, String member) {
        Value value = config.getMember(member);
        return value != null && !value.isNull();
    }

    private static String asString(Object value, String field) {
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        if (value instanceof Value polyglot && polyglot.isString()) {
            return polyglot.asString();
        }
        throw new IllegalArgumentException("ScriptEvents " + field + " must be a string: " + value);
    }

    /**
     * 注册来源脚本 id：从脚本传入的任一 Graal 值反查其 Context 的当前脚本 id。
     * 拿不到时退回常量——per-file STARTUP reload 的按来源清理会退化成整类型清理。
     */
    private static String resolveSourceScriptId(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof Value polyglot) {
                String scriptId = ScriptContextRegistry.currentScriptIdOf(polyglot.getContext());
                if (scriptId != null && !scriptId.isBlank()) {
                    return scriptId;
                }
            }
        }
        return "nekojs:startup/script_events";
    }
}
