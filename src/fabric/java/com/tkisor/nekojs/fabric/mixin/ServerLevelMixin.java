package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity-joins-world hook. fabric-api has no equivalent event: ENTITY_LOAD only covers entities
 * loaded from storage, and summoned/spawned entities never pass through it. The semantics match
 * NeoForge's EntityJoinLevelEvent: HEAD posts a cancellable joinLevel payload (cancelling makes
 * {@code addEntity} return {@code false}), and TAIL runs post-join goal registration when
 * {@code addEntity} returns {@code true}.
 *
 * <p>This class is applied via nekojs-fabric.mixins.json (declared by the {@code mixins} key in
 * fabric.mod.json).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void nekojs$onPlayerJoinLevel(ServerPlayer player, CallbackInfo ci) {
        if (FabricEntityEventBindings.postJoinLevelPre(player, (ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "addEntity", at = @At("TAIL"), require = 1, allow = 1)
    private void nekojs$afterEntityJoin(Entity entity, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) {
            FabricEntityEventBindings.postVersionedJoinLevel(entity, (ServerLevel) (Object) this);
        }
    }
}
