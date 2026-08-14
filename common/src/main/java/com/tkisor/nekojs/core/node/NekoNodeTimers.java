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
