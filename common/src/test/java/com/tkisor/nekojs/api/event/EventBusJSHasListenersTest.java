package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the fallback branch of {@link EventBusJS#hasListeners()}.
 *
 * <p>{@code hasListeners()} first checks the {@code tokensByType} mirror, which holds the
 * listeners registered from JS through {@link EventBusJS#execute}. To stay in sync with direct
 * Java registrations that bypass {@code execute} (tests, bridge code), it then falls back to the
 * underlying bus ({@code EventBusBase.isEmpty()}). This test registers a Java listener through
 * {@link EventBusJS#bus()} directly; on that path {@code tokensByType} stays empty, which is
 * exactly the fallback branch this test covers.
 *
 * <p>{@link EventBusJS} keeps listeners in buckets per {@code ScriptType}. The registration and
 * cleanup paths touch {@code ScriptType.<clinit>}, which needs {@code Platform} to be
 * initialized, so {@code @BeforeAll} initializes the platform fixture first (the same way
 * {@code TypeScriptNoopIrGoldenTest} does) and the test does not depend on execution order.
 */
class EventBusJSHasListenersTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** No listeners at all (mirror and underlying bus both empty) returns false. */
    @Test
    void hasListenersIsFalseWithoutAnyListener() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        assertFalse(bus.hasListeners());
    }

    /**
     * Registers a Java listener directly through {@code bus().listen(Consumer)}, bypassing
     * {@code execute()}, so the {@code tokensByType} mirror stays empty. The
     * {@code EventBusBase.isEmpty()} fallback must still report true.
     */
    @Test
    void hasListenersIsTrueForDirectJavaListener() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        Consumer<String> listener = ignored -> { };
        bus.bus().listen(listener);
        assertTrue(bus.hasListeners(), "Java 直接注册的监听器应被 isEmpty() 兜底分支发现");
    }

    /** The fallback branch follows the underlying bus: after unregistering it reports false again. */
    @Test
    void hasListenersReturnsFalseAfterDirectListenerUnregistered() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        EventListenerToken<String> token = bus.bus().listen(ignored -> { });
        assertTrue(bus.hasListeners());
        assertTrue(bus.bus().unregister(token), "unregister 应成功");
        assertFalse(bus.hasListeners());
    }

    @Test
    void pendingActivationFailureLeavesTheOldListenerRouteIntact() {
        EventBusJS<String, String> bus = EventBusJS.of(String.class, false, DispatchKey.string());
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            Value listener = context.eval("js", "(function () {})");
            EventBusJS.PendingListener old = new EventBusJS.PendingListener(
                    bus, (byte) 0, listener, null, ScriptType.SERVER, "old");
            old.activate();

            Value invalidKey = context.eval("js", "({ notAString: true })");
            EventBusJS.PendingListener candidate = new EventBusJS.PendingListener(
                    bus, (byte) 0, listener, invalidKey, ScriptType.SERVER, "candidate");
            assertThrows(Throwable.class,
                    () -> bus.commitPendingListeners(ScriptType.SERVER, List.of(candidate)));
            assertTrue(bus.hasListeners(), "activation failure must not clear the old route");
            old.deactivate();
            ScriptContextRegistry.unbind(context);
        }
    }
}
