package com.tkisor.nekojs.core;

import graal.graalvm.polyglot.Source;

import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * 同步执行失控看门狗：把 Graal 的语句计数检查点用作安全打断点，实现「持续执行超时」。
 *
 * <p>背景：同步脚本入口里的 {@code while(true){}} 会永久阻塞服务器线程，而 JVM 没有其它
 * 安全中断手段（等待超时只能停等待、interrupt 对纯计算循环无效）。{@code ResourceLimits}
 * 的语句钩子是唯一能从宿主执行内部干净解栈的机制。
 *
 * <p>语义（滑动窗口）：每 {@link #CHECK_INTERVAL_STATEMENTS} 条语句触发一次本回调；
 * 若相邻检查点之间的墙钟间隙 ≤ {@link #IDLE_RESET_NANOS}，视为「持续执行」，窗口累加；
 * 间隙更大说明控制权曾回到 Java（求值结束、事件间隙或长宿主调用），窗口重新起算。
 * 窗口累计超过超时即判定失控，返回 {@code true} 让 Graal 关闭 Context——
 * <b>长驻环境的合法重活永远不会被误杀</b>（每次让出都会重置），这是对「Context 生命周期
 * 语句总量上限」的关键改进：总量上限在忙碌服务器上必然周期性杀死健康环境并丢失监听器。
 *
 * <p>同一回调同时内部模拟可选的绝对语句总量上限（{@code scriptStatementLimit}）：
 * {@code ResourceLimits.Builder} 只允许一个 statementLimit，两种保护共用一个回调与计数粒度。
 *
 * <p>已知盲区（与语句上限一致）：不执行语句的阻塞（宿主调用内部 sleep/IO）不会触发检查点，
 * 看门狗对其不可见。
 */
final class RunawayWatchdog implements Predicate<Source> {

    /** 检查点粒度：每 10 万条语句触发一次判定。对编译热循环约对应毫秒级间隔。 */
    static final long CHECK_INTERVAL_STATEMENTS = 100_000;

    /**
     * 检查点间隙阈值（纳秒）：相邻两次触发间隔超过它即认为「让出过」。
     * 250ms 足以容纳 GC 停顿与零星短宿主调用，又远小于秒级超时窗口。
     */
    static final long IDLE_RESET_NANOS = 250_000_000L;

    /** 日志回调（slf4j 风格占位符参数）。slf4j 在 common 为 compileOnly，故不进签名，便于单测。 */
    interface WarnSink {
        void warn(String message, Object... args);
    }

    private final long timeoutNanos;
    private final long statementCap;
    private final long checkInterval;
    private final WarnSink logger;
    private final LongSupplier clock;

    /**
     * 单线程状态：检查点在求值线程内联触发。Graal 要求同一 Engine 的所有 Context 共用
     * 一个谓词实例，因此本实例被各 ScriptType 沙盒共享；Source 切换（新求值/另一 Context）
     * 时窗口与计数一并重置，避免同线程上多个 Context 相互串扰累计。
     *
     * <p>{@code lastSource} 持弱引用（W4/§3-13）：旧实现是普通字段，求值线程的 ThreadLocal
     * 会永久钉住最后一次求值的 {@link Source} 及其可达的 Context 图——reload 后旧 Source
     * 无法回收，反复 reload 内存单调增长。弱引用下窗口语义不变：活跃求值的 Source 被
     * 引擎强引用，比较仍命中；求值结束后引用自然清除，下次触发视为新 Source 重起窗口。
     */
    private final ThreadLocal<Window> window = ThreadLocal.withInitial(Window::new);

    private static final class Window {
        java.lang.ref.WeakReference<Source> lastSource;
        long windowStart;      // 0 = 尚未起算
        long lastFire;
        long firedIntervals;
    }

    RunawayWatchdog(int timeoutSeconds, long statementCap, WarnSink logger, LongSupplier clock) {
        this.timeoutNanos = timeoutSeconds > 0 ? timeoutSeconds * 1_000_000_000L : 0;
        this.statementCap = statementCap;
        this.logger = logger;
        this.clock = clock;
        // 总量上限小于默认粒度时收窄粒度，保证小上限的判定精度
        this.checkInterval = statementCap > 0 && statementCap < CHECK_INTERVAL_STATEMENTS
                ? statementCap
                : CHECK_INTERVAL_STATEMENTS;
    }

    long checkInterval() {
        return checkInterval;
    }

    /**
     * @return {@code true} = 判定失控/超限，Graal 关闭 Context；{@code false} = 语句计数清零继续
     */
    @Override
    public boolean test(Source source) {
        Window w = window.get();
        long now = clock.getAsLong();
        Source previous = w.lastSource == null ? null : w.lastSource.get();
        if (previous != source) {
            // 新的 Source（另一次求值或另一 Context）→ 独立起算
            w.lastSource = new java.lang.ref.WeakReference<>(source);
            w.windowStart = 0;
            w.firedIntervals = 0;
        }
        w.firedIntervals++;

        if (statementCap > 0 && firedIntervalsTotal(w) >= statementCap) {
            if (logger != null) {
                logger.warn("脚本语句累计数达到 scriptStatementLimit（{}），关闭对应脚本环境；"
                        + "当前求值被中止，下一次取用时会自动重建 Context（/nekojs reload 亦可手动恢复）",
                        statementCap);
            }
            return true;
        }

        if (timeoutNanos <= 0) return false; // 只启用总量上限

        if (w.windowStart == 0 || now - w.lastFire > IDLE_RESET_NANOS) {
            w.windowStart = now; // 新窗口：首次触发，或让出过（回到 Java / 长宿主调用）
        }
        w.lastFire = now;
        if (now - w.windowStart > timeoutNanos) {
            if (logger != null) {
                logger.warn("脚本同步执行持续超过 scriptRunawayTimeoutSeconds（{}s）未让出，判定为失控循环，"
                        + "关闭对应脚本环境；当前求值被中止，下一次取用时会自动重建 Context（/nekojs reload 亦可手动恢复）",
                        timeoutNanos / 1_000_000_000L);
            }
            return true;
        }
        return false;
    }

    private long firedIntervalsTotal(Window w) {
        return w.firedIntervals * checkInterval;
    }
}
