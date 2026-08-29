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
 * {@link OnceRegistry} run-once 守卫逻辑回归：首次 runOnce 抢占成功、重复调用跳过、
 * clear/clearAll 重置、并发下每 key 恰好一个胜者；标记刻意随进程存活（不随"reload"清除）。
 * 附带用真实 Graal Context 验证 {@code once}/{@code clearOnce} 绑定
 * （{@link OnceJS} ProxyExecutable）的参数校验与回调执行语义。
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
        // reload 重建的是脚本 Context，标记表是进程级单例：模拟"reload"（关掉旧 Context
        // 重开新 Context 再走一遍 once 流程）后标记仍在、回调不再执行
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
        // runOnce 已重新落下标记，先清掉再验证“清除不存在的标记返回 false”
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
        // v1 seam：key 全局原样返回；接入脚本所有者前缀后此断言需更新
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

    /* ================= once / clearOnce 绑定（Graal Context） ================= */

    @Test
    void onceBindingExecutesCallbackOnlyFirstTime() {
        List<String> calls = new ArrayList<>();
        // 记录数组注入 JS 全局，回调体内引用，验证回调确实被执行
        context.getBindings("js").putMember("callsMarker", calls);
        Value callback = context.eval("js", "(function() { callsMarker.add('run'); return 'did'; })");

        Object first = OnceJS.ONCE.execute(Value.asValue("boot"), callback);
        Object second = OnceJS.ONCE.execute(Value.asValue("boot"), callback);

        // ProxyExecutable 的返回值对 JS 侧就是回调返回值；Java 测试视角下包在 Graal Value 里
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
