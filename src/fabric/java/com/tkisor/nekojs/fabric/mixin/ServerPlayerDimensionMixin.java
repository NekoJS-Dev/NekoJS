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
 * Fabric hook for the player dimension change event ({@code PlayerEvents.changedDimension}).
 *
 * <p><b>26.1.2 changed the method name: {@code ServerPlayer#changeDimension(ServerLevel)} /
 * ({@code ResourceKey}) no longer exists.</b> Dimension changes now all happen inside
 * {@code ServerPlayer#teleport(TeleportTransition)}, and NeoForge 26.1.2 fires
 * {@code PlayerChangedDimensionEvent} at the end of that method's success path (checked against
 * the userdev patch: {...teleportSpectators...; firePlayerChangedDimensionEvent(this,
 * lastDimension, transition.newLevel().dimension()); return this;}). Fabric hooks the same point.
 *
 * <p>Injection point (checked with javap -c on minecraft-merged-deobf-26.1.2):
 * <ul>
 *   <li>{@code teleport(TeleportTransition)ServerPlayer} is the real method; a covariant bridge
 *       {@code Entity teleport(...)} with the same signature exists alongside it, so the selector
 *       must include the return type.</li>
 *   <li>The success path is {@code return this}; the early exit is
 *       {@code if (isRemoved()) return null}. So TAIL plus
 *       {@code cir.getReturnValue() != null} is the success test.</li>
 * </ul>
 * HEAD captures {@code this.level().dimension()} (the player is still in the old dimension when the
 * method starts; nothing has moved at offset 0); TAIL reads {@code this.level().dimension()} as the
 * new dimension. Like NeoForge, no from==to filtering is done
 * (teleport(TeleportTransition) is only called from dimension teleport and portal paths, and
 * NeoForge does not filter either). defaultRequire=1 fails hard if the method is refactored.
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
