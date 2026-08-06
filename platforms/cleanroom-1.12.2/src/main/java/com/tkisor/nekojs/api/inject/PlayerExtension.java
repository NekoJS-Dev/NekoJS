package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.PlayerSpec;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * 1.12.2 {@link EntityPlayer} 统一扩展方法，注入到 MC 的 {@link EntityPlayer} 基类。
 *
 * <p>注意：mixin 注入到 {@link EntityPlayer}（基类），但服务端专用操作（gamemode / kick / op）
 * 需要 {@link EntityPlayerMP}。方法内部用 {@code instanceof} 检查并在非服务端玩家时安全降级。
 */
@RemapByPrefix("neko$")
public interface PlayerExtension extends PlayerSpec {

    private EntityPlayer self() {
        return (EntityPlayer) this;
    }

    @Override
    default boolean neko$isOp() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null
            && self() instanceof EntityPlayerMP mp
            && server.getPlayerList().canSendCommands(mp.getGameProfile());
    }

    @Override
    default void neko$give(Object stack) {
        if (!(stack instanceof ItemStack itemStack) || itemStack.isEmpty()) return;
        EntityPlayer player = self();
        if (!player.inventory.addItemStackToInventory(itemStack)) {
            // 背包满：在玩家位置掉落剩余
            player.inventory.placeItemBackInInventory(player.getEntityWorld(), itemStack);
        }
    }

    @Override
    default void neko$setGamemode(String name) {
        if (self() instanceof EntityPlayerMP mp) {
            mp.setGameType(GameType.parseGameTypeWithDefault(name, GameType.SURVIVAL));
        }
    }

    @Override
    default String neko$getGamemode() {
        if (self() instanceof EntityPlayerMP mp) {
            return mp.interactionManager.getGameType().getName();
        }
        return GameType.SURVIVAL.getName();
    }

    @Override
    default void neko$addXpLevels(int levels) {
        self().addExperienceLevel(levels);
    }

    @Override
    default void neko$addXpPoints(int points) {
        self().addExperience(points);
    }

    @Override
    default int neko$getXpLevel() {
        return self().experienceLevel;
    }

    @Override
    default void neko$setXpLevel(int level) {
        self().experienceLevel = level;
    }

    @Override
    default void neko$kick(Object reason) {
        if (self() instanceof EntityPlayerMP mp) {
            ITextComponent component = reason instanceof ITextComponent itc
                ? itc
                : new TextComponentString(reason == null ? "Disconnected" : reason.toString());
            mp.connection.disconnect(component);
        }
    }

    // —— 不进 spec 的方法（碰撞）——
    // neko$isCreative — 与原生零参碰撞，行为一致，JS 直接用原生
    // neko$sendMessage — CR 原生有 sendMessage(ITextComponent) 零参碰撞，不注入
}
