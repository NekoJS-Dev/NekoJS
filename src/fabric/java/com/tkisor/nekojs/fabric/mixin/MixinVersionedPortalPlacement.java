package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PortalShape.class)
public abstract class MixinVersionedPortalPlacement {
    @Shadow private Direction.Axis axis;
    @Shadow private BlockPos bottomLeft;

    @Inject(method = "createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    private void nekojs$beforePortalPlacement(LevelAccessor level, CallbackInfo ci) {
        if (!level.isClientSide() && FabricBlockEventBindingsV2.PORTAL_SPAWN.hasListeners()
                && FabricBlockEventBindingsV2.postPortalSpawn(level, bottomLeft,
                        Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis),
                        (PortalShape) (Object) this)) {
            ci.cancel();
        }
    }
}
