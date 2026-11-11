package dev.latvian.mods.kubejs.event;

import com.tkisor.nekujs.proof.NativeEventProofScope;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Lazy binary definitions only; dispatch is owned by an explicit native proof scope. */
public final class EventGroup {
    public final String name;
    private final Map<String, EventHandler> handlers = new LinkedHashMap<>();
    private NativeEventProofScope owner;

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

    private EventHandler define(String name, ScriptType type, Supplier<Class<? extends KubeEvent>> eventType) {
        checkMutable();
        if (handlers.containsKey(name)) {
            throw new IllegalArgumentException("NEKO-NEKU-PROOF-DUPLICATE: duplicate event " + this.name + "." + name);
        }
        EventHandler handler = new EventHandler(this, name, type, eventType);
        handlers.put(name, handler);
        return handler;
    }

    public Map<String, EventHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }

    void replace(EventHandler previous, EventHandler replacement) {
        checkMutable();
        if (!handlers.replace(previous.name, previous, replacement)) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-DEFINITION: stale event definition");
        }
    }

    void checkMutable() {
        if (owner != null) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-FROZEN: event definitions are already registered");
        }
    }

    public void attachToProof(NativeEventProofScope scope) {
        checkMutable();
        owner = Objects.requireNonNull(scope, "scope");
    }

    NativeEventProofScope owner() {
        if (owner == null) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-UNREGISTERED: event group has no dispatch owner");
        }
        return owner;
    }
}
