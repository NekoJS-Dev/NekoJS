package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.api.event.IScriptHandler;
import com.tkisor.nekojs.api.event.TargetedEventHandler;
import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.script.ScriptType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class EventGroup {
    private final String name;
    private final Map<String, IScriptHandler> handlers = new LinkedHashMap<>();

    private final Map<String, Class<?>> eventTypes = new LinkedHashMap<>();
    private final Map<String, Boolean> targetedFlags = new LinkedHashMap<>();

    private EventGroup(String name) {
        this.name = name;
    }

    public static EventGroup of(String name) {
        return new EventGroup(name);
    }

    public String name() {
        return name;
    }

    public boolean hasHandler(String key) {
        return handlers.containsKey(key);
    }

    public boolean isHandlerValidFor(String key, ScriptType currentEnv) {
        IScriptHandler h = handlers.get(key);
        if (h == null) return false;
        return h.scriptType() == ScriptType.COMMON || h.scriptType() == currentEnv;
    }

    public Set<String> getHandlerKeys() {
        return handlers.keySet();
    }

    public Class<?> getEventType(String key) {
        return eventTypes.get(key);
    }

    public boolean isTargeted(String key) {
        return targetedFlags.getOrDefault(key, false);
    }


    public <E extends NekoEvent> EventHandler<E> server(String name, Supplier<Class<E>> type) {
        return add(name, ScriptType.SERVER, type.get());
    }

    public <E extends NekoEvent> EventHandler<E> client(String name, Supplier<Class<E>> type) {
        return add(name, ScriptType.CLIENT, type.get());
    }

    public <E extends NekoEvent> EventHandler<E> startup(String name, Supplier<Class<E>> type) {
        return add(name, ScriptType.STARTUP, type.get());
    }

    public <E extends NekoEvent> EventHandler<E> common(String name, Supplier<Class<E>> type) {
        return add(name, ScriptType.COMMON, type.get());
    }

    private <E extends NekoEvent> EventHandler<E> add(String name, ScriptType scriptType, Class<E> clazz) {
        var h = new EventHandler<E>(this.name + "." + name, scriptType);
        handlers.put(name, h);
        eventTypes.put(name, clazz);
        targetedFlags.put(name, false);
        return h;
    }


    public <E extends NekoEvent> TargetedEventHandler<E> targetedServer(String name, Supplier<Class<E>> type) {
        return addTargeted(name, ScriptType.SERVER, type.get());
    }

    public <E extends NekoEvent> TargetedEventHandler<E> targetedClient(String name, Supplier<Class<E>> type) {
        return addTargeted(name, ScriptType.CLIENT, type.get());
    }

    public <E extends NekoEvent> TargetedEventHandler<E> targetedStartup(String name, Supplier<Class<E>> type) {
        return addTargeted(name, ScriptType.STARTUP, type.get());
    }

    public <E extends NekoEvent> TargetedEventHandler<E> targetedCommon(String name, Supplier<Class<E>> type) {
        return addTargeted(name, ScriptType.COMMON, type.get());
    }

    private <E extends NekoEvent> TargetedEventHandler<E> addTargeted(String name, ScriptType scriptType, Class<E> clazz) {
        var h = new TargetedEventHandler<E>(this.name + "." + name, scriptType);
        handlers.put(name, h);
        eventTypes.put(name, clazz);
        targetedFlags.put(name, true);
        return h;
    }
}