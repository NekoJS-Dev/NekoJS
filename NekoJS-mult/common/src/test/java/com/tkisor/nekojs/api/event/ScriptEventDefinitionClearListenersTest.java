package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.eventbus.EventBusImpl;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the BUG-B3 cross-bucket sweep in
 * {@link ScriptEventDefinition#clearListeners(ScriptType)}: listeners registered from a script
 * of one ScriptType (e.g. STARTUP) must be cleared even when clearListeners is invoked with a
 * different ScriptType (e.g. SERVER), because JS listener tokens are bucketed by the *registering*
 * script's ScriptType, not the event's target type (TEST-1b).
 *
 * <p>Uses reflection to populate {@code EventBusJS.tokensByType} across multiple buckets — the only
 * mutation path is the Graal-backed {@code execute(Value...)}, which would pull in a live Context.
 */
class ScriptEventDefinitionClearListenersTest {

    @BeforeEach
    void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void clearListenersSweepsAllScriptTypeBuckets() throws Exception {
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        EventBusJS<String, Object> jsBus = new EventBusJS<>(bus);

        // Inject one real listener token into each of two buckets.
        injectToken(jsBus, ScriptType.STARTUP, bus.listen(s -> {}));
        injectToken(jsBus, ScriptType.SERVER, bus.listen(s -> {}));
        assertEquals(2, totalTokens(jsBus), "precondition: two buckets populated");
        assertFalse(((EventBusImpl<?>) bus).isEmpty());

        ScriptEventDefinition def = new ScriptEventDefinition(
                "grp", "evt", ScriptType.SERVER, "java.lang.String", "script.js", jsBus, () -> {});
        def.clearListeners(ScriptType.SERVER);

        assertEquals(0, totalTokens(jsBus), "clearListeners must sweep ALL buckets (BUG-B3 fix)");
        assertTrue(((EventBusImpl<?>) bus).isEmpty(), "underlying bus must have no listeners left");
    }

    @Test
    void clearListenersByScriptIdClearsOnlyThatScript() throws Exception {
        EventBus<String> bus = new EventBusImpl<>(String.class, null);
        EventBusJS<String, Object> jsBus = new EventBusJS<>(bus);
        injectToken(jsBus, ScriptType.SERVER, "alpha.js", bus.listen(s -> {}));
        injectToken(jsBus, ScriptType.SERVER, "beta.js", bus.listen(s -> {}));

        ScriptEventDefinition def = new ScriptEventDefinition(
                "grp", "evt", ScriptType.SERVER, "java.lang.String", "alpha.js", jsBus, () -> {});
        def.clearListeners(ScriptType.SERVER, "alpha.js");

        assertEquals(1, totalTokens(jsBus), "only the alpha.js listener should be removed");
    }

    // ---- reflection helpers (tokensByType + private ScriptEventListenerToken record) ----

    @SuppressWarnings("unchecked")
    private static <E> void injectToken(EventBusJS<E, ?> jsBus, ScriptType type,
                                        EventListenerToken<E> token) throws Exception {
        injectToken(jsBus, type, "script.js", token);
    }

    @SuppressWarnings("unchecked")
    private static <E> void injectToken(EventBusJS<E, ?> jsBus, ScriptType type, String scriptId,
                                        EventListenerToken<E> token) throws Exception {
        Map<ScriptType, List<Object>> map = tokensByType(jsBus);
        Class<?> recordClass = Class.forName("com.tkisor.nekojs.api.event.EventBusJS$ScriptEventListenerToken");
        Constructor<?> ctor = recordClass.getDeclaredConstructor(EventListenerToken.class, String.class);
        ctor.setAccessible(true);
        Object entry = ctor.newInstance(token, scriptId);
        map.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(entry);
    }

    @SuppressWarnings("unchecked")
    private static int totalTokens(EventBusJS<?, ?> jsBus) throws Exception {
        int count = 0;
        for (List<Object> bucket : tokensByType(jsBus).values()) {
            count += bucket.size();
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static Map<ScriptType, List<Object>> tokensByType(EventBusJS<?, ?> jsBus) throws Exception {
        Field f = EventBusJS.class.getDeclaredField("tokensByType");
        f.setAccessible(true);
        return (Map<ScriptType, List<Object>>) f.get(jsBus);
    }
}
