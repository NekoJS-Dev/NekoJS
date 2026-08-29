package com.tkisor.nekojs.core;

import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RunawayWatchdog} 滑动窗口语义校验：持续执行超时触发、让出后窗口重置、
 * 语句总量上限内部模拟、检查粒度收窄。
 */
class RunawayWatchdogTest {

    /** 可推进的假时钟（纳秒）。 */
    private static final class FakeClock implements LongSupplier {
        final AtomicLong nanos = new AtomicLong(1_000_000_000L); // 起点非零，避免 0 == "未起算" 哨兵

        void advanceMs(long ms) {
            nanos.addAndGet(ms * 1_000_000L);
        }

        @Override
        public long getAsLong() {
            return nanos.get();
        }
    }

    private static RunawayWatchdog watchdog(int timeoutSeconds, long statementCap, FakeClock clock) {
        return new RunawayWatchdog(timeoutSeconds, statementCap, null, clock);
    }

    @Test
    void continuousExecutionPastTimeoutIsKilled() {
        FakeClock clock = new FakeClock();
        RunawayWatchdog dog = watchdog(10, 0, clock);

        // 持续执行：每次推进 50ms 触发一次检查点（远小于 250ms 让出阈值）。
        // 窗口自第一次触发起算：第 k 次触发时已累计 (k-1)*50ms。
        for (int i = 0; i < 201; i++) {
            clock.advanceMs(50);
            assertFalse(dog.test(null), "窗口累计 ≤ 10s 不应触发");
        }
        // 第 202 次触发时窗口累计 10_050ms > 10s
        clock.advanceMs(50);
        assertTrue(dog.test(null), "持续执行超过 10s 必须判定失控");
    }

    @Test
    void bigJumpCountsAsYieldAndResetsWindow() {
        FakeClock clock = new FakeClock();
        RunawayWatchdog dog = watchdog(10, 0, clock);

        for (int i = 0; i < 100; i++) {
            clock.advanceMs(50);
            assertFalse(dog.test(null));
        }
        // 一次 9.5s 的大跳 = 让出（回到 Java / 长宿主调用），之后继续执行不应被误杀
        clock.advanceMs(9_500);
        for (int i = 0; i < 150; i++) {
            clock.advanceMs(50);
            assertFalse(dog.test(null), "让出重置后窗口从零起算，不应触发");
        }
    }

    @Test
    void yieldGapResetsWindowSoLongLivedContextIsNeverKilled() {
        FakeClock clock = new FakeClock();
        RunawayWatchdog dog = watchdog(10, 0, clock);

        // 模拟长跑服务器：每轮执行 1s（20 个检查点），轮与轮之间让出 1 分钟（事件间隙）
        for (int round = 0; round < 100; round++) {
            for (int i = 0; i < 20; i++) {
                clock.advanceMs(50);
                assertFalse(dog.test(null), "第 " + round + " 轮内不应触发");
            }
            clock.advanceMs(60_000); // 让出：回到 Java / 下一事件
        }
        // 100 轮 × 1s = 100s 总执行量 >> 10s，但窗口按轮重置 → 从未误杀
    }

    @Test
    void statementCapIsEnforcedInsideSameCallback() {
        FakeClock clock = new FakeClock();
        // cap = 3 × 检查粒度（100_000）→ 第 3 次触发时累计达标
        RunawayWatchdog dog = watchdog(0, 300_000, clock);

        assertFalse(dog.test(null));
        assertFalse(dog.test(null));
        assertTrue(dog.test(null), "累计 300_000 条语句应触发总量上限（与窗口无关）");
        assertEquals(100_000, dog.checkInterval());
    }

    @Test
    void smallCapNarrowsCheckInterval() {
        FakeClock clock = new FakeClock();
        RunawayWatchdog dog = watchdog(0, 50_000, clock);
        assertEquals(50_000, dog.checkInterval(), "cap 小于默认粒度时按 cap 收窄");

        // 收窄后 1 次触发即达标
        assertTrue(dog.test(null));
    }

    @Test
    void watchdogDisabledWhenTimeoutZero() {
        FakeClock clock = new FakeClock();
        RunawayWatchdog dog = watchdog(0, 0, clock);

        clock.advanceMs(Long.MAX_VALUE / 2); // 时间再久
        assertFalse(dog.test(null), "timeout=0 且 cap=0 时永不触发");
    }

    @Test
    void sourceChangeResetsStateSoContextsDoNotAccumulateTogether() throws Exception {
        FakeClock clock = new FakeClock();
        // cap = 3 × 粒度
        RunawayWatchdog dog = watchdog(0, 300_000, clock);
        graal.graalvm.polyglot.Source sourceA = graal.graalvm.polyglot.Source
                .newBuilder("js", "var a = 1;", "a.js").build();
        graal.graalvm.polyglot.Source sourceB = graal.graalvm.polyglot.Source
                .newBuilder("js", "var b = 1;", "b.js").build();

        // source A 执行两个检查点（累计 200_000）
        assertFalse(dog.test(sourceA));
        assertFalse(dog.test(sourceA));
        // 切到 source B：计数独立起算，第三个检查点之前不应触发
        assertFalse(dog.test(sourceB));
        assertFalse(dog.test(sourceB));
        assertTrue(dog.test(sourceB), "source B 自身累计 300_000 才触发");
    }
}
