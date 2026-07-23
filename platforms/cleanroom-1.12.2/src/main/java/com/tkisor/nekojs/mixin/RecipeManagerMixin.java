package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 RecipeManagerMixin - targets {@link CraftingManager} to add recipe script support.
 *
 * <p>In 1.12.2, recipes are stored in {@link CraftingManager#REGISTRY} (RegistryNamespaced&lt;ResourceLocation, IRecipe&gt;).
 * This mixin implements {@link IRecipeManagerExtension} to allow scripts to modify recipes at runtime.
 */
@Mixin(CraftingManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension {

    @Override
    public void nekojs$applyScripts() {
        // Build a list of recipe IDs from the Forge recipe registry
        // In 1.12.2, CraftingManager.REGISTRY is a RegistryNamespaced<ResourceLocation, IRecipe>
        List<String> recipeIds = new ArrayList<>();
        for (ResourceLocation id : CraftingManager.REGISTRY.getKeys()) {
            recipeIds.add(id.toString());
        }

        RecipeEventJS eventJS = new RecipeEventJS(recipeIds);
        try {
            NekoRuntimeAccess.get().beforeRecipeLoading(eventJS);
            ServerEvents.RECIPES.post(eventJS);
            ServerEvents.AFTER_RECIPES.post(eventJS);
            NekoRuntimeAccess.get().afterRecipes(eventJS);
        } catch (PolyglotException e) {
            NekoJSMod.RUNTIME_ROOT.errorTracker().recordEventError(ScriptType.SERVER, e);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Recipe script execution crashed", e);
        }

        ScriptType.SERVER.logger().debug("Recipe script execution completed, total recipes: {}", recipeIds.size());
    }
}
