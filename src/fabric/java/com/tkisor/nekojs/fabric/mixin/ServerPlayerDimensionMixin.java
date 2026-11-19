package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.stage.StageDomainOwner;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for dimension entry stage gate enforcement and the player dimension change event
 * ({@code PlayerEvents.changedDimension}) on {@code ServerPlayer#teleport(TeleportTransition)}.
 *
 * <p>HEAD checks {@link StageGateEnforcer#allowEnterDimension} before pushing {@code fromDimension}
 * onto the thread-local stack; if denied, {@code cir.setReturnValue(self)} cancels the transition
 * cleanly without unbalancing the stack. TAIL pops the previous dimension, refreshes stage
 * enforcement state, and dispatches {@code changedDimension} when the teleport succeeded.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionMixin {

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"),
            cancellable = true)
    private void nekojs$checkDimensionGate(TeleportTransition transition,
                                           CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (transition != null && transition.newLevel() != null
                && (self.level() == null || !transition.newLevel().dimension().equals(self.level().dimension()))) {
            var dimId = transition.newLevel().dimension().identifier();
            NekoId targetDim = NekoId.of(dimId.getNamespace(), dimId.getPath());
            if (!StageGateEnforcer.allowEnterDimension(self, targetDim)) {
                cir.setReturnValue(self);
            }
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("TAIL"))
    private void nekojs$onDimensionChanged(TeleportTransition transition,
                                           CallbackInfoReturnable<ServerPlayer> cir,
                                           @Local(index = 4) ResourceKey<Level> from) {
        ServerPlayer player = cir.getReturnValue();
        if (player != null) {
            if (!from.equals(player.level().dimension())) {
                StageDomainOwner owner = StageDomainOwner.current();
                if (owner != null) {
                    owner.onPlayerRespawn(player);
                }
            }
            FabricPlayerEventBindings.postChangedDimension(player, from, transition.newLevel().dimension());
        }
    }
}
