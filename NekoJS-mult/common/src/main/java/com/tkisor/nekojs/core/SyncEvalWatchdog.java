package com.tkisor.nekojs.core;

import graal.graalvm.polyglot.Context;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同步求值墙钟守卫（票 07 watchdog 面）。
 *
 * <p>动机：{@code ResourceLimits} 的语句检查点是宿主从 guest 执行内部干净解栈的
 * 唯一机制，但它只在有语句可数时触发——空循环 {@code while(true){}} 在求值前触发
 * 数次后即再无回调，基于语句回调计数的滑动窗口（{@link RunawayWatchdog}）永不
 * 累计，时间窗口路径形同虚设（票 06 缺陷复现 {@code Ticket06RunawayProbeTest}）。
 * 本守卫改用宿主墙钟 + {@link Context#interrupt(Duration)}：同步求值段 arm，
 * 超时仍未 disarm 即从守卫线程注入中断，owner 线程的求值抛
 * {@code PolyglotException(cancelled)} 后走既有 kill 记账与候选丢弃/隔离恢复；
 * 清理仍在 owner 线程完成，本守卫不做跨线程 close。
 *
 * <p>范围：
 * <ul>
 *   <li>只覆盖 arm/disarm 配对的同步求值段（入口执行），长驻空闲环境不受影响——
 *       每次求值结束即 disarm，不存在累计误杀；</li>
 *   <li>超时关闭（{@code timeoutSeconds <= 0}）时返回无操作守卫，零开销；</li>
 *   <li>中断本身超时（极端）时尽力 {@code close(true)}，全部异常内部消化，
 *       永不把守卫线程的异常抛给求值线程。</li>
 * </ul>
 *
 * <p>线程安全：arm/disarm/触发三方用 CAS 保证只中断一次；disarm 获胜时
 * 调度任务不再中断，避免误伤同一 Context 上的后一次求值。
 */
public final class SyncEvalWatchdog {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "nekojs-eval-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    private SyncEvalWatchdog() {
    }

    /**
     * 为一次同步求值布防。超时关闭时返回无操作守卫。
     *
     * @param context        正在（或即将）同步求值的 Context
     * @param timeoutSeconds 墙钟超时秒数，{@code <= 0} 表示禁用
     */
    public static Guard arm(Context context, int timeoutSeconds) {
        if (context == null || timeoutSeconds <= 0) {
            return Guard.noop();
        }
        Guard guard = new Guard(context);
        ScheduledFuture<?> future = SCHEDULER.schedule(guard::fire,
                TimeUnit.SECONDS.toNanos(timeoutSeconds), TimeUnit.NANOSECONDS);
        guard.track(future);
        return guard;
    }

    /** 一次布防的句柄：disarm 与触发状态查询。 */
    public static final class Guard {
        private final Context context;
        private final AtomicBoolean armed = new AtomicBoolean(false);
        private final AtomicBoolean triggered = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        private Guard(Context context) {
            this.context = context;
            this.armed.set(true);
        }

        private static Guard noop() {
            Guard guard = new Guard(null);
            guard.armed.set(false);
            return guard;
        }

        private void track(ScheduledFuture<?> scheduled) {
            this.future = scheduled;
            if (!armed.get()) {
                scheduled.cancel(false);
            }
        }

        private void fire() {
            if (!armed.compareAndSet(true, false)) {
                return;
            }
            triggered.set(true);
            try {
                context.interrupt(Duration.ofSeconds(5));
            } catch (java.util.concurrent.TimeoutException timeout) {
                try {
                    context.close(true);
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
        }

        /** 结束本次求值段布防：成功求值必须在 finally 中调用。 */
        public void disarm() {
            armed.set(false);
            ScheduledFuture<?> scheduled = this.future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }

        /** 守卫是否触发过中断（求值 catch 路径用于 kill 归因，不依赖异常类型）。 */
        public boolean wasTriggered() {
            return triggered.get();
        }
    }
}
