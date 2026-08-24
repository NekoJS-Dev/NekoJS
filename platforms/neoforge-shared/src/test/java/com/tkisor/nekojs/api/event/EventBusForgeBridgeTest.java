package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EventBusForgeBridge smoke test：用 net.neoforged.bus 的 BusBuilder 构造独立事件总线，
 * 经 bridge 注册监听、post 测试事件，断言回调被调用。仅依赖 bus API（三版本稳定）。
 *
 * <p>{@link EventBusJS} 构造时会通过 {@code EnumMap} 触发 {@code ScriptType} 枚举初始化，
 * 后者又调用 {@code Platform.getGameDir()}，故在纯 JUnit 下需先注入最小 {@link IPlatform} stub。
 */
class EventBusForgeBridgeTest {

    /** 最简事件子类：{@link Event} 为抽象类、构造器 protected，子类可直接继承。 */
    public static class TestEvent extends Event {}

    @BeforeAll
    static void initPlatformStub() {
        try {
            Platform.init(new StubPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            // 同 JVM 内其它测试已初始化平台时复用现有实例
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
