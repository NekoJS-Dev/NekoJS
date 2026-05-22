package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;

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

    public void register(Value config) {
        if (config == null || !config.hasMembers()) {
            throw new IllegalArgumentException("ScriptEvents register config must be an object");
        }
        String groupName = readString(config, "group");
        String eventName = readString(config, "name");
        Object eventClass = readEventClass(config);
        String priority = readString(config, "priority", "normal");
        boolean receiveCancelled = readBoolean(config, "receiveCancelled", false);
        registrar.register(targetType, groupName, eventName, eventClass, priority, receiveCancelled);
    }

    public void register(String groupName, String eventName, Object eventClass) {
        registrar.register(targetType, groupName, eventName, eventClass, "normal", false);
    }

    public void register(String groupName, String eventName, Object eventClass, String priority) {
        registrar.register(targetType, groupName, eventName, eventClass, priority, false);
    }

    public void register(String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled) {
        registrar.register(targetType, groupName, eventName, eventClass, priority, receiveCancelled);
    }

    private static String readString(Value config, String member) {
        Value value = config.getMember(member);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("ScriptEvents register config field must be a string: " + member);
        }
        return value.asString();
    }

    private static String readString(Value config, String member, String fallback) {
        Value value = config.getMember(member);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("ScriptEvents register config field must be a string: " + member);
        }
        return value.asString();
    }

    private static boolean readBoolean(Value config, String member, boolean fallback) {
        Value value = config.getMember(member);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("ScriptEvents register config field must be a boolean: " + member);
        }
        return value.asBoolean();
    }

    private static Object readEventClass(Value config) {
        Value value = config.getMember("event");
        if (value == null || value.isNull()) {
            value = config.getMember("eventClass");
        }
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("ScriptEvents register config requires event or eventClass");
        }
        return value;
    }
}
