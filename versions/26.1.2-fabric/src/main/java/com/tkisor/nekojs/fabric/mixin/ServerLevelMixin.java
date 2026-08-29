package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「实体加入世界」钩子（fabric-api 无等价事件：ENTITY_LOAD 仅覆盖从存储装载，
 * summon/生成的实体不经过它）。对齐 NeoForge EntityJoinLevelEvent 语义：ServerLevel
 * 接受实体加入时（addEntity 返回 true）post 中立 payload 到 joinLevel 总线。
 *
 * <p>本类经 nekojs-fabric.mixins.json 应用（fabric.mod.json {@code mixins} 键声明）。
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
