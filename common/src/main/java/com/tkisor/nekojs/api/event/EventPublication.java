package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

/** Declares independent stable and version publications of one source event. */
public final class EventPublication<E> {
    private final String name;
    private final List<EventExposure> exposures = new ArrayList<>();
    private final EnumSet<BindingExposure.Category> categories = EnumSet.noneOf(BindingExposure.Category.class);
    private boolean frozen;

    EventPublication(String name) { this.name = name; }

    /** Selects the versioned target; uses the source payload unless a view is configured. */
    public Scope versioned() {
        return select(BindingExposure.Category.VERSION);
    }

    /** Selects the stable target; validates the source payload or configured view as a portable public type. */
    public Scope stable() {
        return select(BindingExposure.Category.STABLE);
    }

    private Scope select(BindingExposure.Category category) {
        if (frozen || !categories.add(category)) {
            throw new IllegalArgumentException("[NEKO-4057] Event publication target is frozen or duplicated: " + name);
        }
        EventExposure exposure = new EventExposure(name, category);
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

    /** Payload view and exact script scope for one target, independent of its sibling target. */
    public final class Scope {
        private final EventExposure exposure;
        private Scope(EventExposure exposure) { this.exposure = exposure; }

        /**
         * Projects the source host payload for this target only; without a view, the source payload is used.
         * Cancellable sources require a control mapping and cannot use this projection.
         */
        public <V> Scope view(Class<V> type, Function<? super E, ? extends V> converter) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.<E, V>view(type, converter);
            return this;
        }

        public Scope scriptTypes(ScriptType... types) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.scriptTypes(types);
            return this;
        }
    }
}
