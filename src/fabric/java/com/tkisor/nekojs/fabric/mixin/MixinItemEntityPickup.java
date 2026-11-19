package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for item-pickup stage gate enforcement and {@code ItemEvents.canPickUp},
 * {@code ItemEvents.pickedUpPre}, and {@code ItemEvents.pickedUp}:
 * {@code ItemEntity#playerTouch(Player)}.
 *
 * <p>Pre is injected at HEAD and is cancellable: stage gate denial or a listener returning true
 * calls {@code ci.cancel()}, skipping the pickup cleanly. Post is injected at TAIL and checks
 * both {@code isRemoved()} and whether the stack count decreased so partial pickups also fire
 * {@code postPickedUp}. Both filter client instances with {@code level().isClientSide()}.
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntityPickup {

    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void nekojs$onPlayerTouchPre(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (!StageGateEnforcer.allowPickUpItem(player, self.getItem())) {
            ci.cancel();
            return;
        }
        if (FabricItemEventBindingsV2.postCanPickUp(player, self)) {
            ci.cancel();
            return;
        }
    }

    @WrapOperation(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean nekojs$onInventoryPickup(Inventory inventory, ItemStack stack, Operation<Boolean> original,
                                             Player player) {
        if (!ItemEvents.PICKED_UP.hasListeners()) {
            return original.call(inventory, stack);
        }
        ItemStack pickedUp = stack.copy();
        boolean added = original.call(inventory, stack);
        pickedUp.shrink(stack.getCount());
        if (added && !pickedUp.isEmpty()) {
            FabricItemEventBindingsV2.postStablePickedUp(player, (ItemEntity) (Object) this, pickedUp);
        }
        return added;
    }

    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void nekojs$onPlayerTouchPost(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide() && self.isRemoved()) {
            FabricItemEventBindingsV2.postPickedUp(player, self);
        }
    }
}
