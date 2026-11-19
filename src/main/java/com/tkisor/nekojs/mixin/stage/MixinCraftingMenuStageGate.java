package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.stage.StageGateEnforcer;
//? if >=26 {
import net.minecraft.server.level.ServerLevel;
//?} else {
/*import net.minecraft.world.level.Level;
*///?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shared vanilla stage gate hook on {@code CraftingMenu#slotChangedCraftingGrid} enforcing crafting
 * recipe and output gates across both crafting tables and player 2x2 inventory grids.
 */
@Mixin(CraftingMenu.class)
public abstract class MixinCraftingMenuStageGate {

    @Unique
    private static final NekoId NEKOJS$CRAFTING_TYPE = NekoId.of("minecraft", "crafting");

//? if >=26 {
    @Inject(
            method = "slotChangedCraftingGrid(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/inventory/ResultContainer;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
            at = @At("TAIL")
    )
    private static void nekojs$enforceCraftingStageGate(
            AbstractContainerMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> recipe,
            CallbackInfo ci
    ) {
//?} else {
    /*@Inject(
            method = "slotChangedCraftingGrid(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/inventory/ResultContainer;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
            at = @At("TAIL")
    )
    private static void nekojs$enforceCraftingStageGate(
            AbstractContainerMenu menu,
            Level level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> recipe,
            CallbackInfo ci
    ) {
*///?}
        if (level.isClientSide()) {
            return;
        }
        ItemStack output = resultSlots.getItem(0);
        RecipeHolder<?> matched = resultSlots.getRecipeUsed() != null ? resultSlots.getRecipeUsed() : recipe;
        if (output.isEmpty() && matched == null) {
            return;
        }
        NekoId recipeId = nekojs$extractRecipeId(matched);
        if (!StageGateEnforcer.allowAssembleRecipe(player, recipeId, NEKOJS$CRAFTING_TYPE, output)) {
            resultSlots.setRecipeUsed(null);
            resultSlots.setItem(0, ItemStack.EMPTY);
            menu.broadcastChanges();
            menu.setRemoteSlot(0, ItemStack.EMPTY);
            menu.sendAllDataToRemote();
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
