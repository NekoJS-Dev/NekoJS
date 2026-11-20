package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.annotation.ScriptNullable;
import com.tkisor.nekojs.api.entity.StablePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable portable snapshot of a server datapack synchronization event.
 */
@Doc("Immutable portable snapshot of a server datapack synchronization event.")
public final class StableDatapackSyncEvent {
    private final String playerName;
    private final int playerCount;
    private final boolean reloadSync;
    private final @Nullable StablePlayer player;

    /**
     * Creates an immutable datapack synchronization event snapshot.
     *
     * @param playerName target player name when syncing a single player, or empty for broadcast; null becomes empty
     * @param playerCount number of players receiving the synchronized datapack contents
     * @param reloadSync true when triggered by a server reload rather than initial player login
     */
    @Doc("Creates an immutable datapack synchronization event snapshot.")
    @Param(name = "playerName", value = "target player name when syncing a single player, or empty for broadcast; null becomes empty")
    @Param(name = "playerCount", value = "number of players receiving the synchronized datapack contents")
    @Param(name = "reloadSync", value = "true when triggered by a server reload rather than initial player login")
    public StableDatapackSyncEvent(String playerName, int playerCount, boolean reloadSync) {
        this(playerName, playerCount, reloadSync, null);
    }

    @ScriptInternalConstructor
    public StableDatapackSyncEvent(
            String playerName,
            int playerCount,
            boolean reloadSync,
            @Nullable StablePlayer player) {
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.playerCount = playerCount;
        this.reloadSync = reloadSync;
        this.player = player;
    }

    /**
     * Returns the player targeted by a single-player datapack sync.
     *
     * @return target player, or null for a broadcast or when unavailable
     */
    @Doc("Returns the player targeted by a single-player datapack sync.")
    @Return("target player, or null for a broadcast or when unavailable")
    @ScriptNullable
    public @Nullable StablePlayer getPlayer() {
        return player;
    }

    /**
     * Returns the target player name, or an empty string when syncing to multiple players.
     *
     * @return non-null target player name
     */
    @Doc("Returns the target player name, or an empty string when syncing to multiple players.")
    @Return("non-null target player name")
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Returns the number of players receiving the datapack sync.
     *
     * @return recipient player count
     */
    @Doc("Returns the number of players receiving the datapack sync.")
    @Return("recipient player count")
    public int getPlayerCount() {
        return playerCount;
    }

    /**
     * Returns whether this synchronization was triggered by a reload.
     *
     * @return true for reload sync, false for login sync
     */
    @Doc("Returns whether this synchronization was triggered by a reload.")
    @Return("true for reload sync, false for login sync")
    public boolean isReloadSync() {
        return reloadSync;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableDatapackSyncEvent that)) return false;
        return Objects.equals(this.playerName, that.playerName)
                && this.playerCount == that.playerCount
                && this.reloadSync == that.reloadSync;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, playerCount, reloadSync);
    }

    @Override
    public String toString() {
        return "StableDatapackSyncEvent{"
                + "playerName='" + playerName + "'"
                + ", playerCount=" + playerCount
                + ", reloadSync=" + reloadSync
                + "}";
    }
}
