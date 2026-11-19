package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.bindings.event.StableBlockPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class NeoForgeEndermanPlacementMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/event/EventHooks;onBlockPlace"
                    + "(Lnet/minecraft/world/entity/Entity;Lnet/neoforged/neoforge/common/util/BlockSnapshot;Lnet/minecraft/core/Direction;)Z"),
            require = 1, allow = 1)
    private boolean nekojs$dispatchEndermanPlacement(Entity actor, BlockSnapshot snapshot, Direction face,
                                                     Operation<Boolean> original, @Local(index = 2) Level level,
                                                     @Local(index = 6) BlockPos pos, @Local(index = 10) BlockState state) {
        return original.call(actor, snapshot, face) || (BlockEvents.hasStablePlacementListeners()
                && BlockEvents.postStablePlacement(new StableBlockPlacement.Attempt(level, pos, null, state, face, actor)));
    }
}
