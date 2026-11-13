package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

/**
 * Payload of the {@code ScriptEvents.server/client} callback: a script declares custom events inside it.
 *
 * <pre>
 * ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
 * ScriptEvents.server(event => event.register({ group: 'MyEvents', name: 'bossKilled' }))
 * </pre>
 */
public class ScriptEventRegistrationEvent {

    /**
     * Source id used when the current Context is unavailable: a per-file STARTUP reload then
     * degrades to clearing the whole type.
     */
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

    /** Object form: {@code { group, name }}. */
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

    /** Positional form: {@code register('MyEvents', 'bossKilled')}. */
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
     * Source script id of the registration. Only called inside a script callback, while a Context is
     * active. It reads the current Context's currentScriptId, which is what lets a per-file STARTUP
     * reload clean up by source.
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
