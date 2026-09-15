package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.eventbus.EventBusBase;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 14 AC1：冻结 {@link EventBusJS} 的<strong>外部行为</strong>——注册、remove、
 * dispatch、priority、cancel、side 记账与 reload 清理——以并发 stress fixture
 * （复用 {@code EventBusConcurrentStressTest} 的形状）钉住：
 *
 * <ul>
 *   <li>并发 mutation（reload 线程清理 mirror / owner 线程注册）与 post 之后，总线最终
 *       回到空状态（JS 侧 mirror 与底层 bus 都空）；</li>
 *   <li>没有任何事件被同一监听器注册重复 dispatch（每次投递对每个已激活注册至多送达一次）；</li>
 *   <li>priority 顺序、可取消语义、dispatch key 定向、按 scriptId 的 remove 保持既有契约。</li>
 * </ul>
 *
 * <p>线程模型与生产一致（票 07 契约）：注册与监听器回调都只发生在 owner 线程（Graal
 * 单线程约束——任何 {@code Value} 操作都会进入所属 Context，非 owner 线程调用
 * {@code execute} 会被 Graal 拒绝）；reload 清理（{@code clearTokens}）是纯 host 侧
 * 记账操作，可与注册/post 并发——这正是 {@link EventBusJS} 类注释声明的
 * 「注册（脚本加载线程）与 post 迭代并发」镜像竞态面。
 */
class EventBusJSExternalBehaviorStressTest {

    /** 事件账本：host 对象（脚本回调内调用），按 eventId 记录每个监听器的送达次数。 */
    public static final class DeliveryLedger {
        private final Map<Integer, Map<Integer, AtomicInteger>> deliveries = new ConcurrentHashMap<>();

        /** JS 侧调用：ledger.record(event.getId(), LISTENER_TAG)。 */
        public synchronized void record(int eventId, int listenerTag) {
            deliveries.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(listenerTag, k -> new AtomicInteger())
                    .incrementAndGet();
        }

        /** 某事件对某监听器的送达次数（未送达为 0）。 */
        synchronized int count(int eventId, int listenerTag) {
            Map<Integer, AtomicInteger> perListener = deliveries.get(eventId);
            if (perListener == null) return 0;
            AtomicInteger n = perListener.get(listenerTag);
            return n == null ? 0 : n.get();
        }

        synchronized Set<Integer> listenerTags() {
            Set<Integer> tags = new java.util.TreeSet<>();
            for (Map<Integer, AtomicInteger> perListener : deliveries.values()) {
                tags.addAll(perListener.keySet());
            }
            return tags;
        }
    }

    /** 带唯一 id 的载荷事件（重复 dispatch 检测靠它）。 */
    public static final class Payload {
        private final int id;

        Payload(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    /** priority 顺序记录器（host 对象，JS 侧 add）。 */
    public static final class OrderSink {
        final List<String> order = new ArrayList<>();

        public void add(String tag) {
            order.add(tag);
        }
    }

    private Context context;
    private DeliveryLedger ledger;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
        ledger = new DeliveryLedger();
        context.getBindings("js").putMember("ledger", ledger);
    }

    @AfterEach
    void tearDown() {
        ScriptContextRegistry.unbind(context);
        context.close();
    }

    // ---- 顺序前置：单线程外部行为契约（priority / cancel / dispatch / remove / 空态） ----

    @Test
    void priorityOrderAndReloadStyleSweepFrozen() {
        EventBusJS<Payload, Void> bus = EventBusJS.of(Payload.class);
        OrderSink sink = new OrderSink();
        context.getBindings("js").putMember("sink", sink);

        bus.execute(context.eval("js", "'LOWEST'"), context.eval("js",
                "(function (event) { sink.add('low'); })"));
        bus.execute(context.eval("js", "'HIGHEST'"), context.eval("js",
                "(function (event) { sink.add('high'); })"));
        bus.execute(context.eval("js",
                "(function (event) { sink.add('normal'); })"));

        bus.post(new Payload(0));
        bus.post(new Payload(1));
        // HIGHEST → NORMAL → LOWEST（CommonPriority 降序）
        assertEquals(List.of("high", "normal", "low", "high", "normal", "low"), sink.order,
                "listeners must run in priority order for every post");

        // reload 式清扫：按 ScriptType 清空 mirror 与底层 bus，回到空态
        assertTrue(bus.hasListeners(), "listener registered -> hasListeners");
        bus.clearTokens(ScriptType.SERVER);
        assertFalse(bus.hasListeners(), "reload-style clear must empty the JS-side mirror");
        assertTrue(((EventBusBase<?, ?>) bus.bus()).isEmpty(), "underlying bus must be empty too");

        int before = sink.order.size();
        bus.post(new Payload(2));
        assertEquals(before, sink.order.size(), "no listener may fire after the sweep");
    }

    @Test
    void cancellableBusReturnTrueCancelsAndStopsLaterListeners() {
        EventBusJS<Payload, Void> bus = EventBusJS.of(Payload.class, true);
        context.eval("js", "globalThis.cancel = true;");
        bus.execute(context.eval("js", "'HIGHEST'"), context.eval("js",
                "(function (event) { ledger.record(event.getId(), 1); return globalThis.cancel; })"));
        bus.execute(context.eval("js",
                "(function (event) { ledger.record(event.getId(), 2); })"));

        assertTrue(bus.post(new Payload(10)), "cancellable bus reports the cancellation");
        assertEquals(1, ledger.count(10, 1), "cancelling listener was invoked");
        assertEquals(0, ledger.count(10, 2), "cancellation must stop later (lower priority) listeners");

        context.eval("js", "globalThis.cancel = false;");
        assertFalse(bus.post(new Payload(11)), "post returns false when nobody cancels");
        assertEquals(1, ledger.count(11, 1));
        assertEquals(1, ledger.count(11, 2), "both listeners run when not cancelled");
    }

    @Test
    void dispatchKeyIsolationAndRegisteredKeysFrozen() {
        EventBusJS<Payload, String> dispatch = EventBusJS.of(Payload.class, false, DispatchKey.string());
        dispatch.execute(context.eval("js", "'chat'"), context.eval("js",
                "(function (event) { ledger.record(event.getId(), 3); })"));

        dispatch.post(new Payload(11), "chat");
        dispatch.post(new Payload(12), "other");
        assertEquals(1, ledger.count(11, 3), "keyed listener receives its key exactly once");
        assertEquals(0, ledger.count(12, 3), "keyed listener must not receive other keys");
        assertEquals(Set.of("chat"), dispatch.registeredKeys(),
                "registeredKeys exposes the keyed registrations");

        dispatch.clearTokens(ScriptType.SERVER);
        assertTrue(dispatch.registeredKeys().isEmpty(), "sweep must remove keyed registrations");
    }

    @Test
    void removalByScriptIdLeavesOtherScriptsIntact() {
        EventBusJS<Payload, Void> bus = EventBusJS.of(Payload.class);
        String scriptA = "scripts/a.js";
        String scriptB = "scripts/b.js";
        String previous = ScriptContextRegistry.switchCurrentScriptId(context, scriptA);
        try {
            bus.execute(context.eval("js",
                    "(function (event) { ledger.record(event.getId(), 1); })"));
            ScriptContextRegistry.switchCurrentScriptId(context, scriptB);
            bus.execute(context.eval("js",
                    "(function (event) { ledger.record(event.getId(), 2); })"));
        } finally {
            ScriptContextRegistry.restoreCurrentScriptId(context, previous);
        }

        bus.clearTokens(ScriptType.SERVER, scriptA);
        bus.post(new Payload(20));
        assertEquals(0, ledger.count(20, 1), "removed script's listener must not fire");
        assertEquals(1, ledger.count(20, 2), "other script's listener stays registered exactly once");

        bus.clearTokens(ScriptType.SERVER, scriptB);
        assertFalse(bus.hasListeners(), "final per-script sweep empties the bus");
    }

    @Test
    void wrongArgShapesAreDiagnosed() {
        EventBusJS<Payload, Void> bus = EventBusJS.of(Payload.class);
        assertThrows(IllegalArgumentException.class, () -> bus.execute(),
                "no args must be rejected");
        Value priorityOnly = context.eval("js", "'HIGH'");
        assertThrows(IllegalArgumentException.class, () -> bus.execute(priorityOnly),
                "priority without listener must be rejected");
    }

    // ---- 并发 stress：owner 注册/post 与 reload 线程清理竞争 ----

    /**
     * 形状沿用 {@code EventBusConcurrentStressTest}：owner 线程交替「注册新监听器 +
     * post」（注册进 mirror 的 {@code compute} 与 bus 的 CopyOnWriteArrayList），同时
     * reload 线程并发执行按 scriptId 的清理（单文件 reload）与整型清扫（全量 reload）——
     * 三方竞争同一 {@code tokensByType} bin 锁与 bus 监听器列表。
     *
     * <p>每个注册有唯一 tag（预创建的监听器数组），“无重复 dispatch”因此可精确断言：
     * 任意 (event, 注册) 的送达次数 <= 1。收尾按 scriptId 清理 + 全类型清扫后，
     * mirror 与底层 bus 必须同时回到空态。
     */
    @Test
    void concurrentMutationAndPostReachesEmptyStateWithoutDuplicateDispatch() throws Exception {
        EventBusJS<Payload, Void> bus = EventBusJS.of(Payload.class);
        int registrations = 400;
        int posts = 2000;
        int cleanerThreads = 3;
        String scriptId = "scripts/stress.js";
        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
        ExecutorService pool = Executors.newFixedThreadPool(cleanerThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            // 监听器在 owner 线程预创建：每个注册一个唯一 tag（重复 dispatch 检测的键）
            Value listeners = context.eval("js", """
                    Array.from({ length: %d }, (_, i) =>
                        (function (event) { ledger.record(event.getId(), i); }))
                    """.formatted(registrations));

            // cleaner 线程：纯 host 侧清理（不进入 Graal），与 owner 的注册/post 竞争
            for (int t = 0; t < cleanerThreads; t++) {
                final boolean byScriptId = t % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 400; i++) {
                        if (byScriptId) {
                            bus.clearTokens(ScriptType.SERVER, scriptId);
                        } else {
                            bus.clearTokens(ScriptType.SERVER);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();

            // owner：注册 → post 交替；注册走 execute()（mirror compute），post 在编译
            // 快照上迭代当前已激活监听器
            int registered = 0;
            for (int i = 0; i < posts; i++) {
                if (registered < registrations && i % 5 == 0) {
                    bus.execute(listeners.getArrayElement(registered));
                    registered++;
                }
                bus.post(new Payload(i));
            }
            assertEquals(registrations, registered, "all planned registrations were issued");

            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS); // cleaner 线程抛错在此失败
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

            // 收尾：按 scriptId 清理 + 全类型清扫，最终回到空态
            bus.clearTokens(ScriptType.SERVER, scriptId);
            for (ScriptType type : ScriptType.all()) {
                bus.clearTokens(type);
            }
            assertFalse(bus.hasListeners(), "post-stress sweep must empty the JS-side mirror");
            assertTrue(((EventBusBase<?, ?>) bus.bus()).isEmpty(),
                    "post-stress sweep must empty the underlying bus");

            // 无重复 dispatch：任意 (event, listener 注册) 组合的送达次数 <= 1
            // （mirror 双记账 / bus 重复挂载会让同一次 post 送达两次）
            for (int eventId = 0; eventId < posts; eventId++) {
                for (int tag : ledger.listenerTags()) {
                    int n = ledger.count(eventId, tag);
                    assertTrue(n <= 1,
                            "duplicate dispatch: event " + eventId + " delivered " + n
                                    + "x to listener registration " + tag);
                }
            }
            // 非平凡下界：整轮至少要有一次真实送达（逐对硬下界会因 cleaner 理论上
            // 赢得每个注册→post 窗口而引入 flaky，运行级守卫足以排除零投递回归）
            assertFalse(ledger.listenerTags().isEmpty(),
                    "stress run must deliver at least one event (zero-delivery regression)");
        } finally {
            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
        }
    }
}
