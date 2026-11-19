package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import com.tkisor.nekojs.api.stage.StagePlayerRef;

import java.util.Objects;

/**
 * Immutable portable snapshot of a player chat message event.
 */
@Doc("Immutable portable snapshot of a player chat message event.")
public final class StablePlayerChatEvent {
    private final String playerName;
    private final String playerUuid;
    private final String message;

    /**
     * Creates an immutable player chat event snapshot.
     *
     * @param playerName chatting player name; null is normalized to an empty string
     * @param playerUuid canonical string representation of the player UUID; null becomes empty
     * @param message plain chat message text; null is normalized to an empty string
     */
    @Doc("Creates an immutable player chat event snapshot.")
    @Param(name = "playerName", value = "chatting player name; null is normalized to an empty string")
    @Param(name = "playerUuid", value = "canonical string representation of the player UUID; null becomes empty")
    @Param(name = "message", value = "plain chat message text; null is normalized to an empty string")
    public StablePlayerChatEvent(String playerName, String playerUuid, String message) {
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.playerUuid = Objects.requireNonNullElse(playerUuid, "");
        this.message = Objects.requireNonNullElse(message, "");
    }

    /**
     * Returns the name of the player who sent the chat message.
     *
     * @return non-null player name
     */
    @Doc("Returns the name of the player who sent the chat message.")
    @Return("non-null player name")
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Returns the canonical UUID string of the player.
     *
     * @return non-null player UUID string
     */
    @Doc("Returns the canonical UUID string of the player.")
    @Return("non-null player UUID string")
    public String getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Returns the chat message content.
     *
     * @return non-null chat message text
     */
    @Doc("Returns the chat message content.")
    @Return("non-null chat message text")
    public String getMessage() {
        return message;
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
        if (!(o instanceof StablePlayerChatEvent that)) return false;
        return Objects.equals(this.playerName, that.playerName)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, playerUuid, message);
    }

    @Override
    public String toString() {
        return "StablePlayerChatEvent{"
                + "playerName='" + playerName + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", message='" + message + "'"
                + "}";
    }
}
