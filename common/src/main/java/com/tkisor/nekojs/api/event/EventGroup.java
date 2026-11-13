package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.WithScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A named event group such as {@code ServerEvents}: a set of {@link EventBusJS} instances registered by
 * name and exposed to scripts. Call {@link #freeze()} after bootstrap registration; once frozen, no bus
 * can be added or removed.
 *
 * @author ZZZank
 */
public class EventGroup {
    /** Creates a named empty event group; group name cannot be {@code null}. */
    public static EventGroup of(String name) {
        return new EventGroup(name);
    }

    private final String name;
    private final Map<String, RegisteredBus> buses;
    private final java.util.List<EventExposure.Declaration> publications = new java.util.ArrayList<>();
    private volatile boolean frozen;

    private EventGroup(String name) {
        this.name = Objects.requireNonNull(name);
        // LinkedHashMap: Maintains registration order, viewBuses()/probe directory iteration deterministic (HashMap's
        // String key iteration order depends on internal hashCode layout, fragile across JDK/runs)
        this.buses = new LinkedHashMap<>();
    }

    /** Declares a group-wide module publication; registration supplies the actual plugin owner. */
    public EventGroup expose(java.util.function.Consumer<EventExposure> configure) {
        checkMutable();
        EventExposure exposure = new EventExposure();
        Objects.requireNonNull(configure, "configure").accept(exposure);
        publications.add(exposure.declaration());
        return this;
    }

    java.util.List<ModuleEventGroup> modulePublications(String owner) {
        return publications.stream().map(publication -> publication.bind(owner, this)).toList();
    }

    private void checkMutable() {
        if (frozen) throw new IllegalStateException("EventGroup '" + name + "' is frozen after bootstrap");
    }

    /** Group name (e.g., {@code "ServerEvents"}). */
    public String name() {
        return name;
    }

    /** Freezes this group; after freezing, {@link #add} and {@link #merge} will throw {@link IllegalStateException}. */
    public void freeze() {
        frozen = true;
    }

    /** Read-only view of the buses in this group (bus name → {@link BusHolder}), iterating in registration order. */
    public Map<String, BusHolder> viewBuses() {
        return Collections.unmodifiableMap(buses);
    }

    /** Returns the holder for the given bus name; returns {@code null} if it does not exist. */
    public BusHolder getBusHolder(String busName) {
        return this.buses.get(busName);
    }

    /** Registers an event bus that can be listened to by SERVER scripts (cannot be cancelled, no dispatch). */
    public <E> EventBusJS<E, Void> server(String name, Class<E> type) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type));
    }

    /** Registers a bus for CLIENT scripts. It is not cancellable and has no dispatch key. */
    public <E> EventBusJS<E, Void> client(String name, Class<E> type) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type));
    }

    /** Registers a bus for STARTUP scripts. It is not cancellable and has no dispatch key. */
    public <E> EventBusJS<E, Void> startup(String name, Class<E> type) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type));
    }

    /** Registers a bus for SERVER scripts that dispatches each event by {@code dispatchKey}. */
    public <E, K> EventBusJS<E, K> server(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    /** Registers a bus for CLIENT scripts that dispatches each event by {@code dispatchKey}. */
    public <E, K> EventBusJS<E, K> client(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    /** Registers a bus for STARTUP scripts that dispatches each event by {@code dispatchKey}. */
    public <E, K> EventBusJS<E, K> startup(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    /** Defines a source event and its module publications in the same declaration. */
    public <E> EventBusJS<E, Void> server(String name, Class<E> type,
                                        java.util.function.Consumer<EventPublication<E>> configure) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type), configure);
    }

    public <E> EventBusJS<E, Void> client(String name, Class<E> type,
                                        java.util.function.Consumer<EventPublication<E>> configure) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type), configure);
    }

    public <E> EventBusJS<E, Void> startup(String name, Class<E> type,
                                         java.util.function.Consumer<EventPublication<E>> configure) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type), configure);
    }

    public <E, K> EventBusJS<E, K> server(String name, Class<E> type, DispatchKey<E, K> dispatchKey,
                                         java.util.function.Consumer<EventPublication<E>> configure) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey), configure);
    }

    public <E, K> EventBusJS<E, K> client(String name, Class<E> type, DispatchKey<E, K> dispatchKey,
                                         java.util.function.Consumer<EventPublication<E>> configure) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey), configure);
    }

    public <E, K> EventBusJS<E, K> startup(String name, Class<E> type, DispatchKey<E, K> dispatchKey,
                                          java.util.function.Consumer<EventPublication<E>> configure) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey), configure);
    }

    public <E, K> EventBusJS<E, K> add(String name, ScriptType scriptType, EventBusJS<E, K> bus,
                                      java.util.function.Consumer<EventPublication<E>> configure) {
        checkMutable();
        EventPublication<E> publication = new EventPublication<>(name);
        Objects.requireNonNull(configure, "configure").accept(publication);
        var declarations = publication.declarations();
        add(name, scriptType, bus);
        publications.addAll(declarations);
        return bus;
    }

    /**
     * Adds a bus to this group with its {@code scriptType} and name, and returns it.
     *
     * @throws {@link IllegalStateException} when the group is frozen
     * @throws {@link IllegalArgumentException} when a bus with this name is already registered
     * @throws {@link NullPointerException} when any argument is {@code null}
     */
    public <BUS extends EventBusJS<?, ?>> BUS add(String name, ScriptType scriptType, BUS bus) {
        if (frozen) {
            throw new IllegalStateException("EventGroup '" + this.name + "' is frozen after bootstrap");
        }
        Objects.requireNonNull(name, "name == null");
        Objects.requireNonNull(scriptType, "scriptType == null");
        Objects.requireNonNull(bus, "bus == null");
        if (this.buses.containsKey(name)) {
            throw new IllegalArgumentException(String.format("A bus with name '%s' has already been registered", name));
        }

        bus.scriptType(scriptType);
        bus.metadata(this.name, name);
        this.buses.put(name, new RegisteredBus(bus, scriptType));
        return bus;
    }

    /**
     * Copies every bus from another group with the same name into this group. Each bus goes through
     * {@link #add}, so the frozen and duplicate-name rules still apply. When the names differ this method
     * logs a warning and copies no bus.
     */
    public void merge(EventGroup other) {
        if (!this.name.equals(other.name)) {
            // DEFECT-D8: previously this branch silently no-oped, hiding a real
            // misconfiguration (two unrelated groups passed to merge). Warn so the
            // mismatch is observable; no buses are copied on mismatch.
            NekoJS.LOGGER.warn(
                    "EventGroup.merge skipped: name mismatch ('{}' != '{}')",
                    this.name, other.name);
            return;
        }
        checkMutable();
        for (String busName : other.buses.keySet()) {
            if (buses.containsKey(busName)) {
                throw new IllegalArgumentException("A bus with name '" + busName + "' has already been registered");
            }
        }
        var declarations = other.publications.stream().map(publication -> publication.resolveEvents(other)).toList();
        other.buses.forEach((busName, registered) -> this.add(busName, registered.scriptType, registered.bus));
        publications.addAll(declarations);
    }

    /**
     * Removes every JS listener of the given {@link ScriptType} from every bus in this group that
     * applies to that type. Script reload uses this for cleanup.
     */
    // Removes listeners of the given type. Script reload uses this; the event bus API may still change.
    public void clearListeners(ScriptType type) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBus(registered.bus, type);
            }
        }
    }

    /**
     * Removes the JS listeners that {@code scriptId} registered from every bus in this group that
     * applies to the given {@link ScriptType}.
     */
    public void clearListeners(ScriptType type, String scriptId) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBus(registered.bus, type, scriptId);
            }
        }
    }

    /**
     * Removes the listeners whose script id starts with {@code scriptIdPrefix} from every bus in this
     * group that applies to the given {@link ScriptType}. Used when a whole script pack is unloaded; see
     * {@link EventBusJS#clearTokensByPrefix}.
     */
    public void clearListenersByPrefix(ScriptType type, String scriptIdPrefix) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBusByPrefix(registered.bus, type, scriptIdPrefix);
            }
        }
    }

    /** Atomically hand this group's buses from the old generation to candidate listeners. */
    public void commitPendingListeners(ScriptType type, java.util.List<EventBusJS.PendingListener> pending) {
        for (RegisteredBus registered : buses.values()) {
            if (registered.canApplyOn(type)) {
                commitBus(registered.bus, type, pending);
            }
        }
    }

    /** Prepare every bus in this group; callers finish or roll back the returned batch together. */
    public java.util.List<EventBusJS.PendingCommit> preparePendingListeners(
            ScriptType type, java.util.List<EventBusJS.PendingListener> pending) {
        return preparePendingListeners(type, pending, context -> true);
    }

    public java.util.List<EventBusJS.PendingCommit> preparePendingListeners(
            ScriptType type, java.util.List<EventBusJS.PendingListener> pending,
            java.util.function.Predicate<graal.graalvm.polyglot.Context> replaced) {
        java.util.List<EventBusJS.PendingCommit> commits = new java.util.ArrayList<>();
        try {
            for (RegisteredBus registered : buses.values()) {
                if (registered.canApplyOn(type)) {
                    commits.add(registered.bus.preparePendingListeners(type, pending, replaced));
                }
            }
            return commits;
        } catch (Throwable failure) {
            for (int index = commits.size() - 1; index >= 0; index--) {
                commits.get(index).rollback();
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to prepare event group listener batch", failure);
        }
    }

    /// using a separate method to avoid problematic generic check
    private static <E> void clearBus(EventBusJS<E, ?> bus, ScriptType type) {
        bus.clearTokens(type);
    }

    private static <E> void clearBus(EventBusJS<E, ?> bus, ScriptType type, String scriptId) {
        bus.clearTokens(type, scriptId);
    }

    private static <E> void clearBusByPrefix(EventBusJS<E, ?> bus, ScriptType type, String scriptIdPrefix) {
        bus.clearTokensByPrefix(type, scriptIdPrefix);
    }

    private static <E> void commitBus(EventBusJS<E, ?> bus, ScriptType type,
                                      java.util.List<EventBusJS.PendingListener> pending) {
        bus.commitPendingListeners(type, pending);
    }

    private static <E> EventBusJS.PendingCommit prepareBus(EventBusJS<E, ?> bus, ScriptType type,
                                                           java.util.List<EventBusJS.PendingListener> pending) {
        return bus.preparePendingListeners(type, pending);
    }

    /**
     * Handle for one bus in the group. It carries the bus's {@link ScriptType} and hands the bus out
     * for a target {@link ScriptType}.
     */
    public interface BusHolder extends WithScriptType {

        /** Returns the bus when it applies to {@code targetEnv}, otherwise {@code null}. */
        EventBusJS<?, ?> getBus(ScriptType targetEnv);
    }

    private record RegisteredBus(EventBusJS<?, ?> bus, ScriptType scriptType) implements BusHolder {

        @Override
        public EventBusJS<?, ?> getBus(ScriptType targetEnv) {
            return canApplyOn(targetEnv) ? bus : null;
        }
    }
}
