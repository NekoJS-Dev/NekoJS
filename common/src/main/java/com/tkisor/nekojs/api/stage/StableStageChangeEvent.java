package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable portable snapshot of a stage grant or revoke transition on a player or the server.
 */
@Doc("Immutable portable snapshot of a stage grant or revoke transition on a player or the server.")
public final class StableStageChangeEvent {
    private final String scope;
    private final String stage;
    private final String playerUuid;
    private final String playerName;
    private final boolean temporary;
    private final long remainingTicks;
    private final String reason;
    private final @Nullable StageHandle clientStages;

    /**
     * Creates an immutable stage change event snapshot.
     *
     * @param scope          stage scope ({@code "player"} or {@code "server"})
     * @param stage          stage identifier
     * @param playerUuid     canonical player UUID string (empty for server scope)
     * @param playerName     player name (empty for server scope)
     * @param temporary      whether the stage entry is temporary
     * @param remainingTicks remaining ticks ({@code > 0} if temporary, {@code -1} if permanent, {@code 0} if removed/expired)
     * @param reason         change reason ({@code "manual"}, {@code "clear"}, {@code "replace"}, or {@code "expired"})
     */
    @Doc("Creates an immutable stage change event snapshot.")
    @Param(name = "scope", value = "stage scope ('player' or 'server')")
    @Param(name = "stage", value = "stage identifier")
    @Param(name = "playerUuid", value = "canonical player UUID string; empty for server scope")
    @Param(name = "playerName", value = "player name; empty for server scope")
    @Param(name = "temporary", value = "whether the stage entry is temporary")
    @Param(name = "remainingTicks", value = "remaining ticks for temporary stages, -1 for permanent, or 0 when removed")
    @Param(name = "reason", value = "change reason ('manual', 'clear', 'replace', or 'expired')")
    public StableStageChangeEvent(
            @Nullable String scope,
            @Nullable String stage,
            @Nullable String playerUuid,
            @Nullable String playerName,
            boolean temporary,
            long remainingTicks,
            @Nullable String reason) {
        this(scope, stage, playerUuid, playerName, temporary, remainingTicks, reason, null);
    }

    @HideFromJS
    public static StableStageChangeEvent forClient(
            String scope,
            String stage,
            String playerUuid,
            @Nullable String playerName,
            boolean temporary,
            long remainingTicks,
            String reason,
            StageReadView stages) {
        return new StableStageChangeEvent(
                scope, stage, playerUuid, playerName, temporary, remainingTicks, reason,
                StageHandle.clientReadOnly(stages));
    }

    private StableStageChangeEvent(
            @Nullable String scope,
            @Nullable String stage,
            @Nullable String playerUuid,
            @Nullable String playerName,
            boolean temporary,
            long remainingTicks,
            @Nullable String reason,
            @Nullable StageHandle clientStages) {
        this.scope = scope == null || scope.isBlank() ? "player" : scope.trim().toLowerCase(Locale.ROOT);
        this.stage = Objects.requireNonNullElse(stage, "");
        this.playerUuid = playerUuid == null ? "" : playerUuid.trim();
        this.playerName = Objects.requireNonNullElse(playerName, "");
        this.temporary = temporary;
        this.remainingTicks = remainingTicks;
        this.reason = reason == null || reason.isBlank() ? "manual" : reason.trim().toLowerCase(Locale.ROOT);
        this.clientStages = clientStages;
    }

    /**
     * Creates a player-scoped stage change event snapshot.
     */
    @HideFromJS
    public static StableStageChangeEvent forPlayer(
            String stage,
            String playerUuid,
            @Nullable String playerName,
            boolean temporary,
            long remainingTicks,
            String reason) {
        return new StableStageChangeEvent("player", stage, playerUuid, playerName, temporary, remainingTicks, reason);
    }

    /**
     * Creates a server-scoped stage change event snapshot.
     */
    @HideFromJS
    public static StableStageChangeEvent forServer(
            String stage,
            boolean temporary,
            long remainingTicks,
            String reason) {
        return new StableStageChangeEvent("server", stage, "", "", temporary, remainingTicks, reason);
    }

    /**
     * Returns the scope of this stage transition ({@code "player"} or {@code "server"}).
     *
     * @return {@code "player"} or {@code "server"}
     */
    @Doc("Returns the scope of this stage transition ('player' or 'server').")
    @Return("'player' or 'server'")
    public String getScope() {
        return scope;
    }

    /**
     * Returns the affected stage identifier.
     *
     * @return non-null stage identifier
     */
    @Doc("Returns the affected stage identifier.")
    @Return("non-null stage identifier")
    public String getStage() {
        return stage;
    }

    /**
     * Returns the canonical player UUID string, or an empty string for server-scoped events.
     *
     * @return non-null player UUID string
     */
    @Doc("Returns the canonical player UUID string, or an empty string for server-scoped events.")
    @Return("non-null player UUID string")
    public String getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Returns the player name, or an empty string for server-scoped events.
     *
     * @return non-null player name
     */
    @Doc("Returns the player name, or an empty string for server-scoped events.")
    @Return("non-null player name")
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Returns whether the affected stage entry is temporary.
     *
     * @return true if temporary
     */
    @Doc("Returns whether the affected stage entry is temporary.")
    @Return("true if temporary")
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * Returns remaining ticks associated with the stage transition.
     *
     * @return remaining tick count ({@code -1L} when permanent, {@code 0L} when removed/expired)
     */
    @Doc("Returns remaining ticks associated with the stage transition.")
    @Return("remaining tick count (-1 when permanent, 0 when removed or expired)")
    public long getRemainingTicks() {
        return remainingTicks;
    }

    /**
     * Returns the transition reason ({@code "manual"}, {@code "clear"}, {@code "replace"}, or {@code "expired"}).
     *
     * @return non-null reason string
     */
    @Doc("Returns the transition reason ('manual', 'clear', 'replace', or 'expired').")
    @Return("non-null reason string")
    public String getReason() {
        return reason;
    }

    /**
     * Returns the live stage handle for the player or server associated with this event.
     *
     * @return non-null stage handle
     */
    @Doc("Returns the live stage handle for the player or server associated with this event.")
    @Return("non-null stage handle")
    public StageHandle getStages() {
        if (clientStages != null) {
            return clientStages;
        }
        return "server".equals(scope)
                ? StageHandleResolver.resolveServer()
                : (playerUuid.isEmpty()
                        ? StageHandle.EMPTY
                        : StageHandleResolver.resolvePlayer(StagePlayerRef.of(playerUuid, playerName)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableStageChangeEvent that)) return false;
        return this.temporary == that.temporary
                && this.remainingTicks == that.remainingTicks
                && Objects.equals(this.scope, that.scope)
                && Objects.equals(this.stage, that.stage)
                && Objects.equals(this.playerUuid, that.playerUuid)
                && Objects.equals(this.playerName, that.playerName)
                && Objects.equals(this.reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, stage, playerUuid, playerName, temporary, remainingTicks, reason);
    }

    @Override
    public String toString() {
        return "StableStageChangeEvent{"
                + "scope='" + scope + "'"
                + ", stage='" + stage + "'"
                + ", playerUuid='" + playerUuid + "'"
                + ", playerName='" + playerName + "'"
                + ", temporary=" + temporary
                + ", remainingTicks=" + remainingTicks
                + ", reason='" + reason + "'"
                + "}";
    }
}
