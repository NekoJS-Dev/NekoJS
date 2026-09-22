//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.eventbus.CommonPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 24 每族 caller-to-result 的 bus 级行为 fixture（NeoForge 侧）：在<b>真实家族总线</b>
 * （{@code LevelEvents.LOADED}、{@code PlayerEvents.CHAT}、{@code CommandEvents.COMMAND}、
 * {@code EntityEvents.DEATH}、{@code BlockEvents.BROKEN}、{@code ItemEvents.MODIFICATION}、
 * {@code GoalEvents.REGISTER}、{@code CapabilityEvents.REGISTER}）上验证注册、payload 透传、
 * priority 次序、取消短路、多次订阅恰一次和 reload 清理入口——即脚本 listener 所依赖的
 * 分发语义在家族总线上成立（bus 无关的并发 stress 由 common 的
 * {@code EventBusJSExternalBehaviorStressTest} 承载，不在此重复）。
 *
 * <p>监听器走 Java 侧 {@code bus().listen(priority, ...)}——这正是
 * {@code EventBusForgeBridge.CancellableListener/Listener} 投递脚本总线的同一条底层注册面；
 * 无头 JVM 无法构造真实 MC 事件实例（Level/ServerPlayer 等），因此 post 使用同族形状的
 * 合成载荷（底层 post 不校验事件类型，载荷<b>原样</b>送达监听器——payload 透传语义可测）。
 * 平台原生事件 → 载荷的转换由 {@code EventBusForgeBridge} 承载，其 side filter 与
 * 取消回传已有 {@code EventBusForgeBridgeSideFilterTest}；真实平台回调的 source trace 见
 * {@code Ticket24GameplayEventPhaseTraceTest}。
 *
 * <p>静态组跨用例共享：每个用例自清理（token unregister + {@code clearTokens}），不污染
 * 同 JVM 的其它测试。
 */
class Ticket24GameplayFamilyBusBehaviorTest {

    /** 每个用例注册的 (bus, token)，{@link #tearDown} 兜底反注册。 */
    private final List<Registered> registered = new ArrayList<>();

    private record Registered(EventBus<?> bus, EventListenerToken<?> token) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        void unregister() {
            ((EventBus) bus).unregister(token);
        }
    }

    @BeforeAll
    static void initPlatformStub() {
        try {
            com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                @Override public boolean isClient() { return false; }
                @Override public boolean isDevelopment() { return true; }
                @Override public String getMcVersion() { return "test"; }
                @Override public java.nio.file.Path getGameDir() {
                    return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket24-bus");
                }
                @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
                @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
                @Override public String getLoaderId() { return "test"; }
                @Override public String getLoaderVersion() { return "0"; }
            });
        } catch (IllegalStateException alreadyInitialized) {
            // same-JVM reuse of an already initialized platform stub is fine
        }
    }

    @BeforeEach
    void initCancellabilityPredicate() {
        // 生产初始化次序同款（NekoJSMod 构造器 → NeoForgeRuntimeBootstrap.setup）：先于家族
        // 类初始化设置，可取消总线才不会被冻成不可取消
        EventBusJS.setExternalCancellabilityPredicate(
                net.neoforged.bus.api.ICancellableEvent.class::isAssignableFrom);
    }

    @AfterEach
    void tearDown() {
        for (Registered entry : List.copyOf(registered)) {
            try {
                entry.unregister();
            } catch (RuntimeException ignored) {
                // best-effort cleanup: a stale token must not block the rest
            }
        }
        registered.clear();
        LevelEvents.GROUP.clearListeners(ScriptType.SERVER);
        PlayerEvents.GROUP.clearListeners(ScriptType.SERVER);
        CommandEvents.GROUP.clearListeners(ScriptType.SERVER);
        EntityEvents.GROUP.clearListeners(ScriptType.SERVER);
        ItemEvents.GROUP.clearListeners(ScriptType.SERVER);
        BlockEvents.GROUP.clearListeners(ScriptType.SERVER);
        GoalEvents.GROUP.clearListeners(ScriptType.STARTUP);
        CapabilityEvents.GROUP.clearListeners(ScriptType.STARTUP);
    }

    /** 合成载荷：无头 JVM 里的家族形状替身，断言 payload 原样透传。 */
    private static final class StandIn {
        final String tag;

        StandIn(String tag) {
            this.tag = tag;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E, K> EventBusJS<E, K> cast(EventBusJS<?, ?> bus) {
        return (EventBusJS<E, K>) bus;
    }

    @Test
    void levelFamilyDeliversPayloadToMultipleSubscribersExactlyOnce() {
        List<String> seen = new ArrayList<>();
        EventBusJS<Object, Void> bus = cast(LevelEvents.LOADED);
        registered.add(new Registered(bus.bus(), bus.bus().listen(event -> seen.add("a:" + ((StandIn) event).tag))));
        registered.add(new Registered(bus.bus(), bus.bus().listen(event -> seen.add("b:" + ((StandIn) event).tag))));

        assertFalse(bus.post(new StandIn("l1")));

        assertEquals(List.of("a:l1", "b:l1"), seen,
                "both subscribers see the same payload instance exactly once");
    }

    @Test
    void priorityOrderingHoldsOnThePlayerChatFamilyBus() {
        List<String> order = new ArrayList<>();
        EventBusJS<Object, Void> bus = cast(PlayerEvents.CHAT);
        registered.add(new Registered(bus.bus(), bus.bus().listen(CommonPriority.LOW, event -> order.add("low"))));
        registered.add(new Registered(bus.bus(), bus.bus().listen(CommonPriority.HIGHEST, event -> order.add("highest"))));
        registered.add(new Registered(bus.bus(), bus.bus().listen(CommonPriority.NORMAL, event -> order.add("normal"))));

        bus.post(new StandIn("chat"));

        assertEquals(List.of("highest", "normal", "low"), order,
                "family bus dispatches by priority HIGHEST -> NORMAL -> LOW");
    }

    @Test
    void cancellingAListenerShortCircuitsAndReportsOnTheCommandFamilyBus() {
        List<String> order = new ArrayList<>();
        EventBusJS<Object, Void> bus = cast(CommandEvents.COMMAND);
        assertTrue(bus.canCancel(), "CommandEvent families are cancellable under the production predicate");
        CancellableEventBus<Object> cancellable = (CancellableEventBus<Object>) bus.bus();
        registered.add(new Registered(cancellable, cancellable.listen(CommonPriority.HIGH, event -> {
            order.add("high-cancel");
            return true;
        })));
        registered.add(new Registered(cancellable, cancellable.listen(CommonPriority.NORMAL, event -> {
            order.add("normal");
            return false;
        })));

        assertTrue(bus.post(new StandIn("cmd")), "a cancelling listener makes post report the cancellation");
        assertEquals(List.of("high-cancel"), order,
                "listeners after the cancelling one (by priority) are not run");

        for (Registered entry : List.copyOf(registered)) {
            entry.unregister();
        }
        registered.clear();
        order.clear();

        registered.add(new Registered(cancellable, cancellable.listen(CommonPriority.NORMAL, event -> {
            order.add("normal");
            return false;
        })));
        registered.add(new Registered(cancellable, cancellable.listen(CommonPriority.LOW, event -> {
            order.add("low-cancel");
            return true;
        })));
        assertTrue(bus.post(new StandIn("cmd")));
        assertEquals(List.of("normal", "low-cancel"), order,
                "a later-priority cancellation still runs the earlier listeners first");
    }

    @Test
    void dispatchKeyRoutingWorksOnTheEntityDeathFamilyBus() {
        List<String> seen = new ArrayList<>();
        EventBusJS<Object, Object> bus = cast(EntityEvents.DEATH);
        assertTrue(bus.canDispatch(), "EntityEvents families dispatch by entity type key");
        DispatchEventBus<Object, Object> dispatch = (DispatchEventBus<Object, Object>) bus.bus();
        Object zombieKey = new Object();
        Object skeletonKey = new Object();

        registered.add(new Registered(dispatch, dispatch.listen(null, event -> seen.add("main:" + ((StandIn) event).tag))));
        registered.add(new Registered(dispatch, dispatch.listen(zombieKey, event -> seen.add("zombie:" + ((StandIn) event).tag))));

        assertFalse(bus.post(new StandIn("d1"), zombieKey), "no listener cancels, so the post result stays false");
        assertEquals(List.of("main:d1", "zombie:d1"), seen,
                "mainBus listeners run first, then the keyed listener for the routed key");

        seen.clear();
        bus.post(new StandIn("d2"), skeletonKey);
        assertEquals(List.of("main:d2"), seen,
                "a key with no registered listeners only reaches mainBus listeners");
    }

    @Test
    void blockBrokenWrapperPayloadSurvivesRoundTripOnTheNeutralBus() {
        List<Object> seen = new ArrayList<>();
        EventBusJS<Object, Object> bus = cast(BlockEvents.BROKEN);
        registered.add(new Registered(bus.bus(), bus.bus().listen(seen::add)));

        Object payload = new StandIn("broken");
        bus.post(payload, new Object());

        assertEquals(1, seen.size());
        assertSame(payload, seen.get(0), "the wrapper payload instance is delivered untouched");
    }

    @Test
    void modificationFamilyIsPostedObjectModeAndNotCancellable() {
        AtomicInteger count = new AtomicInteger();
        EventBusJS<Object, Void> bus = cast(ItemEvents.MODIFICATION);
        assertFalse(bus.canCancel(), "posted-object modification buses are not cancellable (ticket 39 contract)");
        assertFalse(bus.canDispatch());
        registered.add(new Registered(bus.bus(), bus.bus().listen(event -> count.incrementAndGet())));

        bus.post(new StandIn("plan"));

        assertEquals(1, count.get());
    }

    @Test
    void goalFamilyPostRegisterReachesRegisteredListeners() {
        List<Object> seen = new ArrayList<>();
        EventBusJS<Object, Void> bus = cast(GoalEvents.REGISTER);
        registered.add(new Registered(bus.bus(), bus.bus().listen(seen::add)));

        GoalEvents.postRegister();

        assertEquals(1, seen.size(), "the production posting site delivers one GoalRegisterEventJS payload");
        assertEquals("GoalRegisterEventJS", seen.get(0).getClass().getSimpleName());
    }

    @Test
    void capabilityFamilyAcceptsRegistrationAndJsCleanupSparesBridgeListeners() {
        List<Object> seen = new ArrayList<>();
        EventBusJS<Object, Void> bus = cast(CapabilityEvents.REGISTER);
        registered.add(new Registered(bus.bus(), bus.bus().listen(seen::add)));

        bus.post(new com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS());
        assertEquals(1, seen.size());

        // Ownership split (documented on EventBusJS.bus()): script reload cleanup
        // (clearTokens) only unregisters JS-registered listeners; Java listeners registered
        // by a platform bridge are owned by that adapter and survive the script reload sweep.
        // The JS-side cleanup path is exercised end to end by
        // Ticket24GameplayEventReloadLifecycleTest with real Graal listeners.
        CapabilityEvents.GROUP.clearListeners(ScriptType.STARTUP);
        bus.post(new com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS());
        assertEquals(2, seen.size(),
                "bridge-owned Java listeners are not swept by the JS reload cleanup (adapter owns them)");
    }
}
//?}
