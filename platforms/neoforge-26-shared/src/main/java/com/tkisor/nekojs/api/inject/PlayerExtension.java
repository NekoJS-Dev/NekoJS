package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface PlayerExtension {
    private Player neko$self() {
        return (Player) this;
    }

    default boolean neko$isOp() {
        return Commands.LEVEL_GAMEMASTERS.check(neko$self().permissions());
    }

    default void neko$give(ItemStack stack) {
        neko$self().getInventory().placeItemBackInInventory(stack);
    }

    default void neko$sendMessage(Object message) {
        neko$self().sendSystemMessage(toComponent(message));
    }

    default void neko$setGamemode(String gamemode) {
        if (!(neko$self() instanceof ServerPlayer serverPlayer)) return;
        GameType type = parseGameType(gamemode);
        if (type != null) {
            serverPlayer.setGameMode(type);
        }
    }

    default String neko$getGamemode() {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.getGameModeForPlayer().getName();
        }
        return null;
    }

    default void neko$addXp(int levels) {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            serverPlayer.giveExperienceLevels(levels);
        }
    }

    default int neko$getXpLevel() {
        return neko$self().experienceLevel;
    }

    default void neko$setXpLevel(int level) {
        neko$self().experienceLevel = level;
    }

    default void neko$kick(Object reason) {
        if (neko$self() instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.disconnect(toComponent(reason != null ? reason : "Kicked"));
        }
    }

    default boolean neko$isCreative() {
        return neko$self().isCreative();
    }

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

    /** 返回挂载到该 player 的内存数据容器；首次访问时 lazy 创建并触发 {@code attachPlayerData}。 */
    AttachedData<Player> neko$data();
}
