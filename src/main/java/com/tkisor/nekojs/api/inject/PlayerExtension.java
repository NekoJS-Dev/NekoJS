package com.tkisor.nekojs.api.inject;

import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * @author ZZZank
 */
public interface PlayerExtension {
    private Player neko$self() {
        return (Player) this;
    }

    default boolean neko$isOp() {
        return Commands.LEVEL_GAMEMASTERS.check(neko$self().permissions());
    }

    default void give(ItemStack item) {
        if (!neko$self().getInventory().add(item)) {
            neko$self().drop(item, false);
        }
    }
}
