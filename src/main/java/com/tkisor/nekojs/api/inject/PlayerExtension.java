package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
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
}
