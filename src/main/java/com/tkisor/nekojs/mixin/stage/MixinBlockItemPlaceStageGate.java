package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shared vanilla stage gate hook on {@link BlockItem#place} blocking unauthorized block placement
 * on the logical server across both NeoForge and Fabric.
 */
@Mixin(BlockItem.class)
public abstract class MixinBlockItemPlaceStageGate {

    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nekojs$enforceBlockPlaceStageGate(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (context == null) {
            return;
        }
        Player player = context.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        BlockItem self = (BlockItem) (Object) this;
        if (!StageGateEnforcer.allowPlaceBlock(
                player,
                context.getLevel(),
                context.getClickedPos(),
                self.getBlock().defaultBlockState()
        )) {
            if (player.containerMenu != null) {
                player.containerMenu.sendAllDataToRemote();
            }
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
