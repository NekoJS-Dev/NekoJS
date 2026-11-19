package com.tkisor.nekojs.bindings.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.Supplier;

public final class NeoForgeSmeltingRouting {
    private static final ThreadLocal<Boolean> EMPTY_TAKE_PENDING = new ThreadLocal<>();
    private static final ThreadLocal<Event> EMPTY_TAKE_EVENT = new ThreadLocal<>();

    private NeoForgeSmeltingRouting() {}

    static boolean isStable(PlayerEvent.ItemSmeltedEvent event) {
        return event.getEntity() instanceof ServerPlayer && EMPTY_TAKE_EVENT.get() != event;
    }

    public static void duringEmptyTake(Runnable action) {
        Boolean previous = EMPTY_TAKE_PENDING.get();
        EMPTY_TAKE_PENDING.set(true);
        try {
            action.run();
        } finally {
            if (previous == null) EMPTY_TAKE_PENDING.remove();
            else EMPTY_TAKE_PENDING.set(previous);
        }
    }

    public static Event postLegacyEvent(Event event, Supplier<Event> post) {
        if (!Boolean.TRUE.equals(EMPTY_TAKE_PENDING.get())) return post.get();
        EMPTY_TAKE_PENDING.remove();
        Event previous = EMPTY_TAKE_EVENT.get();
        EMPTY_TAKE_EVENT.set(event);
        try {
            return post.get();
        } finally {
            if (previous == null) EMPTY_TAKE_EVENT.remove();
            else EMPTY_TAKE_EVENT.set(previous);
        }
    }
}
