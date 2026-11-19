package com.tkisor.nekojs.bindings.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.function.Supplier;

public final class NeoForgeLevelSaveRouting {
    private static final ThreadLocal<LevelEvent.Save> DEFERRED = new ThreadLocal<>();

    private NeoForgeLevelSaveRouting() {}

    static boolean isVersioned(LevelEvent.Save event) {
        return !(event instanceof CompletedSave);
    }

    static boolean isStable(LevelEvent.Save event) {
        return DEFERRED.get() != event;
    }

    public static Event postBeforeFlush(LevelEvent.Save event, Supplier<Event> post) {
        LevelEvent.Save previous = DEFERRED.get();
        DEFERRED.set(event);
        try {
            return post.get();
        } finally {
            if (previous == null) DEFERRED.remove();
            else DEFERRED.set(previous);
        }
    }

    public static void postAfterFlush(LevelEvent.Save event) {
        if (event != null && LevelEvents.SAVED.hasListeners()) LevelEvents.SAVED.post(new CompletedSave(event));
    }

    private static final class CompletedSave extends LevelEvent.Save {
        private CompletedSave(LevelEvent.Save event) {
            super(event.getLevel());
        }
    }
}
