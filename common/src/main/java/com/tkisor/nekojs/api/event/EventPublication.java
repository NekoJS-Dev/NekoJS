package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.BindingExposure;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

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

    /** Payload view, cancellation control, dispatch selector, and exact script scope for one target. */
    public final class Scope {
        private final EventExposure exposure;
        private Scope(EventExposure exposure) { this.exposure = exposure; }

        /**
         * Projects the source host payload for this target only; without a view, the source payload is used.
         * Cancellable sources require an explicit {@link #cancellable()} control mapping when using a projection.
         */
        public <V> Scope view(Class<V> type, Function<? super E, ? extends V> converter) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.<E, V>view(type, converter);
            return this;
        }

        /**
         * Declares that projected callbacks on this target may cancel the underlying cancellable event bus
         * by returning {@code true}.
         *
         * @return this publication scope
         */
        @Doc("Allows projected callbacks on this target to cancel the underlying cancellable event bus by returning true.")
        @Return("This publication scope for chaining.")
        public Scope cancellable() {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.cancellable();
            return this;
        }

        /**
         * Projects a portable subscription selector for this target onto the underlying {@link DispatchEventBus}
         * key type.
         *
         * @param type portable selector type exposed to scripts and catalog declarations
         * @param converter converts a script-supplied selector value into the source dispatch key
         * @param <S> portable selector type
         * @return this publication scope
         */
        @Doc("Projects a portable dispatch selector type onto the underlying dispatch event key.")
        @Param(name = "type", value = "Portable selector class exposed to scripts.")
        @Param(name = "converter", value = "Converts a script selector value into the underlying dispatch key.")
        @Return("This publication scope for chaining.")
        public <S> Scope selector(Class<S> type, Function<? super S, ?> converter) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.<S>selector(type, converter, false);
            return this;
        }

        /** Projects a selector and requires it on every subscription to this publication. */
        public <S> Scope requiredSelector(Class<S> type, Function<? super S, ?> converter) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.<S>selector(type, converter, true);
            return this;
        }

        public Scope notificationOnly() {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.notificationOnly();
            return this;
        }

        public Scope when(Predicate<? super E> filter) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.<E>when(filter);
            return this;
        }

        public Scope scriptTypes(ScriptType... types) {
            if (frozen) throw new IllegalArgumentException("[NEKO-4057] Event publication is frozen: " + name);
            exposure.scriptTypes(types);
            return this;
        }
    }
}
