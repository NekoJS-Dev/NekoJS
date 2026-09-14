package com.tkisor.nekojs.core.lifecycle;

/**
 * 同一 ScriptType 的 owner-thread 生命周期调度门（票 07）。
 *
 * <p>复用调用方（{@code ScriptManager}）的实例锁做跨线程串行：非 owner 线程的
 * managed lifecycle 请求在实例锁的 monitor 队列里排队，不直接触碰 Context、
 * binding、listener 或 timer。本门只解决实例锁表达不了的三件事：
 * <ul>
 *   <li>reload 不重入：同一线程已在生命周期内又请求 RELOAD（回调内 reload、
 *       并发 reload 在同一线程重入）时返回明确拒绝而不是递归开启第二个 candidate；</li>
 *   <li>回调内请求：managed 回调（事件监听器 / timer）执行期间同线程请求
 *       lifecycle 时返回明确拒绝，不同步等待自身；</li>
 *   <li>close 优先：{@link #requestClose()} 先于实例锁设置 volatile 标志，
 *       尚未开始的 reload/load 在拿到锁后看到标志即拒绝，已在途的 candidate
 *       由 close 侧中断并丢弃；{@link #markClosed()} 后一切新 lifecycle 拒绝。</li>
 * </ul>
 *
 * <p>线程约定：除 volatile 标志读写与静态回调深度外，所有实例方法必须在持有
 * 调用方实例锁的前提下调用（与 {@code ScriptManager} 的 synchronized 方法一致）。
 * 跨线程排队由实例锁保证；门内 {@code depth > 0} 因此只可能表示同线程重入。
 *
 * <p>嵌套例外：STARTUP 的 reset+load 路径在 RELOAD 临界区内同步调用 LOAD，
 * 该且仅该嵌套被放行；其它嵌套一律拒绝。
 */
public final class ScriptLifecycleGate {

    /** 生命周期操作种类。 */
    public enum Operation {
        RELOAD,
        LOAD,
        CLOSE
    }

    /** 进入判决：调用方可观察的结果，不是内部锁状态。 */
    public enum Decision {
        /** 已进入，调用方继续执行并在 finally 中 {@link #exit(Operation)}。 */
        EXECUTED,
        /** 同线程重入或 managed 回调内请求：不得递归，必须向外部返回明确拒绝。 */
        REJECTED_REENTRANT,
        /** close 已请求但尚未完成：尚未开始的新工作拒绝，在途工作由 close 侧抢占。 */
        REJECTED_CLOSING,
        /** 已关闭：一切新 lifecycle 拒绝。 */
        REJECTED_CLOSED
    }

    private Thread owner;
    private int depth;
    private Operation outer;
    private volatile boolean closeRequested;
    private volatile boolean closed;
    private volatile boolean activeFailed;

    private static final ThreadLocal<Integer> CALLBACK_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * managed 回调进入（事件监听器 / timer 回调执行体）：调用方（EventBusJS /
     * NekoNodeTimers）在执行 guest 回调前后配对调用，只做线程本地计数，
     * 不加锁、不触碰 Context。
     */
    public static void enterCallback() {
        CALLBACK_DEPTH.set(CALLBACK_DEPTH.get() + 1);
    }

    /** 与 {@link #enterCallback()} 配对的回调退出。 */
    public static void exitCallback() {
        int remaining = CALLBACK_DEPTH.get() - 1;
        if (remaining <= 0) {
            CALLBACK_DEPTH.remove();
        } else {
            CALLBACK_DEPTH.set(remaining);
        }
    }

    /**
     * 请求进入生命周期。调用方必须已持有实例锁。
     *
     * @return 判决；非 {@link Decision#EXECUTED} 时未产生任何门状态变化，
     *         调用方不得执行 lifecycle 体，必须把拒绝结果向外部可观察地返回。
     */
    public Decision tryEnter(Operation op) {
        if (op == Operation.CLOSE) {
            if (outer == null) {
                outer = op;
            }
            owner = Thread.currentThread();
            depth++;
            return Decision.EXECUTED;
        }
        if (closed) {
            return Decision.REJECTED_CLOSED;
        }
        if (closeRequested) {
            return Decision.REJECTED_CLOSING;
        }
        if (depth > 0) {
            if (op == Operation.LOAD && outer == Operation.RELOAD) {
                depth++;
                return Decision.EXECUTED;
            }
            return Decision.REJECTED_REENTRANT;
        }
        if (CALLBACK_DEPTH.get() > 0) {
            return Decision.REJECTED_REENTRANT;
        }
        owner = Thread.currentThread();
        outer = op;
        depth = 1;
        return Decision.EXECUTED;
    }

    /**
     * 退出生命周期。调用方必须已持有实例锁，且与一次 {@link Decision#EXECUTED}
     * 的 {@link #tryEnter(Operation)} 配对（finally 内调用，幂等清理不吞异常）。
     */
    public void exit(Operation op) {
        if (depth <= 0) {
            throw new IllegalStateException("ScriptLifecycleGate.exit without matching tryEnter(" + op + ")");
        }
        depth--;
        if (depth == 0) {
            owner = null;
            outer = null;
        }
    }

    /** 是否有 lifecycle 在体（门内状态；调用方持锁时读）。 */
    public boolean isInLifecycle() {
        return depth > 0;
    }

    /**
     * 请求关闭：close 侧在拿实例锁<i>之前</i>先设置，其他线程随后拿到锁的
     * 新 lifecycle 即看到拒绝——close 优先于尚未开始的 reload。
     */
    public void requestClose() {
        closeRequested = true;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }

    /** 终端关闭完成：之后一切新 lifecycle 拒绝（幂等）。 */
    public void markClosed() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * watchdog 终止 active 后的隔离失败标记：停止向被杀 Context 分发，不自动
     * 创建第二个 active；只由显式 reload/load 成功或 close 清除。
     */
    public void markActiveFailed() {
        activeFailed = true;
    }

    public void clearActiveFailed() {
        activeFailed = false;
    }

    public boolean isActiveFailed() {
        return activeFailed;
    }
}
