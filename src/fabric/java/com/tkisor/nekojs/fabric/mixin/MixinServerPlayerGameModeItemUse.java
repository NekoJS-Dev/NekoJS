package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindings;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameModeItemUse {
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;

    @Inject(method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"),
            cancellable = true, require = 1, allow = 1)
    private void nekojs$beforeStartBlockBreak(BlockPos pos, ServerboundPlayerActionPacket.Action action,
                                              Direction direction, int maxY, int sequence, CallbackInfo ci) {
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                && FabricBlockEventBindings.postStableLeftClick(player, level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "useItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getCount()I", ordinal = 0), cancellable = true)
    private void nekojs$beforeItemUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        if (FabricItemEventBindings.postEligibleRightClick(player, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;gameModeForPlayer:Lnet/minecraft/world/level/GameType;",
                    opcode = Opcodes.GETFIELD), cancellable = true, require = 1, allow = 1)
    private void nekojs$beforeBlockUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                       BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir,
                                       @Local BlockPos pos, @Local BlockState state) {
        if (FabricBlockEventBindings.postEligibleRightClick(player, level, pos, state, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
