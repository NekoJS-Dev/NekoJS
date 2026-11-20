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
 * Immutable portable snapshot of a player item pickup, crafting, or smelting event.
 */
@Doc("Immutable portable snapshot of a player item pickup, crafting, or smelting event.")
public final class StablePlayerItemEvent {
    private final String playerName;
    private final String playerUuid;
    private final String itemId;
    private final int count;
    private final @Nullable StablePlayer player;

    /**
     * Creates an immutable player item event snapshot.
     *
     * @param playerName player name; null is normalized to an empty string
     * @param playerUuid canonical string representation of the player UUID; null becomes empty
     * @param itemId namespaced item id; null is normalized to an empty string
     * @param count stack item count
     */
    @Doc("Creates an immutable player item event snapshot.")
    @Param(name = "playerName", value = "player name; null is normalized to an empty string")
    @Param(name = "playerUuid", value = "canonical string representation of the player UUID; null becomes empty")
    @Param(name = "itemId", value = "namespaced item id; null is normalized to an empty string")
    @Param(name = "count", value = "stack item count")
    public StablePlayerItemEvent(String playerName, String playerUuid, String itemId, int count) {
        this(playerName, playerUuid, itemId, count, null);
    }

    @ScriptInternalConstructor
    public StablePlayerItemEvent(
            String playerName,
            String playerUuid,
            String itemId,
            int count,
            @Nullable StablePlayer player) {
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.playerUuid = Objects.requireNonNullElse(playerUuid, "");
        this.itemId = Objects.requireNonNullElse(itemId, "");
        this.count = count;
        this.player = player;
    }

    /**
     * Returns the player who picked up, crafted, or smelted the item.
     *
     * @return acting player, or null if unavailable
     */
    @Doc("Returns the player who picked up, crafted, or smelted the item.")
    @Return("acting player, or null if unavailable")
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
     * Returns the namespaced item id.
     *
     * @return non-null item id
     */
    @Doc("Returns the namespaced item id.")
    @Return("non-null item id")
    public String getItemId() {
        return itemId;
    }

    /**
     * Returns the item stack count.
     *
     * @return item stack count
     */
    @Doc("Returns the item stack count.")
    @Return("item stack count")
    public int getCount() {
        return count;
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
        if (!(o instanceof StablePlayerItemEvent that)) return false;
        return Objects.equals(this.playerName, that.playerName)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.itemId, that.itemId)
                && this.count == that.count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, playerUuid, itemId, count);
    }

    @Override
    public String toString() {
        return "StablePlayerItemEvent{"
                + "playerName='" + playerName + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", itemId='" + itemId + "'"
                + ", count=" + count
                + "}";
    }
}
