//? if fabric {
/*package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricEventBusBridgeTest {
    @Test
    void bindForwardsRegisteredCallbackPayloadToTheScriptBus() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        AtomicReference<String> observed = new AtomicReference<>();
        AtomicReference<Consumer<String>> registered = new AtomicReference<>();
        bus.bus().listen(observed::set);

        FabricEventBusBridge.create().bind(bus, registered::set);
        registered.get().accept("callback-value");

        assertEquals("callback-value", observed.get());
    }

    @Test
    void bindCancellableReturnsTheScriptCancellationResult() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, true);
        ((CancellableEventBus<String>) bus.bus()).listen(value -> true);
        AtomicReference<Predicate<String>> registered = new AtomicReference<>();

        FabricEventBusBridge.create().bindCancellable(bus, registered::set);

        assertTrue(registered.get().test("cancelled"));
    }

    @Test
    void bindCancellableDispatchedForwardsKeyAndCancellation() {
        EventBusJS<String, Integer> bus = EventBusJS.of(String.class, true,
                com.tkisor.nekojs.api.event.DispatchKey.of(Integer.class, value -> 7));
        ((com.tkisor.nekojs.api.event.DispatchCancellableEventBus<String, Integer>) bus.bus())
                .listen(7, (java.util.function.Predicate<String>) value -> true);
        AtomicReference<BiFunction<String, Integer, Boolean>> registered = new AtomicReference<>();

        FabricEventBusBridge.create().bindCancellableDispatched(bus, registered::set);

        assertTrue(registered.get().apply("cancelled", 7));
    }

    @Test
    void bindTransformedSkipsConversionWhenNoScriptListenerExists() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        AtomicReference<Consumer<Integer>> registered = new AtomicReference<>();

        FabricEventBusBridge.create().bindTransformed(
                bus,
                registered::set,
                value -> {
                    throw new AssertionError("conversion should be skipped without listeners");
                });

        assertFalse(bus.hasListeners());
        registered.get().accept(42);
    }

    @Test
    void bindTransformedConvertsTheCallbackPayloadBeforePosting() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        AtomicReference<String> observed = new AtomicReference<>();
        AtomicReference<Consumer<Integer>> registered = new AtomicReference<>();
        bus.bus().listen(observed::set);

        FabricEventBusBridge.create().bindTransformed(
                bus,
                registered::set,
                value -> "converted-" + value);
        registered.get().accept(42);

        assertEquals("converted-42", observed.get());
    }
}
*///?}
