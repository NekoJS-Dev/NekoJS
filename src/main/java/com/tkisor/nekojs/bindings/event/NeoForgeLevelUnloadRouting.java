package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class NeoForgeLevelUnloadRouting {
    private static final ThreadLocal<StopScope> STOP = new ThreadLocal<>();

    private NeoForgeLevelUnloadRouting() {
    }

    public static void duringStop(Runnable stop) {
        StopScope previous = STOP.get();
        STOP.set(new StopScope());
        try {
            stop.run();
        } finally {
            if (previous == null) {
                STOP.remove();
            } else {
                STOP.set(previous);
            }
        }
    }

    public static void postBeforeClose(ServerLevel level) {
        StopScope scope = STOP.get();
        if (scope != null && scope.notified.add(level)) {
            LevelEvents.UNLOADED.postStableModuleNotification(new PreCloseUnload(level), ScriptType.SERVER);
        }
    }

    public static <T> T postNative(LevelEvent.Unload event, Supplier<T> post) {
        var scope = STOP.get();
        if (scope == null || !(event.getLevel() instanceof ServerLevel level) || !scope.notified.add(level)) {
            return post.get();
        }
        var previous = scope.nativeBeforeClose;
        scope.nativeBeforeClose = event;
        try {
            return post.get();
        } finally {
            scope.nativeBeforeClose = previous;
        }
    }

    static boolean isVersionedUnload(LevelEvent.Unload event) {
        return !(event instanceof PreCloseUnload);
    }

    static boolean isStableUnload(LevelEvent.Unload event) {
        if (event instanceof PreCloseUnload) return true;
        StopScope scope = STOP.get();
        return scope == null || scope.nativeBeforeClose == event || !scope.notified.contains(event.getLevel());
    }

    private static final class StopScope {
        private final Set<ServerLevel> notified = Collections.newSetFromMap(new IdentityHashMap<>());
        private LevelEvent.Unload nativeBeforeClose;
    }

    private static final class PreCloseUnload extends LevelEvent.Unload {
        private PreCloseUnload(ServerLevel level) {
            super(level);
        }
    }
}
