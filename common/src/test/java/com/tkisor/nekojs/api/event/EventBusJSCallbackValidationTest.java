package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusJSCallbackValidationTest {
    private Context context;

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
    }

    @AfterEach
    void tearDown() {
        ScriptContextRegistry.unbind(context);
        context.close();
    }

    @Test
    void rawJavaScriptSubscriptionRejectsNonCallableBeforeRegistering() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, false);
        context.getBindings("js").putMember("listen", bus);

        PolyglotException failure = assertThrows(PolyglotException.class,
                () -> context.eval("js", "listen(42)"));

        assertTrue(failure.getMessage().contains("[NEKO-4057]"));
        assertFalse(bus.hasListeners(), "rejected callback must not create a listener");
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void invalidCallbacksAreRejectedAcrossSubscriptionOverloads(boolean cancellable, boolean keyed) {
        EventBusJS<String, String> bus = newBus(cancellable, keyed);
        context.getBindings("js").putMember("listen", bus);
        List<String> shapes = keyed
                ? List.of("%s", "'HIGH', %s", "'hIgH', 'route', %s")
                : List.of("%s", "'hIgH', %s");

        for (String callback : List.of("42", "null", "undefined", "false", "'not a callback'", "({})")) {
            for (String shape : shapes) {
                String source = "listen(" + shape.formatted(callback) + ")";
                PolyglotException failure = assertThrows(PolyglotException.class,
                        () -> context.eval("js", source), source);
                assertTrue(failure.getMessage().contains("[NEKO-4057]"), source);
                assertFalse(bus.hasListeners(), source);
                assertTrue(bus.registeredKeys().isEmpty(), "rejected callbacks must not reserve dispatch keys");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void validCallbacksKeepPriorityCancellationAndSourceCleanup(boolean cancellable, boolean keyed) {
        EventBusJS<String, String> bus = newBus(cancellable, keyed);
        context.getBindings("js").putMember("listen", bus);
        context.getBindings("js").putMember("cancellable", cancellable);
        context.eval("js", "globalThis.seen = [];");
        String key = keyed ? "'route', " : "";
        String previous = ScriptContextRegistry.switchCurrentScriptId(context, "scripts/low.js");
        try {
            assertTrue(context.eval("js", "listen('LOWEST', " + key
                    + "event => { seen.push('low:' + event); return false; })").asBoolean());
            ScriptContextRegistry.switchCurrentScriptId(context, "scripts/high.js");
            assertTrue(context.eval("js", "listen('HIGHEST', " + key
                    + "event => { seen.push('high:' + event); return cancellable && event === 'cancel'; })").asBoolean());
        } finally {
            ScriptContextRegistry.restoreCurrentScriptId(context, previous);
        }

        assertFalse(post(bus, keyed, "normal"));
        assertEquals("high:normal,low:normal", context.eval("js", "seen.join(',')").asString());
        context.eval("js", "seen.length = 0");
        assertEquals(cancellable, post(bus, keyed, "cancel"));
        assertEquals(cancellable ? "high:cancel" : "high:cancel,low:cancel",
                context.eval("js", "seen.join(',')").asString());

        bus.clearTokens(ScriptType.CLIENT);
        assertTrue(bus.hasListeners(), "cleanup must remain local to the registering ScriptType");
        bus.clearTokens(ScriptType.SERVER, "scripts/high.js");
        context.eval("js", "seen.length = 0");
        assertFalse(post(bus, keyed, "after"));
        assertEquals("low:after", context.eval("js", "seen.join(',')").asString());
        bus.clearTokens(ScriptType.SERVER);
        assertFalse(bus.hasListeners());
        assertTrue(bus.registeredKeys().isEmpty());
        assertFalse(post(bus, keyed, "cleared"));
        assertEquals("low:after", context.eval("js", "seen.join(',')").asString());
    }

    @Test
    void priorityNamedDispatchKeyRemainsAKeyWithoutAnExplicitPriority() {
        EventBusJS<String, String> bus = newBus(false, true);
        context.getBindings("js").putMember("listen", bus);
        context.eval("js", "globalThis.seen = []; listen('HIGH', event => seen.push(event));");

        assertEquals(Set.of("HIGH"), bus.registeredKeys());
        assertFalse(bus.post("matching", "HIGH"));
        assertFalse(bus.post("other", "route"));
        assertEquals("matching", context.eval("js", "seen.join(',')").asString());
        bus.clearTokens(ScriptType.SERVER);
        assertFalse(bus.hasListeners());
    }

    @Test
    void rawSubscriptionKeepsSelectedCallbackAndExistingAsyncAcceptance() {
        EventBusJS<String, String> bus = newBus(false, false);
        context.getBindings("js").putMember("listen", bus);
        context.eval("js", "globalThis.seen = [];");

        assertTrue(context.eval("js", "listen(event => seen.push('sync:' + event), 42)").asBoolean());
        assertTrue(context.eval("js", "listen(async event => { seen.push('async:' + event); })").asBoolean());
        assertFalse(bus.post("event"));
        assertEquals("sync:event,async:event", context.eval("js", "seen.join(',')").asString());
        bus.clearTokens(ScriptType.SERVER);
        assertFalse(bus.hasListeners());
    }

    private static EventBusJS<String, String> newBus(boolean cancellable, boolean keyed) {
        return EventBusJS.of(String.class, cancellable, keyed ? DispatchKey.string() : null);
    }

    private static boolean post(EventBusJS<String, String> bus, boolean keyed, String event) {
        return keyed ? bus.post(event, "route") : bus.post(event);
    }
}
