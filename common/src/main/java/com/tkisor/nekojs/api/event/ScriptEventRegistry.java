package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.ScriptType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScriptEventRegistry {
    private static final Map<String, ScriptEventDefinition> DEFINITIONS = new LinkedHashMap<>();

    private ScriptEventRegistry() {}

    public static synchronized void validateAvailable(IPluginRuntime runtime, ScriptType targetType, String groupName, String eventName) {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.eventGroups().containsKey(groupName)) {
            throw new IllegalArgumentException("Script event group conflicts with built-in event group: " + groupName);
        }
        for (ScriptType type : ScriptType.all()) {
            if (runtime.bindings(type).containsKey(groupName)) {
                throw new IllegalArgumentException("Script event group conflicts with built-in binding: " + groupName);
            }
        }
        if (DEFINITIONS.containsKey(key(targetType, groupName, eventName))) {
            throw new IllegalArgumentException("Script event already registered: " + groupName + "." + eventName + " for " + targetType);
        }
    }

    /**
     * Register a custom script event definition.
     *
     * <p>Same-source re-registration is a replacement, not an error: after a per-file STARTUP
     * reload, {@code ScriptEvents.post} re-fires every startup listener, so the same script may
     * register the same group/event with the same {@code sourceScriptId} again. The old definition
     * is unregistered and replaced. A different {@code sourceScriptId} for the same key is still a
     * hard duplicate.
     */
    public static synchronized void register(IPluginRuntime runtime, ScriptEventDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String registrationKey = key(definition.targetType(), definition.groupName(), definition.eventName());
        ScriptEventDefinition existing = DEFINITIONS.get(registrationKey);
        if (existing != null) {
            if (!Objects.equals(existing.sourceScriptId(), definition.sourceScriptId())) {
                throw new IllegalArgumentException("Script event already registered: " + definition.groupName() + "." + definition.eventName() + " for " + definition.targetType());
            }
            existing.unregister();
        } else {
            validateAvailable(runtime, definition.targetType(), definition.groupName(), definition.eventName());
        }
        DEFINITIONS.put(registrationKey, definition);
    }

    public static synchronized Map<String, Map<String, ScriptEventDefinition>> groupsFor(ScriptType type) {
        Map<String, Map<String, ScriptEventDefinition>> groups = new LinkedHashMap<>();
        for (ScriptEventDefinition definition : DEFINITIONS.values()) {
            if (!definition.canApplyOn(type)) {
                continue;
            }
            groups.computeIfAbsent(definition.groupName(), ignored -> new LinkedHashMap<>())
                    .put(definition.eventName(), definition);
        }
        return groups;
    }

    /** 当前全部动态脚本事件定义（快照副本；catalog/probe 只读消费）。 */
    public static synchronized List<ScriptEventDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static synchronized void clearListeners(ScriptType type) {
        for (ScriptEventDefinition definition : DEFINITIONS.values()) {
            if (definition.canApplyOn(type)) {
                definition.clearListeners(type);
            }
        }
    }

    public static synchronized void clearListeners(ScriptType type, String scriptId) {
        if (scriptId == null || scriptId.isBlank()) {
            return;
        }
        for (ScriptEventDefinition definition : DEFINITIONS.values()) {
            if (definition.canApplyOn(type)) {
                definition.clearListeners(type, scriptId);
            }
        }
    }

    public static synchronized void clearDefinitions(ScriptType targetType) {
        List<String> keys = new ArrayList<>();
        List<ScriptEventDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, ScriptEventDefinition> entry : DEFINITIONS.entrySet()) {
            if (entry.getValue().targetType() == targetType) {
                keys.add(entry.getKey());
                definitions.add(entry.getValue());
            }
        }
        keys.forEach(DEFINITIONS::remove);
        definitions.forEach(ScriptEventDefinition::unregister);
    }

    public static synchronized void clearDefinitions(ScriptType targetType, String sourceScriptId) {
        if (sourceScriptId == null || sourceScriptId.isBlank()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        List<ScriptEventDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, ScriptEventDefinition> entry : DEFINITIONS.entrySet()) {
            if (entry.getValue().targetType() == targetType && sourceScriptId.equals(entry.getValue().sourceScriptId())) {
                keys.add(entry.getKey());
                definitions.add(entry.getValue());
            }
        }
        keys.forEach(DEFINITIONS::remove);
        definitions.forEach(ScriptEventDefinition::unregister);
    }

    private static String key(ScriptType targetType, String groupName, String eventName) {
        return targetType.name() + ":" + groupName + "." + eventName;
    }
}
