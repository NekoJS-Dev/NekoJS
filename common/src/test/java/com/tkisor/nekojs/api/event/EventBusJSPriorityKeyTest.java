package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEFECT-D6 回归：dispatch 总线上，第一个字符串参数应只在「后面还有 key + listener」
 * 时才被解析为 priority；否则它就是 dispatch key。
 *
 * <p>{@code listen("HIGH", listener)} 在 dispatch 总线上必须注册 key {@code "HIGH"}，
 * 而不是把 {@code "HIGH"} 当成 priority 并注册到 main bus。
 */
class EventBusJSPriorityKeyTest {

    private Context context;
    private EventBusJS<String, String> dispatchBus;
    private AtomicInteger delivered;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
        dispatchBus = EventBusJS.of(String.class, false, DispatchKey.string());
        delivered = new AtomicInteger();
        context.getBindings("js").putMember("delivered", delivered);
    }

    @AfterEach
    void tearDown() {
        ScriptContextRegistry.unbind(context);
        context.close();
    }

    @Test
    void dispatchBusTreatsSinglePriorityNamedStringAsDispatchKey() {
        Value listener = context.eval("js", "(event) => { delivered.incrementAndGet(); }");
        Boolean ok = (Boolean) dispatchBus.execute(context.eval("js", "'HIGH'"), listener);

        assertTrue(ok);
        assertTrue(dispatchBus.registeredKeys().contains("HIGH"),
                "dispatch bus must register key HIGH when called as listen('HIGH', listener)");
        dispatchBus.post("event", "HIGH");
        assertEquals(1, delivered.get(),
                "listener registered for key HIGH must receive posts for HIGH");
        dispatchBus.post("event", "OTHER");
        assertEquals(1, delivered.get(),
                "listener registered for key HIGH must NOT receive posts for OTHER (main-bus leak)");
    }

    @Test
    void dispatchBusSinglePriorityNamedArgThrowsListenerAfterPriority() {
        Value high = context.eval("js", "'HIGH'");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dispatchBus.execute(high));
        assertEquals("EventBus requires a listener after priority", ex.getMessage(),
                "listen('HIGH') on a dispatch bus must keep the legacy missing-listener error");
    }

    @Test
    void dispatchBusKeepsPrioritySemanticsWhenKeyAndListenerFollowPriority() {
        Value listener = context.eval("js", "(event) => { delivered.incrementAndGet(); }");
        Boolean ok = (Boolean) dispatchBus.execute(
                context.eval("js", "'HIGH'"),
                context.eval("js", "'other'"),
                listener);

        assertTrue(ok);
        assertTrue(dispatchBus.registeredKeys().contains("other"),
                "dispatch bus must register key 'other' for listen('HIGH', 'other', listener)");
        assertFalse(dispatchBus.registeredKeys().contains("HIGH"),
                "priority-name string must not become a key when a key + listener follow it");
        dispatchBus.post("event", "other");
        assertEquals(1, delivered.get(),
                "keyed listener registered with explicit priority must receive posts for its key");
    }

    @Test
    void nonDispatchBusStillTreatsPriorityNamedStringAsPriority() {
        EventBusJS<String, Void> plainBus = EventBusJS.of(String.class);
        Value listener = context.eval("js", "(event) => { delivered.incrementAndGet(); }");
        Boolean ok = (Boolean) plainBus.execute(context.eval("js", "'HIGH'"), listener);

        assertTrue(ok);
        plainBus.post("event");
        assertEquals(1, delivered.get(),
                "non-dispatch bus must keep parsing 'HIGH' as priority + listener");
    }

    @Test
    void priorityArgOffsetParsesOnlyWithFullKeyAndListenerOnDispatchBus() {
        Value high = context.eval("js", "'HIGH'");
        Value normal = context.eval("js", "'NORMAL'");
        Value key = context.eval("js", "'key'");
        Value listener = context.eval("js", "(event) => {}");

        assertEquals(0, EventBusJS.priorityArgOffset(new Value[]{high, listener}, true),
                "dispatch bus: ('HIGH', listener) must not parse HIGH as priority");
        assertEquals(1, EventBusJS.priorityArgOffset(new Value[]{high, key, listener}, true),
                "dispatch bus: ('HIGH', key, listener) must parse HIGH as priority");
        assertEquals(0, EventBusJS.priorityArgOffset(new Value[]{normal, listener}, true),
                "dispatch bus: ('NORMAL', listener) must not parse NORMAL as priority");
        assertEquals(0, EventBusJS.priorityArgOffset(new Value[]{key, listener}, true),
                "dispatch bus: non-priority key string must never be parsed as priority");
        assertEquals(1, EventBusJS.priorityArgOffset(new Value[]{high, listener}, false),
                "non-dispatch bus: ('HIGH', listener) must keep parsing HIGH as priority");
    }

    /**
     * 静态工厂语义回归：EventBusFactory 退役后，接口静态工厂造出的总线必须保持
     * 原有语义——普通总线不可取消、可取消总线按谓词结果取消并短路、按 key 分发。
     */
    @Test
    void staticFactoryBusSemanticsUnchanged() {
        EventBus<String> plain = EventBus.create(String.class);
        plain.listen(e -> { delivered.incrementAndGet(); });
        assertFalse(plain.post("e"), "plain bus is never cancellable");
        assertEquals(1, delivered.get());

        CancellableEventBus<String> cancellable = CancellableEventBus.create(String.class);
        AtomicInteger afterCancel = new AtomicInteger();
        cancellable.listen(e -> true);
        cancellable.listen(e -> { afterCancel.incrementAndGet(); return false; });
        assertTrue(cancellable.post("e"), "predicate returning true must cancel");
        assertEquals(0, afterCancel.get(), "listeners after a cancelling one must not run");

        CancellableEventBus<String> nonCancelling = CancellableEventBus.create(String.class);
        nonCancelling.listen(e -> false);
        assertFalse(nonCancelling.post("e"), "no cancelling listener means not cancelled");

        DispatchEventBus<String, String> dispatch = DispatchEventBus.create(String.class, DispatchKey.string());
        AtomicInteger keyed = new AtomicInteger();
        dispatch.listen("k", e -> { keyed.incrementAndGet(); });
        dispatch.post("e", "k");
        dispatch.post("e", "other");
        assertEquals(1, keyed.get(), "keyed listener must receive only posts for its own key");
    }
}
