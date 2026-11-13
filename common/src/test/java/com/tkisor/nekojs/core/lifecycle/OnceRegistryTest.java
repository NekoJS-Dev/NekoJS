package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.bindings.static_access.OnceJS;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the run-once guard in {@link OnceRegistry}: the first runOnce claims the key, later calls
 * skip it, clear and clearAll reset the marker, and under concurrency exactly one caller wins per
 * key. Markers deliberately live as long as the process and are not cleared by a "reload".
 * Also checks the {@code once} and {@code clearOnce} bindings ({@link OnceJS} ProxyExecutable)
 * against a real Graal Context: argument validation and callback execution.
 */
class OnceRegistryTest {

    private OnceRegistry registry;
    private Context context;

    @BeforeEach
    void setUp() {
        registry = new OnceRegistry();
        OnceRegistry.SHARED.clearAll();
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        OnceRegistry.SHARED.clearAll();
    }

    @Test
    void runOnceReturnsTrueOnlyForFirstCall() {
        assertTrue(registry.runOnce("init"), "首次调用应返回 true（执行回调）");
        assertFalse(registry.runOnce("init"), "同 key 二次调用应返回 false（跳过）");
        assertFalse(registry.runOnce("init"), "后续调用继续返回 false");
    }

    @Test
    void keysAreIndependent() {
        assertTrue(registry.runOnce("a"));
        assertFalse(registry.runOnce("a"));
        assertTrue(registry.runOnce("b"), "不同 key 互不影响");
        assertEquals(2, registry.size());
    }

    @Test
    void sharedMarkersSurviveReloadSimulation() {
        // A reload rebuilds the script Context, while the marker table is a process-wide singleton.
        // Simulate a reload by closing the old Context, opening a new one and running the once flow
        // again: the marker is still there and the callback does not run again.
        assertTrue(OnceRegistry.SHARED.runOnce("persist"));
        context.close();
        context = Context.newBuilder("js").allowAllAccess(true).build();
        Value callback = context.eval("js", "(function() { throw new Error('must not run after reload'); })");
        Object result = OnceJS.ONCE.execute(Value.asValue("persist"), callback);
        assertNull(result, "reload 后同 key 的 once 应直接跳过");
        assertTrue(OnceRegistry.SHARED.has("persist"), "标记应跨 reload 存活");
    }

    @Test
    void clearRemovesSingleMarkerAndReportsExistence() {
        registry.runOnce("only");
        assertTrue(registry.clear("only"), "清除已存在的标记应返回 true");
        assertFalse(registry.has("only"));
        assertTrue(registry.runOnce("only"), "清除后 runOnce 重新返回 true");
        // runOnce has set the marker again, so clear it first, then check that clearing an unknown
        // marker returns false.
        registry.clear("only");
        assertFalse(registry.clear("only"), "清除不存在的标记应返回 false");
    }

    @Test
    void clearAllResetsEveryMarker() {
        registry.runOnce("a");
        registry.runOnce("b");
        registry.clearAll();
        assertEquals(0, registry.size());
        assertTrue(registry.runOnce("a"));
        assertTrue(registry.runOnce("b"));
    }

    @Test
    void blankKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry.runOnce(" "));
        assertThrows(IllegalArgumentException.class, () -> registry.runOnce(null));
    }

    @Test
    void resolveKeyIsIdentityForNow() {
        // The key is currently returned unchanged; update this assertion once a script owner prefix is
        // added.
        assertEquals("myInit", registry.resolveKey("myInit"));
    }

    @Test
    void concurrentFirstCallersExactlyOne() throws InterruptedException {
        int threads = 8;
        int rounds = 200;
        List<Thread> workers = new ArrayList<>();
        int[] wins = new int[1];
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                for (int i = 0; i < rounds; i++) {
                    if (registry.runOnce("hot:" + i)) {
                        synchronized (wins) {
                            wins[0]++;
                        }
                    }
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals(rounds, wins[0], "每个 key 恰好一个并发调用者胜出");
    }

    /* ================= once and clearOnce bindings (Graal Context) ================= */

    @Test
    void onceBindingExecutesCallbackOnlyFirstTime() {
        List<String> calls = new ArrayList<>();
        // Inject the recording list into the JS global and reference it from the callback body, so the
        // test can see that the callback really ran.
        context.getBindings("js").putMember("callsMarker", calls);
        Value callback = context.eval("js", "(function() { callsMarker.add('run'); return 'did'; })");

        Object first = OnceJS.ONCE.execute(Value.asValue("boot"), callback);
        Object second = OnceJS.ONCE.execute(Value.asValue("boot"), callback);

        // For JS the ProxyExecutable return value is the callback result; from the Java test it is
        // wrapped in a Graal Value.
        assertEquals("did", ((Value) first).asString(), "首次调用应执行回调并透传返回值");
        assertNull(second, "二次调用跳过回调，返回 null");
        assertEquals(List.of("run"), calls, "回调只执行一次");
    }

    @Test
    void clearOnceBindingReArmsKey() {
        Value callback = context.eval("js", "(function() { return 1; })");
        OnceJS.ONCE.execute(Value.asValue("rearm"), callback);
        Object removed = OnceJS.CLEAR_ONCE.execute(Value.asValue("rearm"));
        assertEquals(Boolean.TRUE, removed, "clearOnce(key) 应返回标记原本存在");
        Object again = OnceJS.ONCE.execute(Value.asValue("rearm"), callback);
        assertEquals(1, ((Value) again).asInt(), "清除后 once 重新执行回调");
    }

    @Test
    void clearOnceWithoutArgumentsClearsAll() {
        OnceRegistry.SHARED.runOnce("x");
        OnceRegistry.SHARED.runOnce("y");
        Object result = OnceJS.CLEAR_ONCE.execute();
        assertNull(result);
        assertEquals(0, OnceRegistry.SHARED.size(), "clearOnce() 应清空全部标记");
    }

    @Test
    void onceBindingValidatesArguments() {
        Value callback = context.eval("js", "(function() {})");
        assertThrows(IllegalArgumentException.class,
                () -> OnceJS.ONCE.execute(Value.asValue(42), callback),
                "key 必须是字符串");
        assertThrows(IllegalArgumentException.class,
                () -> OnceJS.ONCE.execute(Value.asValue("k"), Value.asValue("not-a-function")),
                "callback 必须可执行");
    }
}
