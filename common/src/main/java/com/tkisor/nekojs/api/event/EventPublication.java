package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Declares independent stable and version publications of one source event. */
public final class EventPublication<E> {
    private final String name;
    private final List<EventExposure> exposures = new ArrayList<>();
    private final EnumSet<BindingExposure.Category> categories = EnumSet.noneOf(BindingExposure.Category.class);
    private boolean frozen;

    EventPublication(String name) { this.name = name; }

    public Scope versioned() {
        return select(BindingExposure.Category.VERSION);
    }

    /** Publishes the source payload itself; stable public-type validation still applies. */
    public Scope stable() {
        return select(BindingExposure.Category.STABLE);
    }

    public <V> Scope stable(Class<V> type, Function<E, V> converter) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(converter, "converter");
        Scope scope = stable();
        scope.exposure.view(name, type, converter);
        return scope;
    }

    private Scope select(BindingExposure.Category category) {
        if (frozen || !categories.add(category)) {
            throw new IllegalArgumentException("[NEKO-4057] Event publication target is frozen or duplicated: " + name);
        }
        EventExposure exposure = new EventExposure().events(name);
        if (category == BindingExposure.Category.STABLE) exposure.stable();
        else exposure.versioned();
        exposures.add(exposure);
        return new Scope(exposure);
    }

    List<EventExposure.Declaration> declarations() {
        if (exposures.isEmpty()) {
            throw new IllegalArgumentException("[NEKO-4057] Event publication requires at least one target: " + name);
        }
        frozen = true;
        return exposures.stream().map(EventExposure::declaration).toList();
    }

    /** Optional exact script scope for one publication, independent of its sibling target. */
    public final class Scope {
        private final EventExposure exposure;
        private Scope(EventExposure exposure) { this.exposure = exposure; }
        public Scope scriptTypes(ScriptType... types) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.scriptTypes(types);
            return this;
        }
    }
}
