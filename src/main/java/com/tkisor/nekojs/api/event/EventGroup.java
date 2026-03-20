package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author ZZZank
 */
public class EventGroup {
    public static EventGroup of(String name) {
        return new EventGroup(name);
    }

    final String name;
    final Map<String, EventBusJS<?, ?>> buses;
    final Map<String, ScriptType> targetScriptType;

    private EventGroup(String name) {
        this.name = Objects.requireNonNull(name);
        this.buses = new HashMap<>();
        this.targetScriptType = new HashMap<>();
    }

    public String name() {
        return name;
    }

    public Map<String, EventBusJS<?, ?>> viewBuses() {
        return Collections.unmodifiableMap(buses);
    }

    public boolean isHandlerValidFor(String busName, ScriptType type) {
        var scriptType = targetScriptType.get(busName);
        return scriptType != null && (scriptType == ScriptType.COMMON || scriptType == type);
    }

    public <E> EventBusJS<E, Void> server(String name, Class<E> type) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type));
    }

    public <E> EventBusJS<E, Void> client(String name, Class<E> type) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type));
    }

    public <E> EventBusJS<E, Void> startup(String name, Class<E> type) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type));
    }

    public <E> EventBusJS<E, Void> common(String name, Class<E> type) {
        return add(name, ScriptType.COMMON, EventBusJS.of(type));
    }

    public <E, K> EventBusJS<E, K> server(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type, NekoCancellableEvent.testType(type), dispatchKey));
    }

    public <E, K> EventBusJS<E, K> client(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type, NekoCancellableEvent.testType(type), dispatchKey));
    }

    public <E, K> EventBusJS<E, K> startup(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type, NekoCancellableEvent.testType(type), dispatchKey));
    }

    public <E, K> EventBusJS<E, K> common(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.COMMON, EventBusJS.of(type, NekoCancellableEvent.testType(type), dispatchKey));
    }

    public <BUS extends EventBusJS<?, ?>> BUS add(String name, ScriptType scriptType, BUS bus) {
        if (name == null) {
            throw new IllegalArgumentException("name == null");
        } else if (this.buses.containsKey(name)) {
            throw new IllegalArgumentException(String.format("A bus with name '%s' has already been registered", name));
        } else if (scriptType == null) {
            throw new IllegalArgumentException("scriptType == null");
        }
        this.buses.put(name, bus);
        this.targetScriptType.put(name, scriptType);
        return bus;
    }

    public void clearListeners() {
        for (var busJS : buses.values()) {
            clearBus(busJS);
        }
    }

    /// using a separate method to avoid problematic generic check
    private static <E> void clearBus(EventBusJS<E, ?> bus) {
        for (var token : bus.tokens()) {
            bus.bus().unregister(token);
        }
    }
}
