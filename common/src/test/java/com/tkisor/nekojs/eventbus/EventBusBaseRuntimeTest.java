package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventListenerToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime behaviour tests for {@link EventBusBase} via the plain {@link EventBusImpl}.
 * Pure Java — no Platform/Graal/Minecraft fixture required (TEST-3).
 */
class EventBusBaseRuntimeTest {

    @Test
    void listenAndPostDeliversToConsumer() {
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        List<String> seen = new ArrayList<>();
        bus.listen(seen::add);
        bus.post("hello");
        assertEquals(List.of("hello"), seen);
    }

    @Test
    void unregisterRemovesListener() {
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        List<String> seen = new ArrayList<>();
        EventListenerToken<String> token = bus.listen(seen::add);
        assertTrue(bus.unregister(token));
        bus.post("ignored");
        assertTrue(seen.isEmpty());
    }

    @Test
    void unregisterByForeignTokenReturnsFalse() {
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        EventBus<String> other = new EventBusImpl<>(String.class, null);
        EventListenerToken<String> foreign = other.listen(e -> {});
        assertFalse(bus.unregister(foreign));
    }

    @Test
    void higherPriorityInvokedEarlier() {
        // compareTo = -Byte.compare(priority) -> higher byte first.
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        List<String> order = new ArrayList<>();
        bus.listen((byte) 0, e -> order.add("normal"));
        bus.listen((byte) 63, e -> order.add("high"));
        bus.listen((byte) -64, e -> order.add("low"));
        bus.post("x");
        assertEquals(List.of("high", "normal", "low"), order);
    }

    @Test
    void isEmptyReflectsRegistrationState() {
        EventBusImpl<String> bus = new EventBusImpl<>(String.class, null);
        assertTrue(bus.isEmpty());
        EventListenerToken<String> token = bus.listen(e -> {});
        assertFalse(bus.isEmpty());
        bus.unregister(token);
        assertTrue(bus.isEmpty());
    }

    @Test
    void listenerAddedAfterFirstPostStillFires() {
        // Regression for CONC-4: getBuilt caches a compiled snapshot; listen() must invalidate it.
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        List<String> seen = new ArrayList<>();
        bus.post("first");
        assertTrue(seen.isEmpty());
        bus.listen(seen::add);
        bus.post("second");
        assertEquals(List.of("second"), seen);
    }

    @Test
    void multipleListenersAllInvokedInPriorityOrder() {
        EventBus<Integer> bus = new EventBusImpl<>(Integer.class, null);
        List<Integer> order = new ArrayList<>();
        bus.listen(e -> order.add(10));
        bus.listen((byte) 127, e -> order.add(20));
        bus.listen((byte) -128, e -> order.add(30));
        bus.post(1);
        assertEquals(List.of(20, 10, 30), order);
    }
}
