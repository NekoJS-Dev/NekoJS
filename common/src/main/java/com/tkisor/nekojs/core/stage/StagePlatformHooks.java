package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform callback bridge implemented by NeoForge and Fabric stage services to deliver
 * network synchronization packets, HUD/chat/title notifications, and live online player name
 * lookups without introducing Minecraft or loader dependencies into {@code common}.
 */
public interface StagePlatformHooks {

    default boolean hasSyncRecipient(@Nullable String playerUuid) {
        return this != NOOP && (playerUuid == null || resolveOnlinePlayerName(playerUuid) != null);
    }

    /** No-op platform hooks used for headless unit tests and before server startup. */
    StagePlatformHooks NOOP = new StagePlatformHooks() {
        @Override
        public void syncFullToPlayer(
                String playerUuid,
                Set<String> playerPermanent,
                Map<String, Long> playerTemporary,
                Set<String> serverPermanent,
                Map<String, Long> serverTemporary) {
        }

        @Override
        public void syncPlayerDelta(
                String playerUuid,
                String action,
                @Nullable String stage,
                long remainingTicks) {
        }

        @Override
        public void syncServerDelta(
                String action,
                @Nullable String stage,
                long remainingTicks,
                Set<String> fullServerPermanent,
                Map<String, Long> fullServerTemporary) {
        }

        @Override
        public void notifyStageChange(
                String scope,
                @Nullable String playerUuid,
                String stage,
                boolean granted,
                @Nullable StageDefinition definition,
                @Nullable StageNotifyOptions options) {
        }

        @Override
        public @Nullable String resolveOnlinePlayerName(String playerUuid) {
            return null;
        }
    };

    /** Sends a full snapshot of both player-scoped and server-scoped stages to a single online player. */
    void syncFullToPlayer(
            String playerUuid,
            Set<String> playerPermanent,
            Map<String, Long> playerTemporary,
            Set<String> serverPermanent,
            Map<String, Long> serverTemporary);

    /**
     * Sends an incremental player-stage delta to an online player.
     *
     * @param playerUuid     canonical UUID string of the target player
     * @param action         delta action ({@code "add"}, {@code "remove"}, {@code "clear"}, or {@code "replace"})
     * @param stage          affected stage name, or {@code null}/{@code ""} for bulk actions
     * @param remainingTicks remaining ticks ({@code > 0}) when temporary, or {@code 0} when permanent/removed
     */
    void syncPlayerDelta(String playerUuid, String action, @Nullable String stage, long remainingTicks);

    /**
     * Broadcasts a server-stage delta (along with the full server snapshot for {@code "replace"})
     * to all online players.
     *
     * @param action              delta action ({@code "add"}, {@code "remove"}, {@code "clear"}, or {@code "replace"})
     * @param stage               affected stage name, or {@code null}/{@code ""} for bulk actions
     * @param remainingTicks      remaining ticks ({@code > 0}) when temporary, or {@code 0} when permanent/removed
     * @param fullServerPermanent immutable snapshot of current permanent server stages
     * @param fullServerTemporary immutable snapshot of current temporary server stages
     */
    void syncServerDelta(
            String action,
            @Nullable String stage,
            long remainingTicks,
            Set<String> fullServerPermanent,
            Map<String, Long> fullServerTemporary);

    default void syncReplacement(
            @Nullable String playerUuid,
            Set<String> permanent,
            Map<String, Long> temporary,
            List<ReplacementChange> changes) {
        if (playerUuid == null) {
            syncServerDelta("replace", "", 0L, permanent, temporary);
        } else {
            syncPlayerDelta(playerUuid, "replace", "", 0L);
        }
    }

    default void resyncFullToPlayer(
            String playerUuid,
            Set<String> playerPermanent,
            Map<String, Long> playerTemporary,
            Set<String> serverPermanent,
            Map<String, Long> serverTemporary) {
        syncFullToPlayer(playerUuid, playerPermanent, playerTemporary, serverPermanent, serverTemporary);
    }

    record ReplacementChange(String stage, boolean temporary, long remainingTicks) {
        public boolean removed() {
            return remainingTicks == 0L;
        }
    }

    /**
     * Displays configured title, chat, or action-bar notifications when a stage is granted or revoked.
     *
     * @param scope      {@code "player"} or {@code "server"}
     * @param playerUuid target player UUID when {@code scope} is {@code "player"}, otherwise {@code null}
     * @param stage      stage identifier
     * @param granted    {@code true} when granted, {@code false} when revoked or expired
     * @param definition optional registered {@link StageDefinition} metadata
     * @param options    optional per-call notification overrides
     */
    void notifyStageChange(
            String scope,
            @Nullable String playerUuid,
            String stage,
            boolean granted,
            @Nullable StageDefinition definition,
            @Nullable StageNotifyOptions options);

    /** Resolves the current game profile name of an online player, or {@code null} if offline. */
    @Nullable
    String resolveOnlinePlayerName(String playerUuid);
}
