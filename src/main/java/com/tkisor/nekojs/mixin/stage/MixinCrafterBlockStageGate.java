package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.compat.McRecipeCompat;
import com.tkisor.nekojs.stage.StageGateEnforcer;
//? if >=26 {
import net.minecraft.server.level.ServerLevel;
//?} else {
/*import net.minecraft.world.level.Level;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Shared vanilla stage gate hook on {@link CrafterBlock#getPotentialResults} enforcing server-scoped
 * recipe and output gates on automated crafter blocks.
 */
@Mixin(CrafterBlock.class)
public abstract class MixinCrafterBlockStageGate {

    @Unique
    private static final NekoId NEKOJS$CRAFTING_TYPE = NekoId.of("minecraft", "crafting");

//? if >=26 {
    @Inject(
            method = "getPotentialResults(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void nekojs$enforceCrafterStageGate(
            ServerLevel level,
            CraftingInput input,
            CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir
    ) {
//?} else {
    /*@Inject(
            method = "getPotentialResults(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void nekojs$enforceCrafterStageGate(
            Level level,
            CraftingInput input,
            CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir
    ) {
*///?}
        Optional<RecipeHolder<CraftingRecipe>> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        if (level != null && level.isClientSide()) {
            return;
        }
        RecipeHolder<CraftingRecipe> holder = result.get();
//? if >=26 {
        NekoId recipeId = NekoIdCompat.fromPlatformId(holder.id().identifier());
//?} else {
        /*NekoId recipeId = NekoIdCompat.fromPlatformId(holder.id());
*///?}
        ItemStack output = level != null
                ? McRecipeCompat.assemble(holder.value(), input, level)
                : ItemStack.EMPTY;
        if (!StageGateEnforcer.allowCrafterRecipe(
                recipeId,
                NEKOJS$CRAFTING_TYPE,
                output != null ? output : ItemStack.EMPTY
        )) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
