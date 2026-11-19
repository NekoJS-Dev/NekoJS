package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable context passed to {@link StageDefinition} lifecycle callbacks ({@code whenGranted},
 * {@code everyTick}, and {@code whenExpired}).
 */
@Doc("Immutable context passed to StageDefinition lifecycle callbacks.")
public final class StageTickContext {
    private final String scope;
    private final String stage;
    private final String playerUuid;
    private final String playerName;
    private final long remainingTicks;

    /**
     * Creates an immutable stage lifecycle callback context.
     *
     * @param scope          stage scope ({@code "player"} or {@code "server"})
     * @param stage          stage identifier
     * @param playerUuid     canonical player UUID string (empty for server scope)
     * @param playerName     player name (empty for server scope)
     * @param remainingTicks remaining ticks ({@code -1L} when permanent, {@code 0L} on expiration, {@code > 0L} while ticking)
     */
    @Doc("Creates an immutable stage lifecycle callback context.")
    @Param(name = "scope", value = "stage scope ('player' or 'server')")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "playerUuid", value = "canonical player UUID string; empty for server scope")
    @Param(name = "playerName", value = "player name; empty for server scope")
    @Param(name = "remainingTicks", value = "remaining ticks (-1 when permanent, 0 on expiration, > 0 while active)")
    public StageTickContext(
            @Nullable String scope,
            @Nullable String stage,
            @Nullable String playerUuid,
            @Nullable String playerName,
            long remainingTicks) {
        this.scope = scope == null || scope.isBlank() ? "player" : scope.trim().toLowerCase(Locale.ROOT);
        this.stage = Objects.requireNonNullElse(stage, "");
        this.playerUuid = playerUuid == null ? "" : playerUuid.trim();
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.remainingTicks = remainingTicks;
    }

    /**
     * Creates a player-scoped stage callback context.
     */
    @HideFromJS
    public static StageTickContext forPlayer(
            String stage,
            String playerUuid,
            @Nullable String playerName,
            long remainingTicks) {
        return new StageTickContext("player", stage, playerUuid, playerName, remainingTicks);
    }

    /**
     * Creates a server-scoped stage callback context.
     */
    @HideFromJS
    public static StageTickContext forServer(String stage, long remainingTicks) {
        return new StageTickContext("server", stage, "", "", remainingTicks);
    }

    /**
     * Returns the scope of this stage callback ({@code "player"} or {@code "server"}).
     *
     * @return {@code "player"} or {@code "server"}
     */
    @Doc("Returns the scope of this stage callback ('player' or 'server').")
    @Return("'player' or 'server'")
    public String getScope() {
        return scope;
    }

    /**
     * Returns the stage identifier.
     *
     * @return non-null stage identifier
     */
    @Doc("Returns the stage identifier.")
    @Return("non-null stage identifier")
    public String getStage() {
        return stage;
    }

    /**
     * Returns the canonical player UUID string, or an empty string for server-scoped callbacks.
     *
     * @return non-null player UUID string
     */
    @Doc("Returns the canonical player UUID string, or an empty string for server-scoped callbacks.")
    @Return("non-null player UUID string")
    public String getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Returns the player name, or an empty string for server-scoped callbacks.
     *
     * @return non-null player name
     */
    @Doc("Returns the player name, or an empty string for server-scoped callbacks.")
    @Return("non-null player name")
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Returns remaining ticks for this stage entry.
     *
     * @return remaining ticks ({@code -1L} if permanent, {@code 0L} on expiration, {@code > 0L} while active)
     */
    @Doc("Returns remaining ticks for this stage entry.")
    @Return("remaining ticks (-1 if permanent, 0 on expiration, > 0 while active)")
    public long getRemainingTicks() {
        return remainingTicks;
    }

    /**
     * Returns the live stage handle for the player or server associated with this callback.
     *
     * @return non-null stage handle
     */
    @Doc("Returns the live stage handle for the player or server associated with this callback.")
    @Return("non-null stage handle")
    public StageHandle getStages() {
        return "server".equals(scope)
                ? StageHandleResolver.resolveServer()
                : (playerUuid.isEmpty()
                        ? StageHandle.EMPTY
                        : StageHandleResolver.resolvePlayer(StagePlayerRef.of(playerUuid, playerName)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StageTickContext that)) return false;
        return this.remainingTicks == that.remainingTicks
                && Objects.equals(this.scope, that.scope)
                && Objects.equals(this.stage, that.stage)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.playerName, that.playerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, stage, playerUuid, playerName, remainingTicks);
    }

    @Override
    public String toString() {
        return "StageTickContext{"
                + "scope='" + scope + "'"
                + ", stage='" + stage + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", playerName='" + playerName + "'"
                + ", remainingTicks=" + remainingTicks
                + "}";
    }
}
