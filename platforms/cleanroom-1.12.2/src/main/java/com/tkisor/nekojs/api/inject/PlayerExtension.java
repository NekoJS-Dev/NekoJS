package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
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
 *
 * <p>1.12.2 与 1.21.1 的关键差异：
 * <ul>
 *   <li>{@code Player} → {@link EntityPlayer}（服务端 {@link EntityPlayerMP}）</li>
 *   <li>OP 检查：{@code MinecraftServer.getPlayerList().canSendCommands(GameProfile)}</li>
 *   <li>gamemode：{@link GameType} 枚举，{@link EntityPlayerMP#setGameType(GameType)} / {@code interactionManager.getGameType()}</li>
 *   <li>消息：{@link EntityPlayerMP#sendMessage(ITextComponent)}（1.12.2 用 {@link ITextComponent}，非 {@code Component}）</li>
 *   <li>XP：{@link EntityPlayer#experienceLevel} 字段，{@link EntityPlayer#addExperience(int)}</li>
 *   <li>踢出：{@code EntityPlayerMP.connection.disconnect(ITextComponent)}</li>
 * </ul>
 *
 * @see EntityPlayer
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface PlayerExtension {

    private EntityPlayer self() {
        return (EntityPlayer) this;
    }

    /**
     * 该玩家是否为 OP。对齐 1.21.1 {@code isOp()}。
     * 1.12.2 用 {@code PlayerList.canSendCommands(GameProfile)}（perms level ≥ 2 即 OP）。
     *
     * @return {@code true} 若该玩家是服务端 OP
     */
    default boolean neko$isOp() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null
            && self() instanceof EntityPlayerMP mp
            && server.getPlayerList().canSendCommands(mp.getGameProfile());
    }

    /**
     * 给玩家发放物品（放入背包，背包满则掉落）。对齐 1.21.1 {@code give(ItemStack)}。
     * 1.12.2 用 {@code InventoryPlayer.addItemStackToInventory(ItemStack)}，返回 false 表示背包已满；
     * 此时用 {@code placeItemBackInInventory} 兜底（会在世界中掉落）。
     *
     * @param stack 要发放的物品栈
     */
    default void neko$give(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        EntityPlayer player = self();
        if (!player.inventory.addItemStackToInventory(stack)) {
            // 背包满：在玩家位置掉落剩余
            player.inventory.placeItemBackInInventory(player.getEntityWorld(), stack);
        }
    }

    /**
     * 给玩家发送消息。对齐 1.21.1 {@code sendMessage(Object)}（Object → Component 转换）。
     * 1.12.2 接受 {@link ITextComponent}；String 自动包装为 {@link TextComponentString}。
     *
     * @param message 消息（String 或 ITextComponent）
     */
    default void neko$sendMessage(Object message) {
        ITextComponent component;
        if (message instanceof ITextComponent itc) {
            component = itc;
        } else {
            component = new TextComponentString(message == null ? "" : message.toString());
        }
        self().sendMessage(component);
    }

    /**
     * 设置游戏模式。对齐 1.21.1 {@code setGamemode(String)}。
     * 仅对 {@link EntityPlayerMP} 生效（客户端/单机非 host 玩家无法切换）。
     * 名字无效时回退 SURVIVAL。
     *
     * @param name gamemode 名（survival / creative / adventure / spectator）
     */
    default void neko$setGamemode(String name) {
        if (self() instanceof EntityPlayerMP mp) {
            GameType type = GameType.parseGameTypeWithDefault(name, GameType.SURVIVAL);
            mp.setGameType(type);
        }
    }

    /**
     * 取当前游戏模式名。对齐 1.21.1 {@code getGamemode()}。
     * 仅 {@link EntityPlayerMP} 有 interactionManager；非服务端玩家返回 {@code "survival"}。
     *
     * @return gamemode 名
     */
    default String neko$getGamemode() {
        if (self() instanceof EntityPlayerMP mp) {
            return mp.interactionManager.getGameType().getName();
        }
        return GameType.SURVIVAL.getName();
    }

    /**
     * 增加玩家经验值（点数，非等级）。对齐 1.21.1 {@code addXp(int)}。
     * 1.12.2 用 {@link EntityPlayer#addExperience(int)}。
     *
     * @param amount 经验点数
     */
    default void neko$addXp(int amount) {
        self().addExperience(amount);
    }

    /**
     * 取玩家经验等级。对齐 1.21.1 {@code getXpLevel()}。
     * 1.12.2 读 {@link EntityPlayer#experienceLevel} 字段。
     *
     * @return 经验等级
     */
    default int neko$getXpLevel() {
        return self().experienceLevel;
    }

    /**
     * 设置玩家经验等级。对齐 1.21.1 {@code setXpLevel(int)}。
     * 1.12.2 写 {@link EntityPlayer#experienceLevel} 字段（会同步给客户端）。
     *
     * @param level 经验等级
     */
    default void neko$setXpLevel(int level) {
        self().experienceLevel = level;
    }

    /**
     * 踢出玩家。对齐 1.21.1 {@code kick(Object)}。
     * 仅对 {@link EntityPlayerMP} 生效。message 自动包装为 {@link TextComponentString}。
     *
     * @param message 踢出原因（String 或 ITextComponent）
     */
    default void neko$kick(Object message) {
        if (self() instanceof EntityPlayerMP mp) {
            ITextComponent component = message instanceof ITextComponent itc
                ? itc
                : new TextComponentString(message == null ? "Disconnected" : message.toString());
            mp.connection.disconnect(component);
        }
    }

    /**
     * 玩家是否处于创造模式。对齐 1.21.1 {@code isCreative()}。
     * 1.12.2 用 {@link EntityPlayer#isCreative()}（基类已实现，看 capabilities.isCreativeMode）。
     *
     * @return {@code true} 若为创造模式
     */
    default boolean neko$isCreative() {
        return self().isCreative();
    }
}
