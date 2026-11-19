package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a level lifecycle or tick event.
 */
@Doc("Immutable portable snapshot of a level lifecycle or tick event.")
public final class StableLevelEvent {
    private final String dimension;
    private final boolean clientSide;
    private final long gameTime;
    private final long dayTime;

    /**
     * Creates an immutable level event snapshot.
     *
     * @param dimension namespaced dimension id; null is normalized to an empty string
     * @param clientSide true when the level belongs to the logical client
     * @param gameTime total elapsed world game time in ticks
     * @param dayTime current day-cycle time of the level in ticks
     */
    @Doc("Creates an immutable level event snapshot.")
    @Param(name = "dimension", value = "namespaced dimension id; null is normalized to an empty string")
    @Param(name = "clientSide", value = "true when the level belongs to the logical client")
    @Param(name = "gameTime", value = "total elapsed world game time in ticks")
    @Param(name = "dayTime", value = "current day-cycle time of the level in ticks")
    public StableLevelEvent(String dimension, boolean clientSide, long gameTime, long dayTime) {
        this.dimension = Objects.requireNonNullElse(dimension, "");
        this.clientSide = clientSide;
        this.gameTime = gameTime;
        this.dayTime = dayTime;
    }

    /**
     * Returns the namespaced dimension id of the level.
     *
     * @return non-null dimension id
     */
    @Doc("Returns the namespaced dimension id of the level.")
    @Return("non-null dimension id")
    public String getDimension() {
        return dimension;
    }

    /**
     * Returns whether the level is client-side.
     *
     * @return true on the logical client, false on the server
     */
    @Doc("Returns whether the level is client-side.")
    @Return("true on the logical client, false on the server")
    public boolean isClientSide() {
        return clientSide;
    }

    /**
     * Returns the total elapsed game time of the level in ticks.
     *
     * @return elapsed world game time in ticks
     */
    @Doc("Returns the total elapsed game time of the level in ticks.")
    @Return("elapsed world game time in ticks")
    public long getGameTime() {
        return gameTime;
    }

    /**
     * Returns the current day-cycle time of the level in ticks.
     *
     * @return level day time in ticks
     */
    @Doc("Returns the current day-cycle time of the level in ticks.")
    @Return("level day time in ticks")
    public long getDayTime() {
        return dayTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableLevelEvent that)) return false;
        return Objects.equals(this.dimension, that.dimension)
                && this.clientSide == that.clientSide
                && this.gameTime == that.gameTime
                && this.dayTime == that.dayTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, clientSide, gameTime, dayTime);
    }

    @Override
    public String toString() {
        return "StableLevelEvent{"
                + "dimension='" + dimension + "'"
                + ", clientSide=" + clientSide
                + ", gameTime=" + gameTime
                + ", dayTime=" + dayTime
                + "}";
    }
}
