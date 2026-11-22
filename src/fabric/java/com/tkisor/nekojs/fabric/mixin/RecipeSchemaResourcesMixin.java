package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.resource.RecipeTypeDefinitionResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps definitions with the incoming recipe manager until the server binds those resources. */
@Mixin(RecipeManager.class)
public abstract class RecipeSchemaResourcesMixin implements RecipeTypeDefinitionResources.Prepared {
    @Unique
    private RecipeTypeDefinitionRegistry nekojs$dataPackSchemas = RecipeTypeDefinitionRegistry.EMPTY;

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;", at = @At("HEAD"))
    private void nekojs$prepareDataPackSchemas(ResourceManager manager, ProfilerFiller profiler,
                                               CallbackInfoReturnable<RecipeMap> callback) {
        this.nekojs$dataPackSchemas = RecipeTypeDefinitionResources.load(manager);
    }

    @Unique
    @Override
    public void nekojs$publishDataPackSchemas() {
        RecipeTypeDefinitionStorage.replace(this.nekojs$dataPackSchemas);
    }
}
