package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Node-compatible timer backend: setTimeout, setInterval, setImmediate.
 *
 * <p>Uses a daemon-thread {@link ScheduledExecutorService}. Callbacks are queued
 * to a {@code ConcurrentLinkedQueue} and flushed synchronously on each tick
 * (server tick for server_scripts, client tick for client_scripts).
 *
 * <p>STARTUP scripts reject delayed timers and setInterval entirely.
 * Reload cancels all timers registered by the old script.
 */
public final class NekoNodeTimers implements AutoCloseable {
    /** ready 队列上限：游戏线程被阻塞时 1ms interval 会不限速堆积，超限直接丢弃新回调并记录。 */
    private static final int MAX_READY_QUEUE_SIZE = 4096;
    /** 单次 flush 最多执行的回调数：防止一次 tick 内无限处理导致 tick 冻结。 */
    private static final int MAX_CALLBACKS_PER_FLUSH = 256;
    /**
     * 并发注册 timer 数上限：{@code while(true){setInterval(()=>{},1000)}} 会在 50M 语句上限
     * 触发前向 {@code tasks} 无限塞入条目（每个持有 JS 回调 Value 与调度句柄）导致 OOM。
     * 1024 远超正常脚本需求（沙盒里 timer 是长期资源而非吞吐工具），且小于 ready 队列上限
     * （每个 interval 会反复向该队列投递）。超限直接抛 {@link IllegalStateException} 给脚本，
     * 与 STARTUP 拒绝延迟 timer 的 {@link #rejectStartupTimer} 机制一致。
     */
    private static final int MAX_SCHEDULED_TIMERS = 1024;

    private final ScriptType scriptType;
    private final ErrorTracker errorTracker;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "NekoJS-NodeTimers");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger ids = new AtomicInteger(1);
    private final Map<Integer, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final Map<Integer, String> scriptIds = new ConcurrentHashMap<>();
    private final Map<Context, String> activeScriptIds = new ConcurrentHashMap<>();
    private final Queue<TimerCallback> ready = new ConcurrentLinkedQueue<>();

    public NekoNodeTimers(ScriptType scriptType, ErrorTracker errorTracker) {
        this.scriptType = scriptType;
        this.errorTracker = errorTracker;
    }

    public int setTimeout(Value callback, long delayMillis, Object... args) {
        rejectStartupTimer(delayMillis);
        rejectTimerOverflow();
        int id = ids.getAndIncrement();
        ScheduledFuture<?> future = scheduler.schedule(
                () -> enqueueReady(new TimerCallback(id, false, callback, args)),
                Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
        tasks.put(id, future);
        recordScriptId(id, callback);
        return id;
    }

    public void clearTimeout(int id) {
        cancel(id);
    }

    public int setInterval(Value callback, long delayMillis, Object... args) {
        if (scriptType == ScriptType.STARTUP) {
            throw new IllegalStateException("setInterval is not supported in startup scripts. Use server_scripts or client_scripts for lifecycle timers.");
        }
        rejectTimerOverflow();
        int id = ids.getAndIncrement();
        long delay = Math.max(1L, delayMillis);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> enqueueReady(new TimerCallback(id, true, callback, args)),
                delay, delay, TimeUnit.MILLISECONDS);
        tasks.put(id, future);
        recordScriptId(id, callback);
        return id;
    }

    public void clearInterval(int id) {
        cancel(id);
    }

    public int setImmediate(Value callback, Object... args) {
        return setTimeout(callback, 0L, args);
    }

    public void clearImmediate(int id) {
        cancel(id);
    }

    private void rejectStartupTimer(long delayMillis) {
        if (scriptType == ScriptType.STARTUP && delayMillis > 0L) {
            throw new IllegalStateException("Delayed timers are not supported in startup scripts. Use server_scripts or client_scripts for lifecycle timers.");
        }
    }

    /**
     * 注册数上限拒绝：在分配 id / 调度之前检查，超限抛 {@link IllegalStateException}
     * （异常会沿 JS 调用栈抛回脚本，经既有错误上报路径记录）。clearTimeout/clearInterval
     * 或一次性 timer 被 flush 后释放名额即可继续注册。
     */
    private void rejectTimerOverflow() {
        if (tasks.size() >= MAX_SCHEDULED_TIMERS) {
            throw new IllegalStateException("Too many scheduled timers (limit " + MAX_SCHEDULED_TIMERS
                    + "); clearTimeout/clearInterval old timers before registering new ones");
        }
    }

    public void cancelAll() {
        tasks.keySet().forEach(this::cancel);
        ready.clear();
        scriptIds.clear();
        activeScriptIds.clear();
    }

    public void cancelScript(String scriptId) {
        if (scriptId == null || scriptId.isBlank()) return;
        List<Integer> idsToCancel = List.copyOf(scriptIds.entrySet()).stream()
                .filter(entry -> scriptId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        ready.removeIf(callback -> idsToCancel.contains(callback.id));
        idsToCancel.forEach(this::cancel);
    }

    /**
     * 按 scriptId 前缀取消 timer（脚本包整体卸载用；包内脚本 id 统一携带
     * {@code packs/<id>/} / {@code worldpacks/<id>/} 前缀，见 ScriptPack#idPathPrefix）。
     */
    public void cancelScriptByPrefix(String scriptIdPrefix) {
        if (scriptIdPrefix == null || scriptIdPrefix.isBlank()) return;
        List<Integer> idsToCancel = List.copyOf(scriptIds.entrySet()).stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().startsWith(scriptIdPrefix))
                .map(Map.Entry::getKey)
                .toList();
        ready.removeIf(callback -> idsToCancel.contains(callback.id));
        idsToCancel.forEach(this::cancel);
    }

    public void flushReadyCallbacks() {
        TimerCallback callback;
        int executed = 0;
        while (executed < MAX_CALLBACKS_PER_FLUSH && (callback = ready.poll()) != null) {
            ScheduledFuture<?> future = tasks.get(callback.id);
            if (future == null) continue;
            if (!callback.repeating) {
                tasks.remove(callback.id);
                scriptIds.remove(callback.id);
            }
            execute(callback.id, callback.callback, callback.args);
            executed++;
        }
    }

    /** 有界入队：队列已满时丢弃新回调并记一次错误（ErrorTracker 按签名去重，不会刷屏）。 */
    private void enqueueReady(TimerCallback callback) {
        if (ready.size() >= MAX_READY_QUEUE_SIZE) {
            errorTracker.recordCallbackError(scriptType, "timer",
                    new IllegalStateException("Timer queue overflow (" + MAX_READY_QUEUE_SIZE
                            + " callbacks pending); dropping new callback id=" + callback.id));
            return;
        }
        ready.add(callback);
    }

    public boolean hasPendingCallbacks() {
        return !ready.isEmpty() || tasks.values().stream().anyMatch(future -> !future.isDone() && !future.isCancelled());
    }

    @Override
    @HideFromJS
    public void close() {
        cancelAll();
        scheduler.shutdownNow();
    }

    private void cancel(int id) {
        ScheduledFuture<?> future = tasks.remove(id);
        scriptIds.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void recordScriptId(int id, Value callback) {
        if (callback == null) return;
        Context context = callback.getContext();
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        if (scriptId == null || scriptId.isBlank()) {
            scriptId = activeScriptIds.get(context);
        }
        if (scriptId != null && !scriptId.isBlank()) {
            scriptIds.put(id, scriptId);
        }
    }

    private void execute(int id, Value callback, Object[] args) {
        if (callback == null) return;
        Context context = callback.getContext();
        if (ScriptManager.isContextDead(context)) {
            // Context 已被 Graal 关闭（语句上限等）：回调闭包指向死环境，静默跳过，
            // 避免每次 tick 都在死 Context 上抛错刷屏；所属 ScriptManager 会在下次取用时重建
            return;
        }
        if (!callback.canExecute()) return;
        String scriptId = scriptIds.get(id);
        try {
            // No synchronized(context): timer callbacks are flushed on the game tick thread
            // (see NekoNodeTimers.flushReadyCallbacks), and the Graal Context is single-threaded.
            String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
            if (scriptId != null && !scriptId.isBlank()) {
                activeScriptIds.put(context, scriptId);
            }
            try {
                callback.executeVoid(args == null ? new Object[0] : args);
            } finally {
                // 不移除 activeScriptIds：保留「该 Context 最近执行的脚本」归属，使
                // host 侧触发（无 currentScriptId）注册的 timer 也能在对应脚本 reload 时
                // 被 cancelScript 清理，避免孤儿 interval 泄漏。
                ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
            }
        } catch (Throwable e) {
            // 语句上限关闭 Context 的 kill 在稳态下只能从回调路径发现（入口早已执行完）：
            // 上报所属 ScriptManager，使其在下次取用时自动重建环境，而不是静默死亡
            ScriptManager.reportContextKilled(context, e);
            errorTracker.recordCallbackError(scriptType, "timer", e);
        }
    }

    private record TimerCallback(int id, boolean repeating, Value callback, Object[] args) {}
}
