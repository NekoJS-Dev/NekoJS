package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NekoNodeTimers implements AutoCloseable {
    private final ScriptType scriptType;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "NekoJS-NodeTimers");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger ids = new AtomicInteger(1);
    private final Map<Integer, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final Queue<TimerCallback> ready = new ConcurrentLinkedQueue<>();

    public NekoNodeTimers(ScriptType scriptType) {
        this.scriptType = scriptType;
    }

    public int setTimeout(Value callback, long delayMillis, Object... args) {
        rejectStartupTimer(delayMillis);
        int id = ids.getAndIncrement();
        ScheduledFuture<?> future = scheduler.schedule(() -> ready.add(new TimerCallback(id, false, callback, args)), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
        tasks.put(id, future);
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
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> ready.add(new TimerCallback(id, true, callback, args)), delay, delay, TimeUnit.MILLISECONDS);
        tasks.put(id, future);
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
    }

    public void flushReadyCallbacks() {
        TimerCallback callback;
        while ((callback = ready.poll()) != null) {
            ScheduledFuture<?> future = tasks.get(callback.id);
            if (future == null) continue;
            if (!callback.repeating) {
                tasks.remove(callback.id);
            }
            execute(callback.callback, callback.args);
        }
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
        if (future != null) {
            future.cancel(false);
        }
    }

    private void execute(Value callback, Object[] args) {
        if (callback == null || !callback.canExecute()) return;
        Context context = callback.getContext();
        try {
            synchronized (context) {
                callback.executeVoid(args == null ? new Object[0] : args);
            }
        } catch (Throwable e) {
            NekoErrorTracker.recordCallbackError(scriptType, "timer", e);
        }
    }

    private record TimerCallback(int id, boolean repeating, Value callback, Object[] args) {}
}
