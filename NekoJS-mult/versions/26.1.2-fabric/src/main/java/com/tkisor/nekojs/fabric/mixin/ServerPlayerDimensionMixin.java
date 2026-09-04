package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家维度切换事件（{@code PlayerEvents.changedDimension}）的 fabric 钩子。
 *
 * <p><b>26.1.2 方法名修正：{@code ServerPlayer#changeDimension(ServerLevel)} /
 * ({@code ResourceKey}) 已不存在</b>——维度切换统一在
 * {@code ServerPlayer#teleport(TeleportTransition)} 内完成，NeoForge 26.1.2 的
 * {@code PlayerChangedDimensionEvent} 也改在该方法成功路径末尾触发（userdev 补丁实证：
 * {...teleportSpectators...; firePlayerChangedDimensionEvent(this, lastDimension,
 * transition.newLevel().dimension()); return this;}）。fabric 对齐同一点。
 *
 * <p>注入点（javap -c minecraft-merged-deobf-26.1.2 实证）：
 * <ul>
 *   <li>{@code teleport(TeleportTransition)ServerPlayer}（真实方法，注意同签名的
 *       {@code Entity teleport(...)} 协变桥方法并存，选择器必须带返回类型）。</li>
 *   <li>成功路径 {@code return this}，提前退出 {@code if (isRemoved()) return null}——
 *       TAIL + {@code cir.getReturnValue() != null} 即「成功」判定。</li>
 * </ul>
 * HEAD 捕获 {@code this.level().dimension()}（进入方法时玩家还在旧维度——偏移 0 处尚未移动）；
 * TAIL 取 {@code this.level().dimension()} 即新维度。与 NeoForge 一致不做 from==to 过滤
 * （teleport(TeleportTransition) 只在维度传送/传送门路径被调用；NeoForge 也不过滤）。
 * defaultRequire=1：方法被重构时硬失败。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionMixin {

    @Unique
    private ResourceKey<Level> neko$fromDimension;

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"))
    private void nekojs$captureFromDimension(TeleportTransition transition,
                                             CallbackInfoReturnable<ServerPlayer> cir) {
        neko$fromDimension = ((ServerPlayer) (Object) this).level().dimension();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("TAIL"))
    private void nekojs$onDimensionChanged(TeleportTransition transition,
                                           CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = cir.getReturnValue();
        if (player != null) {
            ResourceKey<Level> from = neko$fromDimension;
            neko$fromDimension = null;
            if (from != null) {
                FabricPlayerEventBindings.postChangedDimension(player, from, player.level().dimension());
            }
        }
    }
}
