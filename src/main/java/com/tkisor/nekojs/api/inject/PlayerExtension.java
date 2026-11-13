package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.spec.inject.PlayerSpec;
//? if >=26 {
import net.minecraft.commands.Commands;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

@RemapByPrefix("neko$")
public interface PlayerExtension extends PlayerSpec {

    private Player neko$self() {
        return (Player) this;
    }

    @Override
    default boolean neko$isOp() {
//? if >=26 {
        return Commands.LEVEL_GAMEMASTERS.check(neko$self().permissions());
//?} else {
        /*return neko$self().hasPermissions(2);
*///?}
    }

    @Override
    default void neko$give(Object stack) {
        if (stack instanceof ItemStack itemStack) {
            neko$self().getInventory().placeItemBackInInventory(itemStack);
        }
    }

    @Override
    default void neko$setGamemode(String gamemode) {
        if (!(neko$self() instanceof ServerPlayer serverPlayer)) return;
        GameType type = parseGameType(gamemode);
        if (type != null) {
            serverPlayer.setGameMode(type);
        }
    }

    @Override
    default String neko$getGamemode() {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.getGameModeForPlayer().getName();
        }
        return null;
    }

    @Override
    default void neko$addXpLevels(int levels) {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            serverPlayer.giveExperienceLevels(levels);
        }
    }

    @Override
    default void neko$addXpPoints(int points) {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            serverPlayer.giveExperiencePoints(points);
        }
    }

    @Override
    default int neko$getXpLevel() {
        return neko$self().experienceLevel;
    }

    @Override
    default void neko$setXpLevel(int level) {
        neko$self().experienceLevel = level;
    }

    @Override
    default void neko$kick(Object reason) {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.disconnect(toComponent(reason != null ? reason : "Kicked"));
        }
    }

    // ---- Methods left out of the spec (name collision with vanilla, or platform specific) ----

    // isCreative() collides with the vanilla no-arg method and behaves the same, so scripts call the
    // vanilla one instead of an injected version.

    /** Sends a message to the player. message accepts a String or a platform-native Component. */
    default void neko$sendMessage(Object message) {
        neko$self().sendSystemMessage(toComponent(message));
    }

    /**
     * Returns the in-memory data container of this player. The first access creates it lazily and
     * fires {@code attachPlayerData}.
     */
    AttachedData<Player> neko$data();

    private static Component toComponent(Object o) {
        if (o instanceof Component component) {
            return component;
        }
        return Component.literal(String.valueOf(o));
    }

    private static GameType parseGameType(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase()) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> null;
        };
    }
}
