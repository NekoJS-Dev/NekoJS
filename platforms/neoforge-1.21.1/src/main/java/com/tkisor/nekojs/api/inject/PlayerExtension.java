package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
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

    /**
     * 检查玩家是否拥有 OP (管理员) 权限
     */
    default boolean neko$isOp() {
        // 1.21.1 Mojmap: 使用 getPermissionLevel()
        // 2 级通常代表拥有修改世界、使用基础管理指令的权限 (Level 2 = Gamemaster)
        return neko$self().hasPermissions(2);
    }

    /**
     * 给玩家发放物品
     */
    default void neko$give(ItemStack stack) {
        // 1.21.1: 保持逻辑一致，将物品放入背包，如果背包满了则尝试处理
        neko$self().getInventory().placeItemBackInInventory(stack);
    }
}