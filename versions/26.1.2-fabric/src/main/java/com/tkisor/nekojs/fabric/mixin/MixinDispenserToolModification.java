package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.core.dispenser.DispenseItemBehavior$5")
public abstract class MixinDispenserToolModification extends OptionalDispenseItemBehavior {

    @Inject(method = "execute(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)"
            + "Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "setValue(Lnet/minecraft/world/level/block/state/properties/Property;"
                    + "Ljava/lang/Comparable;)Ljava/lang/Object;"), cancellable = true, require = 1, allow = 1)
    private void nekojs$onLightBlock(BlockSource source, ItemStack dispensed, CallbackInfoReturnable<ItemStack> cir,
                                    @Local ServerLevel level, @Local BlockPos pos, @Local BlockState state) {
        if (FabricBlockEventBindingsV2.postToolModification(level, pos, state, null)) {
            setSuccess(false);
            cir.setReturnValue(dispensed);
        }
    }
}
