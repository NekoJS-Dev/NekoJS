package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.List;
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
    private final Map<Integer, String> scriptIds = new ConcurrentHashMap<>();
    private final Map<Context, String> activeScriptIds = new ConcurrentHashMap<>();
    private final Queue<TimerCallback> ready = new ConcurrentLinkedQueue<>();

    public NekoNodeTimers(ScriptType scriptType) {
        this.scriptType = scriptType;
    }

    public int setTimeout(Value callback, long delayMillis, Object... args) {
        rejectStartupTimer(delayMillis);
        int id = ids.getAndIncrement();
        ScheduledFuture<?> future = scheduler.schedule(() -> ready.add(new TimerCallback(id, false, callback, args)), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
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
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> ready.add(new TimerCallback(id, true, callback, args)), delay, delay, TimeUnit.MILLISECONDS);
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
        while ((callback = ready.poll()) != null) {
            ScheduledFuture<?> future = tasks.get(callback.id);
            if (future == null) continue;
            if (!callback.repeating) {
                tasks.remove(callback.id);
                scriptIds.remove(callback.id);
            }
            execute(callback.id, callback.callback, callback.args);
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
        scriptIds.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void recordScriptId(int id, Value callback) {
        if (callback == null) return;
        Context context = callback.getContext();
        String scriptId = ScriptManager.from(context).getCurrentScriptId(context);
        if (scriptId == null || scriptId.isBlank()) {
            scriptId = activeScriptIds.get(context);
        }
        if (scriptId != null && !scriptId.isBlank()) {
            scriptIds.put(id, scriptId);
        }
    }

    private void execute(int id, Value callback, Object[] args) {
        if (callback == null || !callback.canExecute()) return;
        Context context = callback.getContext();
        String scriptId = scriptIds.get(id);
        try {
            synchronized (context) {
                ScriptManager manager = ScriptManager.from(context);
                String previousScriptId = manager.switchCurrentScriptId(context, scriptId);
                if (scriptId != null && !scriptId.isBlank()) {
                    activeScriptIds.put(context, scriptId);
                }
                try {
                    callback.executeVoid(args == null ? new Object[0] : args);
                } finally {
                    if (scriptId != null && !scriptId.isBlank()) {
                        activeScriptIds.remove(context);
                    }
                    manager.restoreCurrentScriptId(context, previousScriptId);
                }
            }
        } catch (Throwable e) {
            NekoErrorTracker.recordCallbackError(scriptType, "timer", e);
        }
    }

    private record TimerCallback(int id, boolean repeating, Value callback, Object[] args) {}
}
