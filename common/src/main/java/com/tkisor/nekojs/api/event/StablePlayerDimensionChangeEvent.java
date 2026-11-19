package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import com.tkisor.nekojs.api.stage.StagePlayerRef;

import java.util.Objects;

/**
 * Immutable portable snapshot of a player dimension change event.
 */
@Doc("Immutable portable snapshot of a player dimension change event.")
public final class StablePlayerDimensionChangeEvent {
    private final String playerName;
    private final String playerUuid;
    private final String fromDimension;
    private final String toDimension;

    /**
     * Creates an immutable player dimension change event snapshot.
     *
     * @param playerName player name; null is normalized to an empty string
     * @param playerUuid canonical string representation of the player UUID; null becomes empty
     * @param fromDimension origin dimension id; null is normalized to an empty string
     * @param toDimension destination dimension id; null is normalized to an empty string
     */
    @Doc("Creates an immutable player dimension change event snapshot.")
    @Param(name = "playerName", value = "player name; null is normalized to an empty string")
    @Param(name = "playerUuid", value = "canonical string representation of the player UUID; null becomes empty")
    @Param(name = "fromDimension", value = "origin dimension id; null is normalized to an empty string")
    @Param(name = "toDimension", value = "destination dimension id; null is normalized to an empty string")
    public StablePlayerDimensionChangeEvent(String playerName, String playerUuid, String fromDimension, String toDimension) {
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.playerUuid = Objects.requireNonNullElse(playerUuid, "");
        this.fromDimension = Objects.requireNonNullElse(fromDimension, "");
        this.toDimension = Objects.requireNonNullElse(toDimension, "");
    }

    /**
     * Returns the player name.
     *
     * @return non-null player name
     */
    @Doc("Returns the player name.")
    @Return("non-null player name")
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Returns the canonical player UUID string.
     *
     * @return non-null player UUID string
     */
    @Doc("Returns the canonical player UUID string.")
    @Return("non-null player UUID string")
    public String getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Returns the origin dimension id before travel.
     *
     * @return non-null origin dimension id
     */
    @Doc("Returns the origin dimension id before travel.")
    @Return("non-null origin dimension id")
    public String getFromDimension() {
        return fromDimension;
    }

    /**
     * Returns the destination dimension id after travel.
     *
     * @return non-null destination dimension id
     */
    @Doc("Returns the destination dimension id after travel.")
    @Return("non-null destination dimension id")
    public String getToDimension() {
        return toDimension;
    }

    /**
     * Returns the stage handle for this player.
     *
     * @return non-null player stage handle
     */
    @Doc("Returns the stage handle for this player.")
    @Return("non-null player stage handle")
    public StageHandle getStages() {
        if (playerUuid.isBlank()) {
            return StageHandle.EMPTY;
        }
        return StageHandleResolver.resolvePlayer(StagePlayerRef.of(playerUuid, playerName));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StablePlayerDimensionChangeEvent that)) return false;
        return Objects.equals(this.playerName, that.playerName)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.fromDimension, that.fromDimension)
                && Objects.equals(this.toDimension, that.toDimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, playerUuid, fromDimension, toDimension);
    }

    @Override
    public String toString() {
        return "StablePlayerDimensionChangeEvent{"
                + "playerName='" + playerName + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", fromDimension='" + fromDimension + "'"
                + ", toDimension='" + toDimension + "'"
                + "}";
    }
}
