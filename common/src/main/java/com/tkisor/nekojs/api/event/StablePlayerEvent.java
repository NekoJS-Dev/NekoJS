package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.annotation.ScriptNullable;
import com.tkisor.nekojs.api.entity.StableEntity;
import com.tkisor.nekojs.api.entity.StablePlayer;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable portable snapshot of a player lifecycle or state event.
 */
@Doc("Immutable portable snapshot of a player lifecycle or state event.")
public final class StablePlayerEvent {
    private final String playerName;
    private final String playerUuid;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final boolean clientSide;
    private final @Nullable StablePlayer player;
    private final @Nullable StablePlayer originalPlayer;
    private final @Nullable StableEntity target;

    /**
     * Creates an immutable player event snapshot.
     *
     * @param playerName player profile or game name; null is normalized to an empty string
     * @param playerUuid canonical string representation of the player UUID; null becomes empty
     * @param dimension namespaced dimension id of the player; null becomes empty
     * @param x player X coordinate
     * @param y player Y coordinate
     * @param z player Z coordinate
     */
    @Doc("Creates an immutable player event snapshot.")
    @Param(name = "playerName", value = "player profile or game name; null is normalized to an empty string")
    @Param(name = "playerUuid", value = "canonical string representation of the player UUID; null becomes empty")
    @Param(name = "dimension", value = "namespaced dimension id of the player; null becomes empty")
    @Param(name = "x", value = "player X coordinate")
    @Param(name = "y", value = "player Y coordinate")
    @Param(name = "z", value = "player Z coordinate")
    public StablePlayerEvent(String playerName, String playerUuid, String dimension, double x, double y, double z) {
        this(playerName, playerUuid, dimension, x, y, z, null, null, null);
    }

    @ScriptInternalConstructor
    public StablePlayerEvent(
            String playerName, String playerUuid, String dimension, double x, double y, double z,
            @Nullable StablePlayer player, @Nullable StablePlayer originalPlayer, @Nullable StableEntity target) {
        this(playerName, playerUuid, dimension, x, y, z, false, player, originalPlayer, target);
    }

    @HideFromJS
    public static StablePlayerEvent forClient(
            String playerName, String playerUuid, String dimension, double x, double y, double z) {
        return forClient(playerName, playerUuid, dimension, x, y, z, null, null, null);
    }

    @HideFromJS
    public static StablePlayerEvent forClient(
            String playerName, String playerUuid, String dimension, double x, double y, double z,
            @Nullable StablePlayer player, @Nullable StablePlayer originalPlayer, @Nullable StableEntity target) {
        return new StablePlayerEvent(playerName, playerUuid, dimension, x, y, z, true, player, originalPlayer, target);
    }

    private StablePlayerEvent(
            String playerName, String playerUuid, String dimension, double x, double y, double z, boolean clientSide,
            @Nullable StablePlayer player, @Nullable StablePlayer originalPlayer, @Nullable StableEntity target) {
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.playerUuid = Objects.requireNonNullElse(playerUuid, "");
        this.dimension = Objects.requireNonNullElse(dimension, "");
        this.x = x;
        this.y = y;
        this.z = z;
        this.clientSide = clientSide;
        this.player = player;
        this.originalPlayer = originalPlayer;
        this.target = target;
    }

    /**
     * Returns the player this event is about.
     *
     * @return event player, or null if unavailable
     */
    @Doc("Returns the player this event is about.")
    @Return("event player, or null if unavailable")
    @ScriptNullable
    public @Nullable StablePlayer getPlayer() {
        return player;
    }

    /**
     * Returns the previous player instance when a clone or respawn replaces it.
     *
     * @return original player, or null if no different original player was supplied
     */
    @Doc("Returns the previous player instance when a clone or respawn replaces it.")
    @Return("original player, or null if no different original player was supplied")
    @ScriptNullable
    public @Nullable StablePlayer getOriginalPlayer() {
        return originalPlayer;
    }

    /**
     * Returns the entity the player interacted with.
     *
     * @return interaction target, or null if unavailable
     */
    @Doc("Returns the entity the player interacted with.")
    @Return("interaction target, or null if unavailable")
    @ScriptNullable
    public @Nullable StableEntity getTarget() {
        return target;
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
     * Returns the namespaced dimension id of the player.
     *
     * @return non-null dimension id
     */
    @Doc("Returns the namespaced dimension id of the player.")
    @Return("non-null dimension id")
    public String getDimension() {
        return dimension;
    }

    /**
     * Returns the player X coordinate.
     *
     * @return player X coordinate
     */
    @Doc("Returns the player X coordinate.")
    @Return("player X coordinate")
    public double getX() {
        return x;
    }

    /**
     * Returns the player Y coordinate.
     *
     * @return player Y coordinate
     */
    @Doc("Returns the player Y coordinate.")
    @Return("player Y coordinate")
    public double getY() {
        return y;
    }

    /**
     * Returns the player Z coordinate.
     *
     * @return player Z coordinate
     */
    @Doc("Returns the player Z coordinate.")
    @Return("player Z coordinate")
    public double getZ() {
        return z;
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
            return clientSide ? StageHandle.clientReadOnly(null) : StageHandle.EMPTY;
        }
        StagePlayerRef playerRef = clientSide
                ? StagePlayerRef.client(playerUuid, playerName)
                : StagePlayerRef.of(playerUuid, playerName);
        return StageHandleResolver.resolvePlayer(playerRef);
    }

    @HideFromJS
    public boolean clientSide() {
        return clientSide;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StablePlayerEvent that)) return false;
        return Objects.equals(this.playerName, that.playerName)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.dimension, that.dimension)
                && Double.compare(this.x, that.x) == 0
                && Double.compare(this.y, that.y) == 0
                && Double.compare(this.z, that.z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, playerUuid, dimension, x, y, z);
    }

    @Override
    public String toString() {
        return "StablePlayerEvent{"
                + "playerName='" + playerName + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", dimension='" + dimension + "'"
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z
                + "}";
    }
}
