package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.WithScriptType;
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

    private final String name;
    private final Map<String, RegisteredBus> buses;

    private EventGroup(String name) {
        this.name = Objects.requireNonNull(name);
        this.buses = new HashMap<>();
    }

    public String name() {
        return name;
    }

    public Map<String, BusHolder> viewBuses() {
        return Collections.unmodifiableMap(buses);
    }

    public BusHolder getBusHolder(String busName) {
        return this.buses.get(busName);
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
        Objects.requireNonNull(name, "name == null");
        Objects.requireNonNull(scriptType, "scriptType == null");
        Objects.requireNonNull(bus, "bus == null");
        if (this.buses.containsKey(name)) {
            throw new IllegalArgumentException(String.format("A bus with name '%s' has already been registered", name));
        }

        this.buses.put(name, new RegisteredBus(bus, scriptType));
        return bus;
    }

    public void merge(EventGroup other) {
        if (!this.name.equals(other.name)) {
            return;
        }
        other.buses.forEach((busName, registered) -> this.add(busName, registered.scriptType, registered.bus));
    }

    // 清理指定类型的监听器，用于reload scripts，但由于新的eventbus还未熟悉，也许后续会需要调整
    public void clearListeners(ScriptType type) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBus(registered.bus);
            }
        }
    }

    /// using a separate method to avoid problematic generic check
    private static <E> void clearBus(EventBusJS<E, ?> bus) {
        for (var token : bus.tokens()) {
            bus.bus().unregister(token);
        }
    }

    public interface BusHolder extends WithScriptType {

        EventBusJS<?, ?> getBus(ScriptType targetEnv);
    }

    private record RegisteredBus(EventBusJS<?, ?> bus, ScriptType scriptType) implements BusHolder {

        @Override
        public EventBusJS<?, ?> getBus(ScriptType targetEnv) {
            return canApplyOn(targetEnv) ? bus : null;
        }
    }
}
