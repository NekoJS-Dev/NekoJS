package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.mixin.ForgeRegistryMixin;
import com.tkisor.nekojs.wrapper.RecipeRegistryProxy;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.*;

/**
 * 1.12.2 RecipeEventJS - wraps Forge recipe registry for script access.
 * Works with IRecipe/CraftingManager directly, not the datapack JSON system.
 */
public class RecipeEventJS implements RecipeLifecycleContext {
    private final RecipeRegistryProxy recipesProxy = new RecipeRegistryProxy(this);
    private final List<String> recipeIds;
    private final Map<String, String> recipeJsons = new LinkedHashMap<>();

    public RecipeEventJS() {
        this.recipeIds = new ArrayList<>();
        for (ResourceLocation id : CraftingManager.REGISTRY.getKeys()) {
            this.recipeIds.add(id.toString());
        }
    }

    public RecipeEventJS(List<String> recipeIds) {
        this.recipeIds = new ArrayList<>(recipeIds);
    }

    // ========== RecipeLifecycleContext ==========

    @Override
    public Set<String> ids() {
        return new LinkedHashSet<>(recipeIds);
    }

    @Override
    public int count() {
        return recipeIds.size();
    }

    @Override
    public boolean exists(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        return CraftingManager.getRecipe(rl) != null;
    }

    @Override
    public String getJson(String id) {
        return recipeJsons.get(id);
    }

    @Override
    public void setJson(String id, String json) {
        recipeJsons.put(id, json);
    }

    @Override
    public void removeById(String id) {
        NekoJS.LOGGER.debug("RecipeEventJS.removeById({})", id);
        ResourceLocation rl = new ResourceLocation(id);
        IRecipe recipe = CraftingManager.getRecipe(rl);
        if (recipe != null) {
            recipeIds.remove(id);
            recipeJsons.remove(id);
            // Use ForgeRegistryMixin.nekojs$removeEntry for safe removal
            // Cast through Object because the mixin interface is not on IForgeRegistry
            try {
                ((ForgeRegistryMixin) (Object) ForgeRegistries.RECIPES).nekojs$removeEntry(rl);
                NekoJS.LOGGER.info("Removed recipe: {}", id);
            } catch (ClassCastException e) {
                NekoJS.LOGGER.warn("ForgeRegistryMixin not applied - recipe removal may not persist: {}", id);
            }
        }
    }

    @Override
    public String dump() {
        return String.join("\n", recipeIds);
    }

    @Override
    public void print() {
        NekoJS.LOGGER.info("=== Recipes ({} total) ===", recipeIds.size());
        for (String id : recipeIds) {
            IRecipe recipe = CraftingManager.getRecipe(new ResourceLocation(id));
            String output = recipe != null ? recipe.getRecipeOutput().getDisplayName() : "?";
            NekoJS.LOGGER.info("  {} → {}", id, output);
        }
    }

    // ========== 1.12.2-specific API ==========

    public List<String> getRecipeIds() {
        return Collections.unmodifiableList(recipeIds);
    }

    public RecipeRegistryProxy getRecipes() {
        return recipesProxy;
    }

    /** Get the raw IRecipe for a given ID */
    public IRecipe getRecipe(String id) {
        return CraftingManager.getRecipe(new ResourceLocation(id));
    }

    /** Remove a recipe by its registry name */
    public void remove(String recipeId) {
        removeById(recipeId);
    }

    /** Iterator over all recipes */
    public void forEach(java.util.function.Consumer<String> callback) {
        for (String id : recipeIds) {
            callback.accept(id);
        }
    }
}
