package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.annotation.ScriptNullable;
import com.tkisor.nekojs.api.entity.StablePlayer;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable portable snapshot of a player advancement completion or progress event.
 */
@Doc("Immutable portable snapshot of a player advancement completion or progress event.")
public final class StablePlayerAdvancementEvent {
    private final String playerName;
    private final String playerUuid;
    private final String advancementId;
    private final @Nullable StablePlayer player;

    /**
     * Creates an immutable player advancement event snapshot.
     *
     * @param playerName player name; null is normalized to an empty string
     * @param playerUuid canonical string representation of the player UUID; null becomes empty
     * @param advancementId namespaced advancement id; null is normalized to an empty string
     */
    @Doc("Creates an immutable player advancement event snapshot.")
    @Param(name = "playerName", value = "player name; null is normalized to an empty string")
    @Param(name = "playerUuid", value = "canonical string representation of the player UUID; null becomes empty")
    @Param(name = "advancementId", value = "namespaced advancement id; null is normalized to an empty string")
    public StablePlayerAdvancementEvent(String playerName, String playerUuid, String advancementId) {
        this(playerName, playerUuid, advancementId, null);
    }

    @ScriptInternalConstructor
    public StablePlayerAdvancementEvent(
            String playerName,
            String playerUuid,
            String advancementId,
            @Nullable StablePlayer player) {
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.playerUuid = Objects.requireNonNullElse(playerUuid, "");
        this.advancementId = Objects.requireNonNullElse(advancementId, "");
        this.player = player;
    }

    /**
     * Returns the player whose advancement changed.
     *
     * @return player whose advancement changed, or null if unavailable
     */
    @Doc("Returns the player whose advancement changed.")
    @Return("player whose advancement changed, or null if unavailable")
    @ScriptNullable
    public @Nullable StablePlayer getPlayer() {
        return player;
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
     * Returns the namespaced advancement id.
     *
     * @return non-null advancement id
     */
    @Doc("Returns the namespaced advancement id.")
    @Return("non-null advancement id")
    public String getAdvancementId() {
        return advancementId;
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
        if (!(o instanceof StablePlayerAdvancementEvent that)) return false;
        return Objects.equals(this.playerName, that.playerName)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.advancementId, that.advancementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, playerUuid, advancementId);
    }

    @Override
    public String toString() {
        return "StablePlayerAdvancementEvent{"
                + "playerName='" + playerName + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", advancementId='" + advancementId + "'"
                + "}";
    }
}
