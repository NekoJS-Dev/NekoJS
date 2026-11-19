package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import com.tkisor.nekojs.core.stage.StagePlatformHooks;
import com.tkisor.nekojs.core.stage.StageStore;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import com.tkisor.nekojs.core.stage.gate.StageWireProtocol;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >=26 {
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.world.item.BlockItem;
//?} else {
/*import com.tkisor.nekojs.api.inject.MutableComponentExtension;
import net.minecraft.world.item.ItemStack;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Loader-neutral {@link StagePlatformHooks} implementation bound to a running {@link MinecraftServer}.
 *
 * <p>Delivers full and incremental stage sync payloads over {@link ClientDataSyncPacket} using
 * {@link StageWireProtocol#CHANNEL_KEY}, formats title/chat/action-bar stage notifications, and
 * resolves live online player names from the server player list.
 */
public final class MinecraftStagePlatformHooks implements StagePlatformHooks {

    private static final NekoId DEFAULT_GRANT_SOUND =
            NekoId.of("minecraft", "entity.player.levelup");

    private final MinecraftServer server;
    private final Supplier<CompiledStageGateSnapshot> snapshotSupplier;
    private final Supplier<StageStore> storeSupplier;

    /**
     * Creates platform stage hooks bound to {@code server} and {@code snapshotSupplier}, resolving
     * player snapshots from {@link StageDomainOwner#activeStore()}.
     */
    public MinecraftStagePlatformHooks(
            MinecraftServer server,
            Supplier<CompiledStageGateSnapshot> snapshotSupplier
    ) {
        this(server, snapshotSupplier, StageDomainOwner::activeStore);
    }

    /**
     * Creates platform stage hooks bound to {@code server}, {@code snapshotSupplier}, and {@code storeSupplier}.
     */
    public MinecraftStagePlatformHooks(
            MinecraftServer server,
            Supplier<CompiledStageGateSnapshot> snapshotSupplier,
            Supplier<StageStore> storeSupplier
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.storeSupplier = Objects.requireNonNull(storeSupplier, "storeSupplier");
    }

    @Override
    public boolean hasSyncRecipient(@Nullable String playerUuid) {
        return playerUuid == null
                ? server.getPlayerList() != null && !server.getPlayerList().getPlayers().isEmpty()
                : findOnlinePlayer(playerUuid) != null;
    }

    @Override
    public void syncFullToPlayer(
            String playerUuid,
            Set<String> playerPermanent,
            Map<String, Long> playerTemporary,
            Set<String> serverPermanent,
            Map<String, Long> serverTemporary
    ) {
        syncFullToPlayer(playerUuid, playerPermanent, playerTemporary, serverPermanent, serverTemporary, false);
    }

    @Override
    public void resyncFullToPlayer(
            String playerUuid,
            Set<String> playerPermanent,
            Map<String, Long> playerTemporary,
            Set<String> serverPermanent,
            Map<String, Long> serverTemporary
    ) {
        syncFullToPlayer(playerUuid, playerPermanent, playerTemporary, serverPermanent, serverTemporary, true);
    }

    private void syncFullToPlayer(
            String playerUuid,
            Set<String> playerPermanent,
            Map<String, Long> playerTemporary,
            Set<String> serverPermanent,
            Map<String, Long> serverTemporary,
            boolean stateOnly
    ) {
        ServerPlayer player = findOnlinePlayer(playerUuid);
        if (player == null) {
            return;
        }
        String json = StageWireProtocol.encodeFullSync(
                playerPermanent,
                playerTemporary,
                serverPermanent,
                serverTemporary,
                snapshotSupplier.get(),
                stateOnly
        );
        PlayPacketDispatchers.get().sendToPlayer(
                player,
                new ClientDataSyncPacket(StageWireProtocol.CHANNEL_KEY, json)
        );
    }

    @Override
    public void syncPlayerDelta(
            String playerUuid,
            String action,
            @Nullable String stage,
            long remainingTicks
    ) {
        ServerPlayer player = findOnlinePlayer(playerUuid);
        if (player == null || action == null) {
            return;
        }
        Set<String> fullPermanent = null;
        Map<String, Long> fullTemporary = null;
        if ("replace".equalsIgnoreCase(action.trim())) {
            StageStore store = storeSupplier.get();
            if (store != null) {
                fullPermanent = store.playerPermanentSnapshot(playerUuid);
                fullTemporary = store.playerTemporarySnapshot(playerUuid);
            } else {
                fullPermanent = Set.of();
                fullTemporary = Map.of();
            }
        }
        String json = StageWireProtocol.encodeDelta(
                "player",
                action,
                stage,
                remainingTicks,
                fullPermanent,
                fullTemporary
        );
        PlayPacketDispatchers.get().sendToPlayer(
                player,
                new ClientDataSyncPacket(StageWireProtocol.CHANNEL_KEY, json)
        );
    }

    @Override
    public void syncServerDelta(
            String action,
            @Nullable String stage,
            long remainingTicks,
            Set<String> fullServerPermanent,
            Map<String, Long> fullServerTemporary
    ) {
        if (action == null || server.getPlayerList() == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        String json = StageWireProtocol.encodeDelta(
                "server",
                action,
                stage,
                remainingTicks,
                fullServerPermanent,
                fullServerTemporary
        );
        PlayPacketDispatchers.get().sendToAllPlayers(
                new ClientDataSyncPacket(StageWireProtocol.CHANNEL_KEY, json)
        );
    }

    @Override
    public void syncReplacement(
            @Nullable String playerUuid,
            Set<String> permanent,
            Map<String, Long> temporary,
            List<ReplacementChange> changes
    ) {
        if (playerUuid == null) {
            if (!hasSyncRecipient(null)) {
                return;
            }
            String json = StageWireProtocol.encodeReplacement("server", permanent, temporary, changes);
            PlayPacketDispatchers.get().sendToAllPlayers(new ClientDataSyncPacket(StageWireProtocol.CHANNEL_KEY, json));
        } else {
            ServerPlayer player = findOnlinePlayer(playerUuid);
            if (player == null) {
                return;
            }
            String json = StageWireProtocol.encodeReplacement("player", permanent, temporary, changes);
            PlayPacketDispatchers.get().sendToPlayer(player, new ClientDataSyncPacket(StageWireProtocol.CHANNEL_KEY, json));
        }
    }

    @Override
    public void notifyStageChange(
            String scope,
            @Nullable String playerUuid,
            String stage,
            boolean granted,
            @Nullable StageDefinition definition,
            @Nullable StageNotifyOptions options
    ) {
        StageNotifyOptions effectiveOptions = options != null ? options : StageNotifyOptions.DEFAULT;
        if (Boolean.TRUE.equals(effectiveOptions.silent())) {
            return;
        }

        boolean showTitle = effectiveOptions.showTitle(definition);
        boolean showChat = effectiveOptions.showChat(definition);
        boolean showActionBar = effectiveOptions.showActionBar(definition);
        if (!showTitle && !showChat && !showActionBar) {
            return;
        }

        List<ServerPlayer> recipients = resolveRecipients(scope, playerUuid);
        if (recipients.isEmpty()) {
            return;
        }

        String stageLabel = (definition != null && !definition.displayName().isBlank())
                ? definition.displayName()
                : stage;
        String legacyTemplate = resolveMessageTemplate(granted, definition);
        String titleTemplate = definition != null && !granted
                ? templateOrFallback(definition.revokeTitle(), legacyTemplate)
                : legacyTemplate;
        String subtitleTemplate = definition == null ? "" : granted
                ? templateOrFallback(definition.grantSubtitle(), templateOrFallback(definition.description(), ""))
                : definition.revokeSubtitle();
        String chatTemplate = definition == null ? legacyTemplate
                : templateOrFallback(granted ? definition.grantChat() : definition.revokeChat(), legacyTemplate);
        String actionBarTemplate = definition == null ? legacyTemplate
                : templateOrFallback(granted ? definition.grantActionBar() : definition.revokeActionBar(), legacyTemplate);
        NekoId sound = definition == null ? null : definition.sound();
        if (sound == null && granted && showTitle) {
            sound = DEFAULT_GRANT_SOUND;
        }
        Identifier iconId = MinecraftStageRegistryAccess.toMcIdOrNull(definition == null ? null : definition.icon());
        ChatFormatting accent = granted ? ChatFormatting.GREEN : ChatFormatting.GOLD;

        for (ServerPlayer recipient : recipients) {
            String playerName = recipient.getScoreboardName();
            Component titleComponent = showTitle && recipient.connection != null
                    ? withIcon(message(titleTemplate, stageLabel, playerName, accent), iconId) : null;
            Component subtitleComponent = titleComponent == null ? null
                    : subtitleTemplate.isBlank() ? Component.empty()
                    : message(subtitleTemplate, stageLabel, playerName, ChatFormatting.GRAY);
            Component actionBarComponent = showActionBar
                    ? withIcon(message(actionBarTemplate, stageLabel, playerName, accent), iconId) : null;
            Component chatComponent = showChat
                    ? withIcon(message(chatTemplate, stageLabel, playerName, accent), iconId) : null;
//? if <26 {
            /*if (iconId != null && LegacyStageNotification.trySend(
                    recipient, iconId, titleComponent, subtitleComponent,
                    actionBarComponent, chatComponent)) {
                playNotifySound(recipient, sound);
                continue;
            }
*///?}
            if (titleComponent != null) {
                recipient.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
                recipient.connection.send(new ClientboundSetTitleTextPacket(titleComponent));
                recipient.connection.send(new ClientboundSetSubtitleTextPacket(subtitleComponent));
            }

            if (actionBarComponent != null) {
                sendActionBar(recipient, actionBarComponent);
            }

            if (chatComponent != null) {
                recipient.sendSystemMessage(chatComponent);
            }
            playNotifySound(recipient, sound);
        }
    }

    @Override
    public @Nullable String resolveOnlinePlayerName(String playerUuid) {
        ServerPlayer player = findOnlinePlayer(playerUuid);
        return player != null ? player.getScoreboardName() : null;
    }

    public static void sendVehicleCorrection(ServerPlayer driver, Entity vehicle) {
//? if >=26 {
        driver.connection.send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
//?} else {
        /*driver.connection.send(new ClientboundMoveVehiclePacket(vehicle));
*///?}
    }

    /** Sends an action-bar overlay message to {@code player} across 1.21.1 and 26.x. */
    public static void sendActionBar(ServerPlayer player, Component message) {
        if (player == null || message == null) {
            return;
        }
//? if >=26 {
        player.sendSystemMessage(message, true);
//?} else {
        /*player.displayClientMessage(message, true);
*///?}
    }

    /** Plays a notification sound directly to {@code player} if {@code soundId} exists in {@link BuiltInRegistries#SOUND_EVENT}. */
    public static void playNotifySound(ServerPlayer player, @Nullable NekoId soundId) {
        if (player == null || player.connection == null || soundId == null) {
            return;
        }
        Identifier mcSoundId = MinecraftStageRegistryAccess.toMcIdOrNull(soundId);
        if (mcSoundId == null) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(mcSoundId).orElse(null);
        if (sound == null) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.PLAYERS,
                player.getX(),
                player.getY(),
                player.getZ(),
                1.0F,
                1.0F,
                player.getRandom().nextLong()
        ));
    }

    private @Nullable ServerPlayer findOnlinePlayer(@Nullable String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank() || server.getPlayerList() == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(playerUuid.trim());
            return server.getPlayerList().getPlayer(uuid);
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }

    private List<ServerPlayer> resolveRecipients(@Nullable String scope, @Nullable String playerUuid) {
        if (server.getPlayerList() == null) {
            return List.of();
        }
        if ("server".equalsIgnoreCase(scope)) {
            return List.copyOf(server.getPlayerList().getPlayers());
        }
        ServerPlayer single = findOnlinePlayer(playerUuid);
        return single != null ? List.of(single) : List.of();
    }

    private static String resolveMessageTemplate(boolean granted, @Nullable StageDefinition definition) {
        if (granted) {
            if (definition != null && !definition.grantTitle().isBlank()) {
                return definition.grantTitle();
            }
            return "Stage unlocked: {stage}";
        }
        if (definition != null && !definition.revokeMessage().isBlank()) {
            return definition.revokeMessage();
        }
        return "Stage removed: {stage}";
    }

    private static String templateOrFallback(String template, String fallback) {
        return template.isBlank() ? fallback : template;
    }

    private static Component message(String template, String stageLabel, String playerName, ChatFormatting color) {
        return Component.literal(formatTemplate(template, stageLabel, playerName)).withStyle(color);
    }

    private static Component withIcon(Component message, @Nullable Identifier iconId) {
        if (iconId == null) {
            return message;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(iconId).orElse(null);
//? if >=26 {
        String path = iconId.getPath();
        Identifier atlas;
        Identifier sprite;
        if (item != null) {
            boolean blockItem = item instanceof BlockItem;
            atlas = blockItem ? AtlasIds.BLOCKS : AtlasIds.ITEMS;
            sprite = iconId.withPrefix(blockItem ? "block/" : "item/");
        } else if (path.startsWith("item/") || path.startsWith("block/")) {
            atlas = path.startsWith("item/") ? AtlasIds.ITEMS : AtlasIds.BLOCKS;
            sprite = iconId;
        } else {
            atlas = AtlasIds.GUI;
            sprite = path.startsWith("textures/gui/sprites/") && path.endsWith(".png")
                    ? iconId.withPath(path.substring("textures/gui/sprites/".length(), path.length() - 4))
                    : iconId;
        }
        return Component.object(new AtlasSprite(atlas, sprite), Component.empty()).append(" ").append(message);
//?} else {
        /*if (item != null && message instanceof MutableComponentExtension extension) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty()) {
                return extension.neko$hoverItem(stack);
            }
        }
        return message;
*///?}
    }

    private static String formatTemplate(String template, String stageLabel, String playerName) {
        return template
                .replace("{stage}", stageLabel != null ? stageLabel : "")
                .replace("{player}", playerName != null ? playerName : "");
    }
}
