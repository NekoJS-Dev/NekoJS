package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusJSCollectionDispatchTest {
    private Context context;
    private final List<String> delivered = new ArrayList<>();
    private final List<Throwable> reported = new ArrayList<>();

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        context = newContext();
        context.getBindings("js").putMember("delivered", delivered);
        ScriptErrorReporter.set((type, kind, failure) -> reported.add(failure));
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
        ScriptContextRegistry.unbind(context);
        context.close();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void nestedNormalCallbacksContinueAndOuterCollectionRemainsStrict(boolean cancellable, boolean keyed) {
        EventBusJS<String, String> bus = newBus(cancellable, keyed);
        context.getBindings("js").putMember("nestedPost", (ProxyExecutable) arguments ->
                keyed ? bus.post("nested", "route") : bus.post("nested"));
        register(bus, keyed, """
                event => {
                    if (event === 'collection') {
                        nestedPost();
                        delivered.add('outer-resumed');
                    }
                }
                """);
        register(bus, keyed, """
                event => {
                    if (event === 'nested') throw new Error('nested fixture failure');
                    if (event === 'collection') {
                        delivered.add('strict-failure');
                        throw new Error('collection fixture failure');
                    }
                }
                """);
        register(bus, keyed, "event => { delivered.add(event + '-tail'); return false; }");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> bus.postForCollection("collection"));
        assertTrue(failure.getMessage().contains("collection fixture failure"));
        assertEquals(List.of("nested-tail", "outer-resumed", "strict-failure"), delivered);
        assertEquals(1, reported.size(), "only the nested normal failure is reported by the bus");

        assertFalse(keyed ? bus.post("nested", "route") : bus.post("nested"));
        assertEquals("nested-tail", delivered.getLast());
        assertEquals(2, reported.size(), "normal mode is restored after collection aborts");
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void dispatchBusUnkeyedPostAlsoIsolatesNestedNormalDispatch(boolean cancellable) {
        EventBusJS<String, String> bus = newBus(cancellable, true);
        context.getBindings("js").putMember("nestedPost", (ProxyExecutable) arguments -> bus.post("nested"));
        register(bus, true, """
                event => {
                    if (event === 'collection') nestedPost();
                    if (event === 'nested') throw new Error('nested fixture failure');
                }
                """);
        register(bus, true, "event => { delivered.add(event + '-tail'); return false; }");

        assertFalse(bus.postForCollection("collection"));
        assertEquals(List.of("nested-tail", "collection-tail"), delivered);
        assertEquals(1, reported.size());
    }

    @Test
    void nestedCollectionFailureDoesNotMakeTheEnclosingNormalDispatchStrict() {
        EventBusJS<String, String> bus = newBus(false, false);
        context.getBindings("js").putMember("nestedCollection", (ProxyExecutable) arguments ->
                bus.postForCollection("collection"));
        register(bus, false, """
                event => {
                    if (event === 'normal') nestedCollection();
                    if (event === 'collection') throw new Error('collection fixture failure');
                }
                """);
        register(bus, false, "event => { delivered.add(event + '-tail'); }");

        assertFalse(bus.post("normal"));
        assertEquals(List.of("normal-tail"), delivered);
        assertEquals(1, reported.size());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void closedGuestContextAbortsCollectionButNormalDispatchContinues(boolean cancellable, boolean keyed) {
        EventBusJS<String, String> bus = newBus(cancellable, keyed);
        Context closed = newContext();
        try {
            Value callback = closed.eval("js", "event => false");
            if (keyed) bus.execute(closed.eval("js", "'route'"), callback);
            else bus.execute(callback);
        } finally {
            ScriptContextRegistry.unbind(closed);
            closed.close();
        }
        register(bus, keyed, "event => { delivered.add(event + '-tail'); return false; }");

        assertFalse(keyed ? bus.post("normal", "route") : bus.post("normal"));
        assertEquals(List.of("normal-tail"), delivered);
        assertEquals(1, reported.size());
        assertThrows(RuntimeException.class, () -> bus.postForCollection("collection"));
        assertEquals(List.of("normal-tail"), delivered, "strict collection cannot skip a closed listener");
        assertEquals(1, reported.size(), "collection failure belongs to its batch owner");
    }

    @Test
    void fatalErrorsEscapeBothModesAndRestoreDispatchState() {
        EventBusJS<String, String> bus = newBus(false, false);
        AssertionError fatal = new AssertionError("fatal fixture failure");
        bus.bus().listen(event -> {
            if (event.equals("fatal")) throw fatal;
        });
        register(bus, false, """
                event => {
                    if (event === 'normal-failure') throw new Error('normal fixture failure');
                }
                """);
        register(bus, false, "event => { delivered.add(event + '-tail'); }");

        assertSame(fatal, assertThrows(AssertionError.class, () -> bus.postForCollection("fatal")));
        assertSame(fatal, assertThrows(AssertionError.class, () -> bus.post("fatal")));
        assertFalse(bus.post("normal-failure"));
        assertEquals(List.of("normal-failure-tail"), delivered);
        assertEquals(1, reported.size());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void graalHostErrorsEscapeWithoutBeingConvertedToCollectionSuccess(boolean cancellable, boolean keyed) {
        EventBusJS<String, String> bus = newBus(cancellable, keyed);
        AssertionError fatal = new AssertionError("host fatal fixture failure");
        context.getBindings("js").putMember("fatalHost", (ProxyExecutable) arguments -> {
            throw fatal;
        });
        register(bus, keyed, "event => { fatalHost(); }");
        register(bus, keyed, "event => { delivered.add(event + '-tail'); return false; }");

        assertSame(fatal, assertThrows(AssertionError.class, () -> bus.postForCollection("collection")));
        assertSame(fatal, assertThrows(AssertionError.class,
                () -> { if (keyed) bus.post("normal", "route"); else bus.post("normal"); }));
        assertTrue(delivered.isEmpty());
        assertTrue(reported.isEmpty());
    }

    @Test
    void hostInterruptIsPreservedWhileNormalCallbacksContinueAndCollectionAborts() {
        EventBusJS<String, String> bus = newBus(false, false);
        context.getBindings("js").putMember("interruption", new InterruptingHost());
        register(bus, false, "event => { interruption.fail(); }");
        register(bus, false, "event => { delivered.add(event + '-tail'); }");

        try {
            assertFalse(bus.post("normal"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of("normal-tail"), delivered);
            assertEquals(1, reported.size());
            Thread.interrupted();
            assertThrows(RuntimeException.class, () -> bus.postForCollection("collection"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of("normal-tail"), delivered);
            assertEquals(1, reported.size());
        } finally {
            Thread.interrupted();
        }
    }

    public static final class InterruptingHost {
        public void fail() throws InterruptedException {
            throw new InterruptedException("interrupt fixture failure");
        }
    }

    private static Context newContext() {
        Context created = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(created, ScriptType.SERVER);
        return created;
    }

    private static EventBusJS<String, String> newBus(boolean cancellable, boolean keyed) {
        return EventBusJS.of(String.class, cancellable,
                keyed ? DispatchKey.of(String.class, event -> "route") : null);
    }

    private void register(EventBusJS<String, String> bus, boolean keyed, String source) {
        Value callback = context.eval("js", source);
        if (keyed) bus.execute(context.eval("js", "'route'"), callback);
        else bus.execute(callback);
    }
}
