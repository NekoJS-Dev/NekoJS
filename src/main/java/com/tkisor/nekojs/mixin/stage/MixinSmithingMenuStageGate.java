package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
//? if >=26 {
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
//?}
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
//? if <26 {
/*import net.minecraft.world.item.crafting.SmithingRecipe;
*///?}
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
//? if <26 {
/*import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shared vanilla stage gate hook on {@link SmithingMenu#createResult} enforcing smithing recipe and
 * output item stage gates.
 */
@Mixin(SmithingMenu.class)
public abstract class MixinSmithingMenuStageGate extends ItemCombinerMenu {

    @Unique
    private static final NekoId NEKOJS$SMITHING_TYPE = NekoId.of("minecraft", "smithing");

//? if <26 {
    /*// Legacy pickup checks this field independently of the result container.
    @Shadow
    @Nullable
    private RecipeHolder<SmithingRecipe> selectedRecipe;
*///?}

//? if >=26 {
    protected MixinSmithingMenuStageGate(
            @Nullable MenuType<?> menuType,
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            ItemCombinerMenuSlotDefinition slotDefinition
    ) {
        super(menuType, containerId, inventory, access, slotDefinition);
    }
//?} else {
    /*protected MixinSmithingMenuStageGate(
            @Nullable MenuType<?> menuType,
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access
    ) {
        super(menuType, containerId, inventory, access);
    }
*///?}

    @Inject(method = "createResult()V", at = @At("TAIL"))
    private void nekojs$enforceSmithingStageGate(CallbackInfo ci) {
        if (this.player != null && this.player.level().isClientSide()) {
            return;
        }
        ItemStack output = this.resultSlots.getItem(0);
//? if >=26 {
        RecipeHolder<?> holder = this.resultSlots.getRecipeUsed();
//?} else {
        /*RecipeHolder<?> holder = this.selectedRecipe;
*///?}
        if (output.isEmpty() && holder == null) {
            return;
        }
        NekoId recipeId = nekojs$extractRecipeId(holder);
        if (!StageGateEnforcer.allowAssembleRecipe(this.player, recipeId, NEKOJS$SMITHING_TYPE, output)) {
//? if <26 {
            /*this.selectedRecipe = null;
*///?}
            this.resultSlots.setRecipeUsed(null);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.broadcastChanges();
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
