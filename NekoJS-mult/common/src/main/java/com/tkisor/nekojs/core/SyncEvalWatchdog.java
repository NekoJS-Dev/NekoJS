package com.tkisor.nekojs.core;

import graal.graalvm.polyglot.Context;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * {@code PolyglotException(cancelled)} 后走既有 kill 记账（候选丢弃 / active 隔离）；
 * 清理仍在 owner 线程完成，本守卫不做跨线程 close（中断超时的极端情形除外）。
 *
 * <p>范围：
 * <ul>
 *   <li>只覆盖 arm/disarm 配对的同步求值段（入口执行），长驻空闲环境不受影响——
 *       每次求值结束即 disarm，不存在累计误杀；</li>
 *   <li>超时关闭（{@code timeoutSeconds <= 0}）时返回无操作守卫，零开销；</li>
 *   <li>中断本身超时（极端：guest 在不可中断的宿主调用里长驻）时才降级
 *       {@code close(true)} 强制回收，全部异常内部消化，永不把守卫线程的异常
 *       抛给求值线程。</li>
 * </ul>
 *
 * <p>已知盲区（与语句检查点一致）：guest 回调（事件监听器 / timer）内的失控循环
 * 不在 arm 范围内——回调路径没有配对的 arm/disarm 求值段，仍由语句检查点窗口
 * （{@link RunawayWatchdog}）负责，见票 07 报告的已知缺口节。
 *
 * <p>线程安全：arm/disarm/触发三方用 CAS 保证只中断一次；触发与 disarm 的竞态
 * 窗口内（求值确实已超过时限、disarm 恰在 fire 的 CAS 之后到达）可能对刚结束的
 * Context 注入一次迟到中断——中断只影响「正在执行的求值」，空闲 Context 上的
 * 迟到中断无效果，紧邻的下一次求值若在传播窗口内启动才可能被误伤（窗口远小于
 * 秒级超时本身，接受并记录）。
 */
public final class SyncEvalWatchdog {

    /** 守卫线程数：所有 ScriptType 共享一个单线程调度器（守卫任务只做 interrupt，微秒级）。 */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "nekojs-eval-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    /** 中断注入的等待上限：guest 需要到达安全点（循环回边 / 宿主调用边界）才可被中断。 */
    private static final Duration INTERRUPT_WAIT = Duration.ofSeconds(5);

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
            return Guard.NOOP;
        }
        Guard guard = new Guard(context, true);
        ScheduledFuture<?> future = SCHEDULER.schedule(
                guard::fire, timeoutSeconds, TimeUnit.SECONDS);
        guard.track(future);
        return guard;
    }

    /**
     * 一次布防的句柄：disarm（求值 finally 内调用）与触发状态查询
     * （kill 归因用，不依赖异常类型判定）。
     */
    public static final class Guard {
        static final Guard NOOP = new Guard(null, false);

        private final Context context;
        /** 布防状态：true = 计时中；fire/disarm 以 CAS 争抢，胜者独占后续动作。 */
        private final AtomicBoolean armed = new AtomicBoolean();
        private volatile boolean triggered;
        private volatile ScheduledFuture<?> future;

        private Guard(Context context, boolean initiallyArmed) {
            this.context = context;
            this.armed.set(initiallyArmed);
        }

        private void track(ScheduledFuture<?> scheduled) {
            this.future = scheduled;
            if (!armed.get()) {
                // track 与 disarm 的竞态兜底：disarm 已取消布防则撤掉调度任务
                scheduled.cancel(false);
            }
        }

        private void fire() {
            if (!armed.compareAndSet(true, false)) {
                return;
            }
            triggered = true;
            try {
                context.interrupt(INTERRUPT_WAIT);
            } catch (TimeoutException interruptedTooSlow) {
                // guest 卡在不可中断的宿主调用里超过 5s：强制关闭是唯一回收手段。
                // 求值线程后续对该 Context 的访问会抛「已关闭」异常，走失败/丢弃路径。
                closeForcibly();
            } catch (Throwable ignored) {
                // Context 可能已被 owner 线程关闭（reload 丢弃 / close 抢占）：
                // 守卫线程只做尽力中断，任何异常不外泄
            }
        }

        private void closeForcibly() {
            try {
                context.close(true);
            } catch (Throwable ignored) {
            }
        }

        /** 结束本次求值段布防：成功求值必须在 finally 中调用。幂等。 */
        public void disarm() {
            armed.set(false);
            ScheduledFuture<?> scheduled = this.future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }

        /** 守卫是否触发过中断（求值 catch 路径用于 kill 归因，不依赖异常类型/文本）。 */
        public boolean wasTriggered() {
            return triggered;
        }
    }
}
