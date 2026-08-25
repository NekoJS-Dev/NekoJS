package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Side-contract test for the filtered bind overload
 * {@link EventBusForgeBridge#bind(EventBusJS, java.util.function.Predicate)} used by
 * {@code ClientEvents.playerTickPre} / {@code playerTickPost} (and the mirrored server
 * filters in {@code PlayerEvents}) to split a both-logical-side NeoForge event between
 * the CLIENT and SERVER script buses.
 *
 * <p>Runs in bare JUnit with a stand-in event class (no Minecraft class-init): the
 * contract under test is pure bridge semantics — filtered-out events are not dispatched,
 * filtered-in events are dispatched, and cancellation from a cancellable script bus
 * propagates to the native event only after it passes the filter.
 */
class EventBusForgeBridgeSideFilterTest {

    /** Minimal both-side stand-in for a NeoForge tick event; {@code clientSide} mimics {@code level().isClientSide()}. */
    public static class TestEvent extends Event {
        private final boolean clientSide;

        public TestEvent(boolean clientSide) {
            this.clientSide = clientSide;
        }

        public boolean isClientSide() {
            return clientSide;
        }
    }

    /** Cancellable stand-in, mimicking e.g. {@code InputEvent.InteractionKeyMappingTriggered}. */
    public static class CancellableTestEvent extends TestEvent implements ICancellableEvent {
        public CancellableTestEvent(boolean clientSide) {
            super(clientSide);
        }
    }

    @BeforeAll
    static void initPlatformStub() {
        try {
            Platform.init(new StubPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            // 同 JVM 内其它测试已初始化平台时复用现有实例
        }
    }

    @Test
    void onlyEventsPassingTheFilterAreDispatched() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusJS<TestEvent, Object> busJS = EventBusJS.of(TestEvent.class);

        AtomicInteger dispatched = new AtomicInteger();
        busJS.bus().listen(event -> dispatched.incrementAndGet());

        EventBusForgeBridge.create(forgeBus).bind(busJS, TestEvent::isClientSide);

        forgeBus.post(new TestEvent(false));
        forgeBus.post(new TestEvent(true));
        forgeBus.post(new TestEvent(false));

        assertEquals(1, dispatched.get(), "only the client-side instance should reach the bus");
    }

    @Test
    void cancellationPropagatesOnlyThroughTheFilter() {
        IEventBus forgeBus = BusBuilder.builder().build();
        // cancellable bus, mirroring GROUP.client(...) after the external predicate marks
        // ICancellableEvent types cancellable
        EventBusJS<CancellableTestEvent, Object> busJS =
                EventBusJS.of(CancellableTestEvent.class, true);

        // script listener "returns true" -> cancel；bus() 静态类型是基接口（Consumer-only），
        // Predicate 重载在 CancellableEventBus 上，按实际运行时形态下转
        ((com.tkisor.nekojs.api.event.CancellableEventBus<CancellableTestEvent>) busJS.bus())
                .listen((Predicate<CancellableTestEvent>) event -> true);

        EventBusForgeBridge.create(forgeBus).bind(busJS, TestEvent::isClientSide);

        CancellableTestEvent serverEvent = new CancellableTestEvent(false);
        CancellableTestEvent clientEvent = new CancellableTestEvent(true);

        forgeBus.post(serverEvent);
        assertFalse(
                serverEvent.isCanceled(),
                "a filtered-out event must not be dispatched, so it must stay uncanceled");

        forgeBus.post(clientEvent);
        assertTrue(
                clientEvent.isCanceled(),
                "a passed-filter event that the script bus canceled must end up canceled on the native event");
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
            return Path.of(System.getProperty("java.io.tmpdir"), "nekojs-smoke-test");
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
