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
 * Pins down the observable behavior of {@link EventBusJS}: registration, remove, dispatch,
 * priority, cancel, side bookkeeping and reload cleanup. A concurrent stress fixture (shaped like
 * {@code EventBusConcurrentStressTest}) checks three things:
 *
 * <ul>
 *   <li>after concurrent mutation (a reload thread clearing the mirror, the owner thread
 *       registering) and posts, the bus ends up empty again, both the JS-side mirror and the
 *       underlying bus;</li>
 *   <li>no event is dispatched twice to the same listener registration: each post reaches each
 *       active registration at most once;</li>
 *   <li>priority order, cancellable semantics, dispatch key targeting and remove by scriptId keep
 *       their existing behavior.</li>
 * </ul>
 *
 * <p>The thread model matches production: registration and listener callbacks only happen on the
 * owner thread. Graal runs single-threaded, every {@code Value} operation enters its owning
 * Context, and Graal rejects {@code execute} from a non-owner thread. Reload cleanup
 * ({@code clearTokens}) only touches host-side bookkeeping, so it can run at the same time as
 * registration and posts; that is the mirror race between registration on the script-load thread
 * and post iteration which the {@link EventBusJS} class comment describes.
 */
class EventBusJSExternalBehaviorStressTest {

    /** Delivery ledger: a host object called from script callbacks; counts deliveries per event and listener. */
    public static final class DeliveryLedger {
        private final Map<Integer, Map<Integer, AtomicInteger>> deliveries = new ConcurrentHashMap<>();

        /** Called from JS as ledger.record(event.getId(), LISTENER_TAG). */
        public synchronized void record(int eventId, int listenerTag) {
            deliveries.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(listenerTag, k -> new AtomicInteger())
                    .incrementAndGet();
        }

        /** Number of times one listener saw one event; 0 when it was never delivered. */
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

    /** Payload event with a unique id; duplicate dispatch detection keys on it. */
    public static final class Payload {
        private final int id;

        Payload(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    /** Records the call order for priority checks; a host object, called from JS through add. */
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

    // ---- Ordering first: single-threaded behavior (priority, cancel, dispatch, remove, empty state) ----

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
        // HIGHEST -> NORMAL -> LOWEST (descending CommonPriority)
        assertEquals(List.of("high", "normal", "low", "high", "normal", "low"), sink.order,
                "listeners must run in priority order for every post");

        // Reload-style sweep: clear the mirror and the underlying bus for one ScriptType, back to empty
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

    // ---- Concurrency stress: owner registration and posts race the reload cleaners ----

    /**
     * Shaped like {@code EventBusConcurrentStressTest}: the owner thread alternates between
     * registering a new listener and posting (registration goes into the mirror's {@code compute}
     * and into the bus's CopyOnWriteArrayList), while reload threads concurrently clear by
     * scriptId (single-file reload) and sweep whole types (full reload). All three contend on the
     * same {@code tokensByType} bin lock and on the bus listener list.
     *
     * <p>Every registration has a unique tag (the pre-created listener array), so "no duplicate
     * dispatch" can be asserted exactly: the delivery count of any (event, registration) pair is
     * at most 1. After clearing by scriptId and sweeping all types, the mirror and the underlying
     * bus must both be empty.
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
            // Pre-create the listeners on the owner thread: one unique tag per registration, the duplicate key
            Value listeners = context.eval("js", """
                    Array.from({ length: %d }, (_, i) =>
                        (function (event) { ledger.record(event.getId(), i); }))
                    """.formatted(registrations));

            // Cleaner threads: host-side clearing only (never enters Graal), racing the owner's registrations and posts
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

            // Owner: alternate registration and posts; registration goes through execute() (mirror compute),
            // and a post walks the active listeners over a compiled snapshot
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
                f.get(60, TimeUnit.SECONDS); // A throw from any cleaner thread fails here
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

            // Wrap up: clear by scriptId, sweep all types, and end empty
            bus.clearTokens(ScriptType.SERVER, scriptId);
            for (ScriptType type : ScriptType.all()) {
                bus.clearTokens(type);
            }
            assertFalse(bus.hasListeners(), "post-stress sweep must empty the JS-side mirror");
            assertTrue(((EventBusBase<?, ?>) bus.bus()).isEmpty(),
                    "post-stress sweep must empty the underlying bus");

            // No duplicate dispatch: the delivery count of any (event, listener registration) pair is at most 1.
            // Double bookkeeping in the mirror or a listener mounted twice would deliver one post twice.
            for (int eventId = 0; eventId < posts; eventId++) {
                for (int tag : ledger.listenerTags()) {
                    int n = ledger.count(eventId, tag);
                    assertTrue(n <= 1,
                            "duplicate dispatch: event " + eventId + " delivered " + n
                                    + "x to listener registration " + tag);
                }
            }
            // A non-trivial lower bound: the whole run must deliver at least once. A per-pair bound would be
            // flaky because a cleaner can in theory win every register-to-post window; a run-level guard
            // is enough to catch a zero-delivery regression.
            assertFalse(ledger.listenerTags().isEmpty(),
                    "stress run must deliver at least one event (zero-delivery regression)");
        } finally {
            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
        }
    }
}
