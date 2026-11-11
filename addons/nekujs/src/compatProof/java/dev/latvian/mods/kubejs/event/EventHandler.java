package dev.latvian.mods.kubejs.event;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;

import java.util.Objects;
import java.util.function.Supplier;

/** Native, non-Rhino handler with only the fixture's executed binary operations. */
public class EventHandler {
    public final EventGroup group;
    public final String name;
    public final Supplier<Class<? extends KubeEvent>> eventType;
    private final ScriptType phase;
    private boolean resultEnabled;
    public EventTargetType<?> target;
    public boolean targetRequired;

    EventHandler(EventGroup group, String name, ScriptType phase, Supplier<Class<? extends KubeEvent>> eventType) {
        this.group = Objects.requireNonNull(group, "group");
        this.name = Objects.requireNonNull(name, "name");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    public EventHandler hasResult() {
        group.checkMutable();
        resultEnabled = true;
        return this;
    }

    public <T> TargetedEventHandler<T> requiredTarget(EventTargetType<T> type) {
        group.checkMutable();
        TargetedEventHandler<T> replacement = new TargetedEventHandler<>(this, type);
        group.replace(this, replacement);
        return replacement;
    }

    public ScriptType proofPhase() {
        return phase;
    }

    public boolean proofHasResult() {
        return resultEnabled;
    }

    public EventResult post(ScriptTypeHolder type, KubeEvent event) {
        return group.owner().post(this, Objects.requireNonNull(type, "type").kjs$getScriptType(),
                Objects.requireNonNull(event, "event"));
    }
}
