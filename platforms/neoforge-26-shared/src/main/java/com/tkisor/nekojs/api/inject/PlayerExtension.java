package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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

    /** 返回挂载到该 player 的内存数据容器；首次访问时 lazy 创建并触发 {@code attachPlayerData}。 */
    AttachedData<Player> neko$data();
}
