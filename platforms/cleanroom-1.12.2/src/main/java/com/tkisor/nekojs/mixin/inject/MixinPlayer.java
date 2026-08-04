package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.PlayerExtension;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link EntityPlayer} 基类注入玩家扩展方法（isOp / give / sendMessage / gamemode / xp / kick）。
 * <p>注入基类 {@link EntityPlayer}，方法内部用 {@code instanceof EntityPlayerMP} 检查服务端专用操作。
 * <p>{@link EntityPlayer} 是 MC 类，使用默认 remap（{@code remap = true}）。
 *
 * @author ZZZank
 */
@Mixin(EntityPlayer.class)
public abstract class MixinPlayer implements PlayerExtension {
}
