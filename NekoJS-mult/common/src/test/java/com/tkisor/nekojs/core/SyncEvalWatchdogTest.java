package com.tkisor.nekojs.core;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 07 同步求值看门狗：语句钩子看不见的空循环 {@code while(true){}} 必须能被
 * 墙钟超时打断（{@code Context.interrupt}），健康脚本不受影响，超时关闭时无动作。
 *
 * <p>背景：{@code ResourceLimits} 语句回调在空循环体内再无语句可数，
 * 回调只在求值前触发数次即停止，滑动窗口永不累计（见票 06 缺陷复现）。
 * 本守卫只覆盖同步求值段（arm/disarm 配对），长驻空闲环境永不误杀。
 */
class SyncEvalWatchdogTest {

    @Test
    void emptyLoopIsInterruptedByWallClock() {
        Engine engine = Engine.newBuilder().build();
        Context ctx = Context.newBuilder("js")
                .engine(engine)
                .allowExperimentalOptions(true)
                .build();
        SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(ctx, 1);
        try {
            java.util.concurrent.atomic.AtomicReference<Throwable> evalError =
                    new java.util.concurrent.atomic.AtomicReference<>();
            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
                try {
                    ctx.eval("js", "while (true) { /* spin forever */ }");
                } catch (Throwable t) {
                    evalError.set(t);
                }
            }, "wall-clock watchdog (1s) must abort the empty infinite loop");
            assertTrue(evalError.get() != null, "interrupted eval must surface an exception");
            assertTrue(guard.wasTriggered(), "guard must record that it fired the interrupt");
        } catch (AssertionError e) {
            // assertTimeoutPreemptively 抛出的超时即失败：守卫未打断循环
            throw e;
        } finally {
            guard.disarm();
            try {
                ctx.close(true);
            } catch (Throwable ignored) {
            }
            engine.close();
        }
    }

    @Test
    void healthyScriptIsNotDisturbed() {
        Engine engine = Engine.newBuilder().build();
        Context ctx = Context.newBuilder("js")
                .engine(engine)
                .allowExperimentalOptions(true)
                .build();
        SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(ctx, 30);
        try {
            Object result = ctx.eval("js", "1 + 2").asInt();
            assertTrue(Integer.valueOf(3).equals(result), "healthy eval must complete normally");
            assertFalse(guard.wasTriggered(), "healthy eval must not trigger the guard");
        } finally {
            guard.disarm();
            ctx.close();
            engine.close();
        }
    }

    @Test
    void disabledTimeoutIsNoop() {
        Engine engine = Engine.newBuilder().build();
        Context ctx = Context.newBuilder("js")
                .engine(engine)
                .allowExperimentalOptions(true)
                .build();
        SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(ctx, 0);
        try {
            assertFalse(guard.wasTriggered());
            ctx.eval("js", "var a = 1;");
        } finally {
            guard.disarm();
            ctx.close();
            engine.close();
        }
    }
}
