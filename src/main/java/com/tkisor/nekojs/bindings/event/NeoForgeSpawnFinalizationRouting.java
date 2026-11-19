package com.tkisor.nekojs.bindings.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.function.Supplier;

public final class NeoForgeSpawnFinalizationRouting {
    private static final ThreadLocal<Event> WITHOUT_INITIALIZATION = new ThreadLocal<>();

    private NeoForgeSpawnFinalizationRouting() {}

    static boolean isInitializing(FinalizeSpawnEvent event) {
        return WITHOUT_INITIALIZATION.get() != event;
    }

    public static Event postWithoutInitialization(Event event, Supplier<Event> post) {
        Event previous = WITHOUT_INITIALIZATION.get();
        WITHOUT_INITIALIZATION.set(event);
        try {
            return post.get();
        } finally {
            if (previous == null) WITHOUT_INITIALIZATION.remove();
            else WITHOUT_INITIALIZATION.set(previous);
        }
    }
}
