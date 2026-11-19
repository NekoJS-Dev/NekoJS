package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BaseFireBlock.class)
public abstract class MixinPortalShape {

    @ModifyExpressionValue(method = "onPlace(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalShape;"
                    + "findEmptyPortalShape(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction$Axis;)Ljava/util/Optional;"), require = 1, allow = 1)
    private Optional<PortalShape> nekojs$onPortalSpawn(Optional<PortalShape> shape,
                                                     BlockState fireState, Level level, BlockPos pos,
                                                     BlockState previousState, boolean moved) {
        if (shape.isPresent() && FabricBlockEventBindingsV2.postStablePortalSpawn(level, pos, shape.get())) {
            return Optional.empty();
        }
        return shape;
    }
}
