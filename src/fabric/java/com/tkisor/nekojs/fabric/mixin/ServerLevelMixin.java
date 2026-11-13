package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity-joins-world hook. fabric-api has no equivalent event: ENTITY_LOAD only covers entities
 * loaded from storage, and summoned/spawned entities never pass through it. The semantics match
 * NeoForge's EntityJoinLevelEvent: when ServerLevel accepts an entity (addEntity returns
 * {@code true}), a neutral payload is posted to the joinLevel bus.
 *
 * <p>This class is applied via nekojs-fabric.mixins.json (declared by the {@code mixins} key in
 * fabric.mod.json).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void nekojs$onEntityJoin(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            FabricEntityEventBindings.postJoinLevel(entity, (ServerLevel) (Object) this);
        }
    }
}
