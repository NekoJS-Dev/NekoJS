package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinMiningItemDestruction {
    @Shadow @Final protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z")
    private boolean nekojs$captureMiningToolDestruction(BlockPos pos, Operation<Boolean> original) {
        return FabricPlayerEventBindings.captureMiningDestruction(player, () -> original.call(pos));
    }

    @WrapOperation(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"),
            require = 1, allow = 1)
    private ItemStack nekojs$trackMiningTool(ItemStack stack, Operation<ItemStack> original) {
        ItemStack snapshot = original.call(stack);
        FabricPlayerEventBindings.trackMiningTool(player, stack, snapshot);
        return snapshot;
    }
}
