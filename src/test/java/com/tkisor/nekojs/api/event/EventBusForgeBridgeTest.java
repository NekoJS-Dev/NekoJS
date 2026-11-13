// Fabric has no equivalent implementation yet; this whole-file guard goes away once the port is
// done.
//? if neoforge {
package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.ICancellableEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for EventBusForgeBridge: builds a standalone event bus with net.neoforged.bus
 * BusBuilder, registers listeners through the bridge, posts test events and asserts that the
 * callbacks ran. It only depends on the bus API, which is stable across the three versions.
 *
 * <p>Constructing {@link EventBusJS} initializes the {@code ScriptType} enum through an
 * {@code EnumMap}, and that initialization calls {@code Platform.getGameDir()}. Under plain JUnit
 * a minimal {@link IPlatform} stub must therefore be injected first.
 */
class EventBusForgeBridgeTest {

    /**
     * Minimal event subclass: {@link Event} is abstract with a protected constructor, so a subclass
     * inherits it directly.
     */
    public static class TestEvent extends Event {}
    public static class CancellableTestEvent extends Event implements ICancellableEvent {}

    @BeforeAll
    static void initPlatformStub() {
        try {
            Platform.init(new StubPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            // Reuse the instance another test in this JVM already initialized.
        }
    }

    @Test
    void boundListenerIsInvokedWhenEventPosted() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusForgeBridge bridge = EventBusForgeBridge.create(forgeBus);

        EventBusJS<TestEvent, Object> busJS = EventBusJS.of(TestEvent.class);
        AtomicBoolean invoked = new AtomicBoolean(false);
        busJS.bus().listen(event -> invoked.set(true));

        bridge.bind(busJS);

        forgeBus.post(new TestEvent());

        assertTrue(invoked.get(), "bridge-registered listener should fire on post");
    }

    @Test
    void boundListenerReceivesPostedEventInstance() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusForgeBridge bridge = EventBusForgeBridge.create(forgeBus);

        EventBusJS<TestEvent, Object> busJS = EventBusJS.of(TestEvent.class);
        AtomicReference<TestEvent> received = new AtomicReference<>();
        busJS.bus().listen(received::set);

        bridge.bind(busJS, EventPriority.HIGH, false);

        TestEvent posted = new TestEvent();
        TestEvent returned = forgeBus.post(posted);

        assertSame(posted, returned);
        assertSame(posted, received.get());
    }

    @Test
    void transformedListenerConvertsEventType() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusForgeBridge bridge = EventBusForgeBridge.create(forgeBus);

        EventBusJS<String, Object> target = EventBusJS.of(String.class);
        AtomicReference<String> received = new AtomicReference<>();
        target.bus().listen(received::set);

        bridge.bindTransformed(target, event -> "received", TestEvent.class);

        forgeBus.post(new TestEvent());

        assertEquals("received", received.get());
    }

    @Test
    void transformedCancellationWritesBackToNativeEvent() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusJS<String, Object> target = EventBusJS.of(String.class, true);
        ((CancellableEventBus<String>) target.bus()).listen((Predicate<String>) event -> true);
        EventBusForgeBridge.create(forgeBus).bindTransformed(target, event -> "broken", CancellableTestEvent.class);

        CancellableTestEvent nativeEvent = new CancellableTestEvent();
        forgeBus.post(nativeEvent);

        assertTrue(nativeEvent.isCanceled(), "a cancelled neutral payload must cancel its native source event");
    }

    private static final class StubPlatform implements IPlatform {
        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "test";
        }

        @Override
        public Path getGameDir() {
            return com.tkisor.nekojs.TestGameDirs.unique("nekojs-smoke-test");
        }

        @Override
        public Map<String, IModInfo> getMods() {
            return Map.of();
        }

        @Override
        public IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0";
        }
    }
}
//?}
