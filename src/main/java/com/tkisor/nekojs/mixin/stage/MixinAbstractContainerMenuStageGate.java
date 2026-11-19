package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shared vanilla stage gate hook on {@link AbstractContainerMenu#clicked} enforcing container menu
 * access gates and item container storage restrictions on the logical server.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenuStageGate {
    @Unique private Player nekojs$transferPlayer;

    @Inject(
            method = "clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nekojs$enforceContainerStageGate(
            int slotId,
            int button,
            ContainerInput clickType,
            Player player,
            CallbackInfo ci
    ) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!StageGateEnforcer.allowOpenContainer(player, self)) {
            if (player instanceof ServerPlayer sp) {
                sp.closeContainer();
            }
            ci.cancel();
            return;
        }

        if (slotId >= 0 && slotId < self.slots.size()) {
            Slot slot = self.slots.get(slotId);
            boolean isContainerSlot = slot.container != player.getInventory();
            ItemStack candidateStack = ItemStack.EMPTY;
            if (isContainerSlot) {
                if (!self.getCarried().isEmpty()
                        && (clickType == ContainerInput.PICKUP || clickType == ContainerInput.QUICK_CRAFT)) {
                    candidateStack = self.getCarried();
                } else if (clickType == ContainerInput.SWAP
                        && button >= 0
                        && button < player.getInventory().getContainerSize()) {
                    candidateStack = player.getInventory().getItem(button);
                }
            }

            if (!candidateStack.isEmpty()
                    && !StageGateEnforcer.allowContainerSlotClick(player, self, slotId, candidateStack)) {
                self.sendAllDataToRemote();
                ci.cancel();
            }
        }
    }

    @WrapOperation(
            method = "doClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack nekojs$checkQuickTransfer(AbstractContainerMenu menu, Player player, int index,
                                               Operation<ItemStack> original) {
        Player previous = nekojs$transferPlayer;
        nekojs$transferPlayer = player;
        try {
            return original.call(menu, player, index);
        } finally {
            nekojs$transferPlayer = previous;
        }
    }

    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack nekojs$checkQuickMerge(Slot slot, Operation<ItemStack> original, ItemStack incoming,
                                            int start, int end, boolean reverse) {
        return nekojs$allowQuickTransfer(slot, incoming) ? original.call(slot) : ItemStack.EMPTY;
    }

    @Unique
    private boolean nekojs$allowQuickTransfer(Slot slot, ItemStack stack) {
        Player player = nekojs$transferPlayer;
        if (!(player instanceof ServerPlayer) || slot.container == player.getInventory()) return true;
        var menu = (AbstractContainerMenu) (Object) this;
        return StageGateEnforcer.allowContainerSlotClick(player, menu, slot.index, stack);
    }

    @WrapOperation(
            method = {"doClick", "moveItemStackTo"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean nekojs$checkOffhandPlacement(Slot slot, ItemStack stack, Operation<Boolean> original) {
        return original.call(slot, stack) && nekojs$allowOffhandPlacement(slot, stack)
                && nekojs$allowQuickTransfer(slot, stack);
    }

    @WrapOperation(
            method = "doClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack nekojs$checkOffhandInsertion(Slot slot, ItemStack stack, int count, Operation<ItemStack> original) {
        return nekojs$allowOffhandPlacement(slot, stack) ? original.call(slot, stack, count) : stack;
    }

    @WrapOperation(
            method = "doClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V")
    )
    private void nekojs$checkMenuOffhandSwap(
            Inventory inventory, int index, ItemStack stack, Operation<Void> original, @Cancellable CallbackInfo ci
    ) {
        if (index == Inventory.SLOT_OFFHAND
                && inventory.player instanceof ServerPlayer player
                && !StageGateEnforcer.allowEquipItem(player, stack)) {
            if (player.containerMenu != player.inventoryMenu) {
                player.inventoryMenu.sendAllDataToRemote();
            }
            ci.cancel();
            return;
        }
        original.call(inventory, index, stack);
    }

    @Unique
    private static boolean nekojs$allowOffhandPlacement(Slot slot, ItemStack stack) {
        if (slot.getContainerSlot() == Inventory.SLOT_OFFHAND
                && slot.container instanceof Inventory inventory
                && inventory.player instanceof ServerPlayer player) {
            return StageGateEnforcer.allowEquipItem(player, stack);
        }
        return true;
    }
}
