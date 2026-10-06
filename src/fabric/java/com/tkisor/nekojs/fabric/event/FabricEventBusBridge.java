package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Binds Fabric callback registrations to script event buses and skips unused payload conversion. */
public final class FabricEventBusBridge {
    private FabricEventBusBridge() {
    }

    public static FabricEventBusBridge create() {
        return new FabricEventBusBridge();
    }

    /** Registers a callback whose payload already matches the script event object. */
    public <E> FabricEventBusBridge bind(
            EventBusJS<E, ?> bus,
            Consumer<Consumer<E>> registerCallback) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(registerCallback, "registerCallback");
        registerCallback.accept(event -> {
            if (bus.hasListeners()) {
                bus.post(event);
            }
        });
        return this;
    }

    /** Registers a cancellable Fabric callback and returns the script bus cancellation result. */
    public <E> FabricEventBusBridge bindCancellable(
            EventBusJS<E, ?> bus,
            Consumer<Predicate<E>> registerCallback) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(registerCallback, "registerCallback");
        if (!bus.canCancel()) {
            throw new IllegalArgumentException(
                    "bindCancellable requires a cancellable event bus: " + bus.eventType().getName());
        }
        registerCallback.accept(event -> bus.hasListeners() && bus.post(event));
        return this;
    }

    /** Registers a callback and transforms its native payload before posting to scripts. */
    public <N, E> FabricEventBusBridge bindTransformed(
            EventBusJS<E, ?> bus,
            Consumer<Consumer<N>> registerCallback,
            Function<N, E> transform) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(registerCallback, "registerCallback");
        Objects.requireNonNull(transform, "transform");
        registerCallback.accept(nativeEvent -> {
            if (bus.hasListeners()) {
                bus.post(transform.apply(nativeEvent));
            }
        });
        return this;
    }
}
