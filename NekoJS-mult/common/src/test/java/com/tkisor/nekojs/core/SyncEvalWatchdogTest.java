package com.tkisor.nekojs.core;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 07 同步求值看门狗：语句钩子看不见的空循环 {@code while(true){}} 必须能被
 * 墙钟超时打断（{@code Context.interrupt}），健康脚本不受影响，超时关闭时无动作。
 *
 * <p>背景：{@code ResourceLimits} 语句回调在空循环体内再无语句可数，回调只在求值前
 * 触发数次即停止，滑动窗口永不累计（见票 06 缺陷复现）。本守卫只覆盖同步求值段
 * （arm/disarm 配对），长驻空闲环境永不误杀。
 *
 * <p>另含 AC8 判定的两个实证探针（跨线程进入同一 Context 的真实 Graal 行为）：
 * 并发 eval 与「另一线程 close 正在执行的 Context」——它们的结论决定 EventBusJS
 * 分发点 Context monitor 的去留（见票 07 报告 AC8 节）。
 */
class SyncEvalWatchdogTest {

    /** 探针信号对象：guest 代码进入循环前调用 {@link #enter()}，宿主侧据此确定时序。 */
    public static final class ProbeSignal {
        final CountDownLatch entered = new CountDownLatch(1);

        public void enter() {
            entered.countDown();
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(10, TimeUnit.SECONDS);
        }
    }

    private static Context newContext(Engine engine) {
        return Context.newBuilder("js")
                .engine(engine)
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.ALL)
                .build();
    }

    private static Context probeContext(Engine engine, ProbeSignal signal) {
        Context ctx = newContext(engine);
        ctx.getBindings("js").putMember("__probeEnter", signal);
        return ctx;
    }

    @Test
    void emptyLoopIsInterruptedByWallClock() {
        Engine engine = Engine.newBuilder().build();
        Context ctx = newContext(engine);
        SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(ctx, 1);
        AtomicReference<Throwable> evalError = new AtomicReference<>();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                try {
                    ctx.eval("js", "while (true) { /* spin forever */ }");
                } catch (Throwable t) {
                    evalError.set(t);
                }
            }, "wall-clock watchdog (1s) must abort the empty infinite loop");
            assertNotNull(evalError.get(), "interrupted eval must surface an exception");
            assertTrue(guard.wasTriggered(), "guard must record that it fired the interrupt");
        } finally {
            guard.disarm();
            closeQuietly(ctx);
            closeQuietly(engine);
        }
    }

    @Test
    void healthyScriptIsNotDisturbed() {
        Engine engine = Engine.newBuilder().build();
        Context ctx = newContext(engine);
        SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(ctx, 30);
        try {
            int result = ctx.eval("js", "1 + 2").asInt();
            assertTrue(result == 3, "healthy eval must complete normally");
            assertFalse(guard.wasTriggered(), "healthy eval must not trigger the guard");
        } finally {
            guard.disarm();
            closeQuietly(ctx);
            closeQuietly(engine);
        }
    }

    @Test
    void disabledTimeoutIsNoop() {
        Engine engine = Engine.newBuilder().build();
        Context ctx = newContext(engine);
        SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(ctx, 0);
        try {
            assertFalse(guard.wasTriggered());
            ctx.eval("js", "var a = 1;");
        } finally {
            guard.disarm();
            closeQuietly(ctx);
            closeQuietly(engine);
        }
    }

    /**
     * 实证探针（AC8 判定输入）：两个线程无互斥地并发进入同一 Context 求值，
     * Graal 必须拒绝（Multi threaded access）。结论：并发进入需要互斥——删除
     * 分发点 Context monitor 的前提是「lifecycle 与分发不同时从不同线程进入同一
     * active Context」（owner-thread 纪律 + manager 串行），而非 Graal 自身容忍。
     */
    @Test
    void concurrentEvalOnSameContextIsRejectedByGraal() throws Exception {
        Engine engine = Engine.newBuilder().build();
        ProbeSignal signal = new ProbeSignal();
        Context ctx = probeContext(engine, signal);
        AtomicReference<Throwable> secondEvalError = new AtomicReference<>();
        try {
            Thread spinner = new Thread(() -> {
                try {
                    ctx.eval("js", "__probeEnter.enter(); var x = 0; while (true) { x = (x + 1) % 1000; }");
                } catch (Throwable ignored) {
                    // spin 线程的异常不参与判定（探针侧只观察第二个线程）
                }
            }, "watchdog-probe-spinner");
            spinner.setDaemon(true);
            spinner.start();
            assertTrue(signal.awaitEntered(), "spinner must reach the guest loop");
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                try {
                    ctx.eval("js", "1 + 1");
                } catch (Throwable t) {
                    secondEvalError.set(t);
                }
            });
            assertNotNull(secondEvalError.get(),
                    "concurrent eval on a single-threaded Context must be rejected by Graal");
        } finally {
            // spinner 仍在死循环：先中断再关闭，避免 close 阻塞在 entered 状态上
            try {
                ctx.interrupt(Duration.ofSeconds(5));
            } catch (Throwable ignored) {
            }
            closeQuietly(ctx);
            closeQuietly(engine);
        }
    }

    /**
     * 实证探针（AC8 判定输入 + close 抢占可行性）：求值线程在 guest 内执行时，
     * 另一线程调用 {@code Context.interrupt} 必须能在安全点打断它（close 抢占的
     * 中断加速依赖此行为；watchdog 的 interrupt 路径同源）。
     */
    @Test
    void interruptFromOtherThreadAbortsRunningEval() throws Exception {
        Engine engine = Engine.newBuilder().build();
        ProbeSignal signal = new ProbeSignal();
        Context ctx = probeContext(engine, signal);
        AtomicReference<Throwable> evalError = new AtomicReference<>();
        Thread spinner = new Thread(() -> {
            try {
                ctx.eval("js", "__probeEnter.enter(); var x = 0; while (true) { x = (x + 1) % 1000; }");
            } catch (Throwable t) {
                evalError.set(t);
            }
        }, "watchdog-probe-interrupt");
        spinner.setDaemon(true);
        try {
            spinner.start();
            assertTrue(signal.awaitEntered(), "spinner must reach the guest loop");
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                try {
                    ctx.interrupt(Duration.ofSeconds(5));
                } catch (Throwable interruptFailure) {
                    // interrupt 本身失败不必然失败（上下文可能已退出），以 spinner 结束为准
                }
                spinner.join(TimeUnit.SECONDS.toMillis(20));
            }, "interrupt from another thread must abort the running eval");
            assertFalse(spinner.isAlive(), "spinner must terminate after interrupt");
            assertNotNull(evalError.get(), "interrupted eval must surface an exception");
        } finally {
            closeQuietly(ctx);
            closeQuietly(engine);
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }
}
