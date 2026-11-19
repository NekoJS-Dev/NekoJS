package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a server lifecycle transition.
 */
@Doc("Immutable portable snapshot of a server lifecycle transition.")
public final class StableServerLifecycleEvent {
    private final String stage;
    private final int tick;

    /**
     * Creates an immutable server lifecycle event snapshot.
     *
     * @param stage lifecycle stage name; null is normalized to an empty string
     * @param tick native server tick count captured at the transition
     */
    @Doc("Creates an immutable server lifecycle event snapshot.")
    @Param(name = "stage", value = "lifecycle stage name; null is normalized to an empty string")
    @Param(name = "tick", value = "native server tick count captured at the transition")
    public StableServerLifecycleEvent(String stage, int tick) {
        this.stage = Objects.requireNonNullElse(stage, "");
        this.tick = tick;
    }

    /**
     * Returns the server lifecycle stage name.
     *
     * @return non-null lifecycle stage name
     */
    @Doc("Returns the server lifecycle stage name.")
    @Return("non-null lifecycle stage name")
    public String getStage() {
        return stage;
    }

    /**
     * Returns the native server tick count at the lifecycle transition.
     *
     * @return server tick count
     */
    @Doc("Returns the native server tick count at the lifecycle transition.")
    @Return("server tick count")
    public int getTick() {
        return tick;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableServerLifecycleEvent that)) return false;
        return Objects.equals(this.stage, that.stage)
                && this.tick == that.tick;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stage, tick);
    }

    @Override
    public String toString() {
        return "StableServerLifecycleEvent{"
                + "stage='" + stage + "'"
                + ", tick=" + tick
                + "}";
    }
}
