package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.spec.inject.PlayerSpec;
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
        return neko$self().hasPermissions(2);
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

    // —— 不进 spec 的方法（碰撞或平台独有）——

    /** 给玩家发送消息。message 接受 String 或平台原生 Component。 */
    default void neko$sendMessage(Object message) {
        neko$self().sendSystemMessage(toComponent(message));
    }

    /** 返回挂载到该 player 的内存数据容器；首次访问时 lazy 创建并触发 {@code attachPlayerData}。 */
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
