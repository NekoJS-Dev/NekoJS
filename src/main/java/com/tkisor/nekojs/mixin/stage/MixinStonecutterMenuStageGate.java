package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shared vanilla stage gate hook on {@link StonecutterMenu} capturing the interacting player and
 * enforcing stonecutting recipe and output item stage gates.
 */
@Mixin(StonecutterMenu.class)
public abstract class MixinStonecutterMenuStageGate {

    @Unique
    private static final NekoId NEKOJS$STONECUTTING_TYPE = NekoId.of("minecraft", "stonecutting");

    @Unique
    @Nullable
    private Player nekojs$player;

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL")
    )
    private void nekojs$capturePlayer(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess access,
            CallbackInfo ci
    ) {
        this.nekojs$player = playerInventory != null ? playerInventory.player : null;
    }

//? if >=26 {
    @Inject(method = "setupResultSlot(I)V", at = @At("TAIL"))
    private void nekojs$enforceStonecutterStageGate(int selectedIndex, CallbackInfo ci) {
//?} else {
    /*@Inject(method = "setupResultSlot()V", at = @At("TAIL"))
    private void nekojs$enforceStonecutterStageGate(CallbackInfo ci) {
*///?}
        Player player = this.nekojs$player;
        if (player != null && player.level().isClientSide()) {
            return;
        }
        StonecutterMenu self = (StonecutterMenu) (Object) this;
        Slot resultSlot = self.getSlot(StonecutterMenu.RESULT_SLOT);
        ItemStack output = resultSlot.getItem();
        RecipeHolder<?> holder = resultSlot.container instanceof ResultContainer rc
                ? rc.getRecipeUsed()
                : null;
        if (output.isEmpty() && holder == null) {
            return;
        }
        NekoId recipeId = nekojs$extractRecipeId(holder);
        if (!StageGateEnforcer.allowAssembleRecipe(player, recipeId, NEKOJS$STONECUTTING_TYPE, output)) {
            if (resultSlot.container instanceof ResultContainer rc) {
                rc.setRecipeUsed(null);
            }
            resultSlot.set(ItemStack.EMPTY);
            self.broadcastChanges();
        }
    }

    @Unique
    @Nullable
    private static NekoId nekojs$extractRecipeId(@Nullable RecipeHolder<?> holder) {
        if (holder == null) {
            return null;
        }
//? if >=26 {
        return NekoIdCompat.fromPlatformId(holder.id().identifier());
//?} else {
        /*return NekoIdCompat.fromPlatformId(holder.id());
*///?}
    }
}
