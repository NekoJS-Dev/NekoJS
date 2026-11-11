package dev.latvian.mods.kubejs.event;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;

import java.util.Objects;
import java.util.Set;

/** Target declarations remain lazy; subscribing is rejected by the native facade. */
public class TargetedEventHandler<T> extends EventHandler {
    TargetedEventHandler(EventHandler previous, EventTargetType<T> type, boolean required) {
        super(previous.group, previous.name, previous.phase(), previous.eventType);
        target = Objects.requireNonNull(type, "type");
        targetRequired = required;
    }

    @Override
    public TargetedEventHandler<T> hasResult() {
        super.hasResult();
        return this;
    }

    /** No target IDs exist while targeted subscriptions are rejected by the native facade. */
    public Set<T> findUniqueExtraIds(ScriptType type) {
        Objects.requireNonNull(type, "type");
        if (hasListeners()) throw unsupportedTarget();
        return Set.of();
    }

    public boolean hasListeners(T target) {
        if (hasListeners()) throw unsupportedTarget();
        return false;
    }

    public EventResult post(KubeEvent event, T target) {
        return post(event);
    }

    public EventResult post(ScriptTypeHolder type, T target, KubeEvent event) {
        return post(type, event);
    }
}
