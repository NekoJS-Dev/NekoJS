package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric hook for item-drop stage gate enforcement and {@code ItemEvents.dropped}: targets
 * {@code ServerPlayer#drop(ItemStack, boolean, boolean)}, the three-argument overload in 26.x
 * where both GUI drops ({@code Player#drop(ItemStack, boolean)}) and hotbar drops
 * ({@code ServerPlayer#drop(boolean)}) converge.
 *
 * <p>Because callers have already removed {@code stack} from the player's inventory or cursor
 * before invoking {@code drop}, denying a drop through {@link StageGateEnforcer#allowDropItem}
 * restores a copy of {@code stack} back into the player's inventory (or carried cursor slot if
 * all inventory slots are full) and broadcasts slot changes before returning {@code null}.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerItemDrop {

    @WrapOperation(method = "drop(Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity nekojs$hotbarToss(ServerPlayer player, ItemStack stack, boolean spread, boolean retainOwnership,
                                           Operation<ItemEntity> original) {
        return FabricItemEventBindingsV2.withPlayerToss(player, stack,
                () -> original.call(player, stack, spread, retainOwnership));
    }

    @WrapMethod(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;")
    private ItemEntity nekojs$onDrop(ItemStack stack, boolean spread, boolean retainOwnership,
                                    Operation<ItemEntity> original) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        boolean playerToss = FabricItemEventBindingsV2.takePlayerToss(self, stack);
        if (self.isDeadOrDying() || stack.isEmpty()) {
            // Death drops (equipment and inventory) bypass player toss gates and events.
            return playerToss
                    ? FabricItemEventBindingsV2.finishPlayerToss(self, stack, () -> original.call(stack, spread, retainOwnership))
                    : original.call(stack, spread, retainOwnership);
        }
        if (!StageGateEnforcer.allowDropItem(self, stack)) {
            ItemStack restored = stack.copy();
            self.getInventory().add(restored);
            if (!restored.isEmpty() && self.containerMenu != null && self.containerMenu.getCarried().isEmpty()) {
                self.containerMenu.setCarried(restored);
            }
            self.inventoryMenu.broadcastChanges();
            if (self.containerMenu != null && self.containerMenu != self.inventoryMenu) {
                self.containerMenu.broadcastChanges();
            }
            return null;
        }
        if (FabricItemEventBindingsV2.postDropped(self, stack)) return null;
        return playerToss
                ? FabricItemEventBindingsV2.finishPlayerToss(self, stack, () -> original.call(stack, spread, retainOwnership))
                : original.call(stack, spread, retainOwnership);
    }
}
