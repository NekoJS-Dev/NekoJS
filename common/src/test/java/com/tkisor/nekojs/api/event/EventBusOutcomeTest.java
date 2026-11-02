package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusOutcomeTest {
    private Context context;
    private final List<String> delivered = new ArrayList<>();
    private final List<Throwable> reported = new ArrayList<>();

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
        context.getBindings("js").putMember("delivered", delivered);
        ScriptErrorReporter.set((type, kind, failure) -> reported.add(failure));
    }

    @AfterEach
    void tearDown() {
        ScriptErrorReporter.set(ScriptErrorReporter.Reporter.NOOP);
        ScriptContextRegistry.unbind(context);
        context.close();
    }

    @Test
    void checkedFailureContinuesListenersAndDoesNotLeakIntoNextDispatch() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, false);
        bus.execute(context.eval("js", "event => { if (event === 'bad') throw new Error('fixture'); }"));
        bus.execute(context.eval("js", "event => { delivered.add(event); }"));

        assertFalse(bus.postChecked("bad"));
        assertEquals(List.of("bad"), delivered);
        assertEquals(1, reported.size());
        assertTrue(bus.postChecked("good"));
        assertEquals(List.of("bad", "good"), delivered);
        assertEquals(1, reported.size(), "stale reported errors do not affect a new dispatch");
        assertFalse(bus.post("bad"), "ordinary post still returns cancellation, not success");
        assertEquals(List.of("bad", "good", "bad"), delivered);
        assertEquals(2, reported.size());
    }

    @Test
    void nestedCheckedFailureIsIsolatedAndRestoresOuterScope() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, false);
        List<Boolean> nested = new ArrayList<>();
        context.getBindings("js").putMember("nested", (ProxyExecutable) arguments -> {
            nested.add(bus.postChecked("inner"));
            return null;
        });
        bus.execute(context.eval("js", "event => { if (event !== 'inner') nested(); }"));
        bus.execute(context.eval("js", "event => { if (event === 'inner' || event === 'outer-bad') throw new Error('fixture'); }"));
        bus.execute(context.eval("js", "event => { delivered.add(event); }"));

        assertTrue(bus.postChecked("outer-good"));
        assertFalse(bus.postChecked("outer-bad"));
        assertEquals(List.of(false, false), nested);
        assertEquals(List.of("inner", "outer-good", "inner", "outer-bad"), delivered);
        assertEquals(3, reported.size());
    }

    @Test
    void checkedSuccessIsIndependentOfCancellation() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, true);
        bus.execute(context.eval("js", "event => true"));
        assertTrue(bus.post("cancel"));
        assertTrue(bus.postChecked("cancel"));
    }

    @Test
    void directJavaDispatchFailuresAreCheckedAndFatalErrorsStillEscape() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, false);
        AssertionError fatal = new AssertionError("fatal fixture");
        bus.bus().listen(event -> {
            if (event.equals("runtime")) throw new IllegalStateException("fixture");
            if (event.equals("fatal")) throw fatal;
        });
        assertFalse(bus.postChecked("runtime"));
        assertSame(fatal, assertThrows(AssertionError.class, () -> bus.postChecked("fatal")));
        assertTrue(bus.postChecked("good"));
    }

    @Test
    void collectionFailureAndNormalNestedFailureKeepTheirExistingBehavior() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class, false);
        context.getBindings("js").putMember("normal", (ProxyExecutable) arguments -> bus.post("inner"));
        bus.execute(context.eval("js", "event => { if (event === 'outer') normal(); }"));
        bus.execute(context.eval("js", "event => { if (event !== 'good') throw new Error('fixture'); }"));
        bus.execute(context.eval("js", "event => { delivered.add(event); }"));
        assertFalse(bus.postChecked("outer"));
        assertEquals(List.of("inner", "outer"), delivered);
        assertThrows(RuntimeException.class, () -> bus.postForCollection("collection"));
        assertTrue(bus.postChecked("good"));
    }

    @Test
    void unsupportedRecipeManagerCannotClaimCheckedSuccessOrObservation() {
        int[] calls = {0};
        IRecipeManagerExtension extension = () -> calls[0]++;
        assertFalse(extension.nekojs$applyScriptsChecked());
        assertFalse(extension.nekojs$observeScriptReplays().succeeded());
        assertFalse(extension.nekojs$scriptReplaysSucceededSinceCreation());
        assertEquals(0, calls[0]);
    }
}
