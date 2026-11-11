package com.tkisor.nekojs.api.event;

/** Immutable snapshot of the native server tick count at event delivery. */
public final class StableServerTickEvent {
    private final int tick;

    public StableServerTickEvent(int tick) {
        this.tick = tick;
    }

    /** The native server count, without rebasing or a separate script counter. */
    public int getTick() {
        return tick;
    }
}
