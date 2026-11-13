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
    /**
     * Size limit of the ready queue. While the game thread is blocked, a 1ms interval piles up without
     * bound; past the limit a new callback is dropped and recorded.
     */
    private static final int MAX_READY_QUEUE_SIZE = 4096;
    /**
     * Maximum callbacks run in one flush, so one tick cannot keep processing callbacks and freeze the tick.
     */
    private static final int MAX_CALLBACKS_PER_FLUSH = 256;
    /**
     * Limit on the number of timers that can be registered at once.
     * {@code while(true){setInterval(()=>{},1000)}} would otherwise stuff entries into {@code tasks}
     * without bound (each holds a JS callback Value and a schedule handle) and exhaust memory before
     * the 50M statement limit fires. 1024 is far above normal script needs (timers are a long-lived
     * resource in the sandbox, not a throughput tool) and stays below the ready queue limit, since
     * every interval keeps posting to that queue. Past the limit an {@link IllegalStateException} is
     * thrown to the script, the same way {@link #rejectStartupTimer} rejects delayed timers in STARTUP.
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
    private volatile boolean closed;

    public NekoNodeTimers(ScriptType scriptType, ErrorTracker errorTracker) {
        this.scriptType = scriptType;
        this.errorTracker = errorTracker;
    }

    public synchronized int setTimeout(Value callback, long delayMillis, Object... args) {
        rejectClosed();
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

    public synchronized int setInterval(Value callback, long delayMillis, Object... args) {
        rejectClosed();
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
     * Rejects a registration past the limit. The check runs before an id is allocated or the timer is
     * scheduled and throws {@link IllegalStateException}; the exception travels back up the JS call
     * stack to the script and is recorded by the normal error reporting path.
     * clearTimeout/clearInterval or a flushed one-shot timer frees a slot and registration can go on.
     */
    private void rejectTimerOverflow() {
        if (tasks.size() >= MAX_SCHEDULED_TIMERS) {
            throw new IllegalStateException("Too many scheduled timers (limit " + MAX_SCHEDULED_TIMERS
                    + "); clearTimeout/clearInterval old timers before registering new ones");
        }
    }

    public synchronized void cancelAll() {
        tasks.keySet().forEach(this::cancel);
        ready.clear();
        scriptIds.clear();
        activeScriptIds.clear();
    }

    public synchronized void cancelScript(String scriptId) {
        if (scriptId == null || scriptId.isBlank()) return;
        List<Integer> idsToCancel = List.copyOf(scriptIds.entrySet()).stream()
                .filter(entry -> scriptId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        ready.removeIf(callback -> idsToCancel.contains(callback.id));
        idsToCancel.forEach(this::cancel);
    }

    /**
     * Cancels every timer whose script id starts with the given scriptIdPrefix, used when a whole script
     * pack is unloaded. Scripts inside a pack carry the {@code packs/<id>/} or
     * {@code worldpacks/<id>/} prefix; see ScriptPack#idPathPrefix.
     */
    public synchronized void cancelScriptByPrefix(String scriptIdPrefix) {
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
        while (!closed && executed < MAX_CALLBACKS_PER_FLUSH && (callback = ready.poll()) != null) {
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

    /**
     * Enqueues with a bound: when the queue is full a new callback is dropped and one error is recorded
     * (ErrorTracker deduplicates by signature, so this does not flood the log).
     */
    private synchronized void enqueueReady(TimerCallback callback) {
        if (closed || !tasks.containsKey(callback.id)) return;
        if (ready.size() >= MAX_READY_QUEUE_SIZE) {
            Context context = callback.callback == null ? null : callback.callback.getContext();
            errorTracker.recordCallbackError(context, scriptType, "timer",
                    new IllegalStateException("Timer queue overflow (" + MAX_READY_QUEUE_SIZE
                            + " callbacks pending); dropping new callback id=" + callback.id));
            return;
        }
        ready.add(callback);
    }

    public boolean hasPendingCallbacks() {
        return !closed && (!ready.isEmpty() || tasks.values().stream().anyMatch(future -> !future.isDone() && !future.isCancelled()));
    }

    @Override
    @HideFromJS
    public void close() {
        synchronized (this) {
            closed = true;
            cancelAll();
            scheduler.shutdownNow();
        }
        // The worker only queues callbacks; never wait while holding its enqueue monitor.
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("[NEKO-1017] Timer scheduler did not terminate within 2 seconds");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[NEKO-1017] Timer scheduler teardown was interrupted", failure);
        }
    }

    private void rejectClosed() {
        if (closed) throw new IllegalStateException("[NEKO-1017] Timer registration rejected after runtime close");
    }

    private synchronized void cancel(int id) {
        ScheduledFuture<?> future = tasks.remove(id);
        scriptIds.remove(id);
        if (future != null) {
            future.cancel(false);
        }
        ready.removeIf(callback -> callback.id == id);
    }

    private void recordScriptId(int id, Value callback) {
        // Callers hold the instance monitor, so `closed` cannot flip before this runs; `execute`
        // re-checks because the flush thread may run after close.
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
        if (closed || callback == null) return;
        Context context = callback.getContext();
        if (ScriptManager.isContextDead(context)) {
            // The Context has already been closed by Graal (statement limit and so on): the callback closure
            // points at a dead environment, so skip it silently. Throwing on a dead Context every tick
            // would flood the log; the owning ScriptManager rebuilds the environment on next use.
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
            // A lifecycle request on the same thread is rejected while a timer callback runs, matching the
            // dispatch point in EventBusJS; see ScriptManager#noteCallbackEnter.
            ScriptManager.noteCallbackEnter();
            try {
                callback.executeVoid(args == null ? new Object[0] : args);
            } finally {
                // Keep attribution for host registrations until this runtime is released.
                try { ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId); }
                finally { ScriptManager.noteCallbackExit(); }
            }
        } catch (Throwable e) {
            // A kill by the statement limit only shows up on the callback path in steady state (the entry has
            // long finished): report it to the owning ScriptManager so it rebuilds the environment on next
            // use instead of leaving the script silently dead.
            ScriptManager.reportContextKilled(context, e);
            errorTracker.recordCallbackError(context, scriptType, "timer", e);
        }
    }

    private record TimerCallback(int id, boolean repeating, Value callback, Object[] args) {}
}
