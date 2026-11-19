package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
//? if >=26 {
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
//?}
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shared vanilla stage gate hook on {@link AnvilMenu#createResult} enforcing anvil output item and
 * enchantment stage restrictions.
 */
@Mixin(AnvilMenu.class)
public abstract class MixinAnvilMenuStageGate extends ItemCombinerMenu {

//? if >=26 {
    protected MixinAnvilMenuStageGate(
            @Nullable MenuType<?> menuType,
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            ItemCombinerMenuSlotDefinition slotDefinition
    ) {
        super(menuType, containerId, inventory, access, slotDefinition);
    }
//?} else {
    /*protected MixinAnvilMenuStageGate(
            @Nullable MenuType<?> menuType,
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access
    ) {
        super(menuType, containerId, inventory, access);
    }
*///?}

    @Inject(method = "createResult()V", at = @At("TAIL"))
    private void nekojs$enforceAnvilStageGate(CallbackInfo ci) {
        if (this.player != null && this.player.level().isClientSide()) {
            return;
        }
        ItemStack output = this.resultSlots.getItem(0);
        if (!output.isEmpty() && !StageGateEnforcer.allowAnvilResult(this.player, output)) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.broadcastChanges();
        }
    }
}
