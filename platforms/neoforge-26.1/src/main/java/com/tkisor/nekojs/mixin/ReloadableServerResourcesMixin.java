package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.mixin_api.IRecipeManagerExtension;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {

    @Final
    @Shadow private RecipeManager recipes;

    @Inject(method = "updateComponentsAndStaticRegistryTags", at = @At("TAIL"))
    private void onTagsAndComponentsBound(CallbackInfo ci) {
        if (this.recipes instanceof IRecipeManagerExtension ext) {
            ext.nekojs$applyScripts();
        }
    }
}