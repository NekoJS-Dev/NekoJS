package dev.latvian.mods.kubejs.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;
import graal.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.function.Supplier;

/** Binary event facade; subscriptions and dispatch stay on the native Neko bus. */
public class EventHandler {
    public final EventGroup group;
    public final String name;
    public final Supplier<Class<? extends KubeEvent>> eventType;
    public transient EventTargetType<?> target;
    public transient boolean targetRequired;
    private final ScriptType phase;
    private boolean resultEnabled;
    private EventBusJS<? extends KubeEvent, Void> bus;

    EventHandler(EventGroup group, String name, ScriptType phase, Supplier<Class<? extends KubeEvent>> eventType) {
        this.group = Objects.requireNonNull(group, "group");
        this.name = Objects.requireNonNull(name, "name");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    ScriptType phase() { return phase; }

    public EventHandler hasResult() {
        group.checkMutable();
        resultEnabled = true;
        return this;
    }

    public <T> TargetedEventHandler<T> requiredTarget(EventTargetType<T> type) {
        return targeted(type, true);
    }

    public <T> TargetedEventHandler<T> supportsTarget(EventTargetType<T> type) {
        return targeted(type, false);
    }

    private <T> TargetedEventHandler<T> targeted(EventTargetType<T> type, boolean required) {
        group.checkMutable();
        TargetedEventHandler<T> replacement = new TargetedEventHandler<>(this, type, required);
        ((EventHandler) replacement).resultEnabled = resultEnabled;
        group.replace(this, replacement);
        return replacement;
    }

    EventBusJS<? extends KubeEvent, Void> nativeBus() {
        if (bus == null) {
            // Supported channels publish the actual payload for native callback validation and Probe.
            // Unsupported targets keep optional wrapper classes unresolved and reject subscriptions.
            bus = target == null ? EventBusJS.of(resolveEventType(), resultEnabled)
                    : new EventBusJS<KubeEvent, Void>(EventBusJS.of(KubeEvent.class, resultEnabled).bus()) {
                        @Override
                        public Object execute(Value... arguments) {
                            throw unsupportedTarget();
                        }
                    };
        }
        return bus;
    }

    private Class<? extends KubeEvent> resolveEventType() {
        Class<?> resolved = eventType.get();
        if (resolved == null || !KubeEvent.class.isAssignableFrom(resolved)) {
            throw new IllegalArgumentException("[NEKO-4215] Invalid legacy event payload type: " + this
                    + ", actual=" + (resolved == null ? "null" : resolved.getName()));
        }
        return resolved.asSubclass(KubeEvent.class);
    }

    public boolean hasListeners() {
        return bus != null && bus.hasListeners();
    }

    public EventResult post(KubeEvent event) {
        return post(phase, event);
    }

    public EventResult post(ScriptTypeHolder type, KubeEvent event) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(event, "event");
        if (type.kjs$getScriptType() != phase) {
            throw new IllegalArgumentException("[NEKO-4213] Invalid legacy event phase: " + this + ", requested=" + type.kjs$getScriptType());
        }
        // Unused optional channels are harmless; targeted subscription itself is rejected.
        if (!hasListeners()) return EventResult.PASS;
        if (target != null) throw unsupportedTarget();
        Class<? extends KubeEvent> expected = bus.eventType();
        if (!expected.isInstance(event)) {
            throw new IllegalArgumentException("[NEKO-4214] Invalid legacy event payload: " + this + ", expected=" + expected.getName());
        }
        EventResult result = EventResult.fromCancellation(postNative(bus, event));
        event.afterPosted(result);
        return result;
    }

    private static <E extends KubeEvent> boolean postNative(EventBusJS<E, Void> bus, KubeEvent event) {
        return bus.post(bus.eventType().cast(event));
    }

    protected final UnsupportedOperationException unsupportedTarget() {
        return new UnsupportedOperationException("[NEKO-4217] Legacy event targets are unsupported: " + this);
    }

    @Override
    public String toString() { return group.name + "." + name; }
}
