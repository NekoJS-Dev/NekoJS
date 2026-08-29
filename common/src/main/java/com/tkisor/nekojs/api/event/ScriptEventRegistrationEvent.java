package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
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

    /** 拿不到当前 Context 时的来源 id：per-file STARTUP reload 会退化成整类型清理。 */
    private static final String UNKNOWN_SOURCE = "nekojs:startup/script_events";

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
            throw new IllegalArgumentException("ScriptEvents register config takes only group and name");
        }
        register(asString(cfg.getMember("group"), "group"), asString(cfg.getMember("name"), "name"));
    }

    /** 位置形态：{@code register('MyEvents', 'bossKilled')}。 */
    public void register(String groupName, String eventName) {
        registrar.register(targetType, groupName, eventName, currentSourceScriptId());
    }

    private static boolean hasMember(Value config, String member) {
        Value value = config.getMember(member);
        return value != null && !value.isNull();
    }

    private static String asString(Value value, String field) {
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("ScriptEvents " + field + " must be a string: " + value);
        }
        return value.asString();
    }

    /**
     * 注册来源脚本 id：本方法只在脚本回调内（Context 活跃）被调用，
     * 取当前 Context 的 currentScriptId —— per-file STARTUP reload 按来源清理靠它。
     */
    private static String currentSourceScriptId() {
        Context current = Context.getCurrent();
        if (current == null) {
            return UNKNOWN_SOURCE;
        }
        String scriptId = ScriptContextRegistry.currentScriptIdOf(current);
        return scriptId == null || scriptId.isBlank() ? UNKNOWN_SOURCE : scriptId;
    }
}
