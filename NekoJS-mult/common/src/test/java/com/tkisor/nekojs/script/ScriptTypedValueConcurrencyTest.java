package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link ScriptTypedValue#at(ScriptType)} 的懒初始化并发安全。
 *
 * <p>修复前 at() 的 check-then-act 未同步，多个线程并发首次取用时可能各自调用
 * initializer，产生重复的 Logger / BindingRegistry 等重资源。修复只同步
 * lazy-initialization 分支（double-checked locking），保证 initializer 每个 type 只执行一次。
 */
class ScriptTypedValueConcurrencyTest {

    @Test
    void concurrentAtStartupInvokesInitializerExactlyOnce() throws Exception {
        int rounds = 50;
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                AtomicInteger calls = new AtomicInteger();
                AtomicReference<Object> created = new AtomicReference<>();
                ScriptTypedValue<Object> typed = ScriptTypedValue.of(type -> {
                    calls.incrementAndGet();
                    Object value = new Object();
                    created.compareAndSet(null, value);
                    return value;
                });

                CountDownLatch start = new CountDownLatch(1);
                List<Future<Object>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return typed.at(ScriptType.STARTUP);
                    }));
                }
                start.countDown();

                Set<Object> values = ConcurrentHashMap.newKeySet();
                for (Future<Object> future : futures) {
                    values.add(future.get(30, TimeUnit.SECONDS));
                }

                assertEquals(1, calls.get(),
                        "round " + round + ": initializer must run exactly once per type");
                assertEquals(1, values.size(),
                        "round " + round + ": all threads must observe the same initialized value");
                assertSame(created.get(), values.iterator().next(),
                        "round " + round + ": value must be the one produced by the initializer");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
