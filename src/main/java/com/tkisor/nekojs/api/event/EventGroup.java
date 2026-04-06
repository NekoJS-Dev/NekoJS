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
        return add(name, ScriptType.SERVER, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    public <E, K> EventBusJS<E, K> client(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    public <E, K> EventBusJS<E, K> startup(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    public <E, K> EventBusJS<E, K> common(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.COMMON, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
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

    public void merge(EventGroup other) {
        if (!this.name.equals(other.name)) {
            return;
        }
        other.buses.forEach((busName, bus) -> {
            ScriptType type = other.targetScriptType.get(busName);
            this.add(busName, type, bus);
        });
    }

    public void clearListeners() {
        for (var busJS : buses.values()) {
            clearBus(busJS);
        }
    }

    // 清理指定类型的监听器，用于reload scripts，但由于新的eventbus还未熟悉，也许后续会需要调整
    public void clearListeners(ScriptType type) {
        for (var entry : buses.entrySet()) {
            String busName = entry.getKey();
            EventBusJS<?, ?> busJS = entry.getValue();

            ScriptType busType = targetScriptType.get(busName);

            if (busType == type || type == ScriptType.COMMON) {
                clearBus(busJS);
            }
        }
    }

    /// using a separate method to avoid problematic generic check
    private static <E> void clearBus(EventBusJS<E, ?> bus) {
        for (var token : bus.tokens()) {
            bus.bus().unregister(token);
        }
    }
}
