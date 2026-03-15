package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow private HolderLookup.Provider registries;

    @Shadow private RecipeMap recipes;

    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void onApplyDone(RecipeMap recipesParams, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {

        RecipeEventJS eventJS = new RecipeEventJS(this.recipes.values(), this.registries);

        ServerEvents.RECIPES.post(eventJS);

        this.recipes = eventJS.getFinalMap();
    }
}