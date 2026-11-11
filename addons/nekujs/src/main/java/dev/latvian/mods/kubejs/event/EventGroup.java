package dev.latvian.mods.kubejs.event;

import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Root-persistent definitions published through Neko's event collection Point. */
public final class EventGroup {
    public final String name;
    private final Map<String, EventHandler> handlers = new LinkedHashMap<>();
    private boolean frozen;

    private EventGroup(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static EventGroup of(String name) {
        return new EventGroup(name);
    }

    public EventHandler startup(String name, Supplier<Class<? extends KubeEvent>> eventType) {
        return define(name, ScriptType.STARTUP, eventType);
    }

    public EventHandler server(String name, Supplier<Class<? extends KubeEvent>> eventType) {
        return define(name, ScriptType.SERVER, eventType);
    }

    public EventHandler client(String name, Supplier<Class<? extends KubeEvent>> eventType) {
        return define(name, ScriptType.CLIENT, eventType);
    }

    private EventHandler define(String name, ScriptType phase, Supplier<Class<? extends KubeEvent>> eventType) {
        checkMutable();
        if (handlers.containsKey(name)) {
            throw new IllegalArgumentException("[NEKO-4210] Duplicate legacy event: " + this.name + "." + name);
        }
        EventHandler handler = new EventHandler(this, name, phase, eventType);
        handlers.put(name, handler);
        return handler;
    }

    public Map<String, EventHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }

    void checkMutable() {
        if (frozen) {
            throw new IllegalStateException("[NEKO-4211] Legacy event definitions are frozen: " + name);
        }
    }

    void replace(EventHandler previous, EventHandler replacement) {
        checkMutable();
        if (!handlers.replace(previous.name, previous, replacement)) {
            throw new IllegalStateException("[NEKO-4212] Stale legacy event definition: " + previous);
        }
    }

    /** Collection only: native ScriptManager generations own all callback resources. */
    public void register(com.tkisor.nekojs.api.event.EventGroupRegistry registry, boolean client) {
        Objects.requireNonNull(registry, "registry");
        frozen = true;
        var nativeGroup = com.tkisor.nekojs.api.event.EventGroup.of(name);
        boolean populated = false;
        for (EventHandler handler : handlers.values()) {
            if ((handler.phase() == ScriptType.CLIENT) == client) {
                nativeGroup.add(handler.name, handler.phase().nativeType(), handler.nativeBus());
                populated = true;
            }
        }
        if (populated) registry.register(nativeGroup);
    }

    @Override
    public String toString() { return name; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof EventGroup group && name.equals(group.name);
    }
}
