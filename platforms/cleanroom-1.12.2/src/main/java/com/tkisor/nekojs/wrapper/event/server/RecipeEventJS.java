package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.RecipeEntryJS;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.mixin.ForgeRegistryMixin;
import com.tkisor.nekojs.wrapper.RecipeRegistryProxy;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

/**
 * 1.12.2 RecipeEventJS - wraps the Forge recipe registry ({@link CraftingManager#REGISTRY})
 * for script access. Works with {@link IRecipe} directly, not the datapack JSON system.
 *
 * <h2>Cross-platform parity</h2>
 * <p>This implements the full {@link RecipeLifecycleContext} contract plus the iteration /
 * filtering / replace methods that scripts use on the datapack-era platforms. The key
 * 1.12.2 caveats:
 * <ul>
 *   <li><b>No JSON for live recipes.</b> 1.12.2 recipes are not datapack JSON; the only
 *       JSON available is what scripts stage via {@link #setJson}. {@link #getJson} for a
 *       live recipe throws {@link UnsupportedOperationException}.</li>
 *   <li><b>Filters operate on the in-memory {@code IRecipe}.</b> A {@link RecipeFilter}
 *       (parsed by {@code RecipeFilterAdapter}) is evaluated via
 *       {@link RecipeFilter#test(RecipeEntryJS)}.</li>
 *   <li><b>Recipe creation</b> lives on {@code event.recipes.minecraft.*}
 *       ({@code MinecraftRecipeHandler}); the matching methods here throw
 *       {@link UnsupportedOperationException} pointing there.</li>
 * </ul>
 */
public class RecipeEventJS implements RecipeLifecycleContext {
    // Lazily created by getRecipes(): constructing the proxy in a constructor or field
    // initializer would hand a partially initialized `this` to another object (this-escape).
    private volatile RecipeRegistryProxy recipesProxy;
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
        // Only recipes staged via setJson have JSON on 1.12.2.
        if (recipeJsons.containsKey(id)) {
            return recipeJsons.get(id);
        }
        // Live recipes are not JSON-backed on 1.12.2 (pre-datapack era). Refuse with a
        // helpful message rather than silently returning null for an existing recipe.
        ResourceLocation rl = new ResourceLocation(id);
        if (CraftingManager.getRecipe(rl) != null) {
            throw new UnsupportedOperationException(
                "getJson for live recipes is not supported on 1.12.2 (pre-datapack era); " +
                "use setJson/removeById, or event.recipes.minecraft.* to recreate the recipe. id=" + id);
        }
        return null;
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
            // Use ForgeRegistryMixin.nekojs$removeEntry for safe removal.
            // Cast through Object because the mixin interface is not on IForgeRegistry.
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
        return dump(null);
    }

    @Override
    public void print() {
        print(null);
    }

    // ========== Filter-based iteration (parity with datapack-era RecipeEventJS) ==========
    //
    // The filter parameter is a RecipeFilter produced by RecipeFilterAdapter (or null).
    // null == no match (find returns empty, matching datapack-era semantics). A flat
    // object is an AND of its criteria; "not" wraps a sub-filter in Not; top-level arrays
    // are an Or.

    /** All recipes as entries. */
    public List<RecipeEntryJS> all() {
        List<RecipeEntryJS> recipes = new ArrayList<>();
        for (String id : recipeIds) {
            IRecipe recipe = CraftingManager.getRecipe(new ResourceLocation(id));
            if (recipe != null) recipes.add(new RecipeEntryJS(this, recipe));
        }
        return recipes;
    }

    /** Recipes matching the filter. {@code filter == null} returns an empty list (matches datapack-era semantics). */
    public List<RecipeEntryJS> find(RecipeFilter filter) {
        List<RecipeEntryJS> recipes = new ArrayList<>();
        if (filter == null) return recipes;
        for (String id : recipeIds) {
            IRecipe recipe = CraftingManager.getRecipe(new ResourceLocation(id));
            if (recipe == null) continue;
            RecipeEntryJS entry = new RecipeEntryJS(this, recipe);
            if (filter.test(entry)) {
                recipes.add(entry);
            }
        }
        return recipes;
    }

    /** Get a single recipe entry by id, or {@code null}. */
    public RecipeEntryJS get(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        IRecipe recipe = CraftingManager.getRecipe(rl);
        return recipe == null ? null : new RecipeEntryJS(this, recipe);
    }

    public int count(RecipeFilter filter) {
        return find(filter).size();
    }

    public boolean exists(RecipeFilter filter) {
        return !find(filter).isEmpty();
    }

    public void remove(RecipeFilter filter) {
        if (filter == null) return;
        List<RecipeEntryJS> toRemove = find(filter);
        int removed = 0;
        for (RecipeEntryJS entry : toRemove) {
            ResourceLocation rl = entry.recipe().getRegistryName();
            if (rl != null) {
                removeById(rl.toString());
                removed++;
            }
        }
        NekoJS.LOGGER.debug("Removed {} recipes matching the filter", removed);
    }

    public void forEach(RecipeFilter filter, Consumer<RecipeEntryJS> callback) {
        for (RecipeEntryJS recipe : find(filter)) {
            callback.accept(recipe);
        }
    }

    public void forEach(Consumer<RecipeEntryJS> callback) {
        for (RecipeEntryJS recipe : all()) {
            callback.accept(recipe);
        }
    }

    /** Alias for {@link #forEach(RecipeFilter, Consumer)} (datapack-era scripts use {@code modify}). */
    public void modify(RecipeFilter filter, Consumer<RecipeEntryJS> callback) {
        forEach(filter, callback);
    }

    public String dump(RecipeFilter filter) {
        List<RecipeEntryJS> recipes = filter == null ? all() : find(filter);
        JsonObject dump = new JsonObject();
        for (RecipeEntryJS recipe : recipes) {
            JsonObject info = new JsonObject();
            info.addProperty("type", recipe.type());
            info.addProperty("group", recipe.group());
            String outId = recipe.outputId();
            if (outId != null) info.addProperty("output", outId);
            dump.add(recipe.id(), info);
        }
        return dump.toString();
    }

    public void print(RecipeFilter filter) {
        List<RecipeEntryJS> recipes = filter == null ? all() : find(filter);
        NekoJS.LOGGER.info("=== Recipes ({} matched) ===", recipes.size());
        for (RecipeEntryJS recipe : recipes) {
            ItemStack out = recipe.output();
            String output = out.isEmpty() ? "<none>" : out.getDisplayName();
            NekoJS.LOGGER.info("  {} [{}] → {}", recipe.id(), recipe.type(), output);
        }
    }

    // ========== Replace input/output ==========
    //
    // 1.12.2 ingredient mutation: there is no JSON to rewrite, so we rebuild each
    // matching recipe's ingredient list with the substituted ingredient and re-register
    // a new Shaped/Shapeless recipe under the same id. Furnace recipes go through
    // FurnaceRecipes. This is intentionally conservative: only Shaped/Shapeless are
    // rewritten in-place; other recipe types are logged and skipped (their inputs are
    // not always representable as a swappable Ingredient list on 1.12.2).

    public void replaceInput(RecipeFilter filter, Ingredient match, Ingredient replacement) {
        if (match == null || replacement == null) return;
        int replaced = 0;
        for (RecipeEntryJS entry : find(filter)) {
            IRecipe recipe = entry.recipe();
            if (recipe instanceof ShapedRecipes shaped) {
                if (replaceInputShaped(shaped, match, replacement)) replaced++;
            } else if (recipe instanceof ShapelessRecipes shapeless) {
                if (replaceInputShapeless(shapeless, match, replacement)) replaced++;
            } else {
                // Cooking recipes / others: inputs are an ItemStack key in FurnaceRecipes,
                // not a mutable Ingredient list — skip with a debug note.
                NekoJS.LOGGER.debug("replaceInput: skipping non-crafting recipe {} ({})",
                    entry.id(), recipe.getClass().getSimpleName());
            }
        }
        NekoJS.LOGGER.debug("Replaced input ingredients in {} recipes", replaced);
    }

    public void replaceOutput(RecipeFilter filter, Ingredient match, ItemStack replacement) {
        if (match == null || replacement == null || replacement.isEmpty()) return;
        int replaced = 0;
        for (RecipeEntryJS entry : find(filter)) {
            IRecipe recipe = entry.recipe();
            ItemStack output = recipe.getRecipeOutput();
            if (output == null || output.isEmpty()) continue;
            if (!match.apply(output)) continue;

            ItemStack newOutput = replacement.copy();
            // Preserve original output count when the replacement is a single item.
            if (newOutput.getCount() == 1 && output.getCount() > 1) {
                newOutput.setCount(output.getCount());
            }

            ResourceLocation id = recipe.getRegistryName();
            if (recipe instanceof ShapedRecipes shaped) {
                ShapedRecipes copy = new ShapedRecipes(
                    shaped.getGroup(), shaped.recipeWidth, shaped.recipeHeight,
                    shaped.recipeItems, newOutput);
                registerReplacement(id, copy);
                replaced++;
            } else if (recipe instanceof ShapelessRecipes shapeless) {
                ShapelessRecipes copy = new ShapelessRecipes(
                    shapeless.getGroup(), newOutput, shapeless.recipeItems);
                registerReplacement(id, copy);
                replaced++;
            } else {
                NekoJS.LOGGER.debug("replaceOutput: skipping non-crafting recipe {} ({})",
                    entry.id(), recipe.getClass().getSimpleName());
            }
        }
        NekoJS.LOGGER.debug("Replaced outputs in {} recipes", replaced);
    }

    private boolean replaceInputShaped(ShapedRecipes recipe, Ingredient match, Ingredient replacement) {
        NonNullList<Ingredient> original = recipe.recipeItems;
        boolean changed = false;
        NonNullList<Ingredient> rebuilt = NonNullList.withSize(original.size(), Ingredient.EMPTY);
        for (int i = 0; i < original.size(); i++) {
            Ingredient ing = original.get(i);
            if (ing == null || ing == Ingredient.EMPTY) {
                rebuilt.set(i, Ingredient.EMPTY);
                continue;
            }
            if (ingredientOverlaps(ing, match)) {
                rebuilt.set(i, replacement);
                changed = true;
            } else {
                rebuilt.set(i, ing);
            }
        }
        if (changed) {
            ResourceLocation id = recipe.getRegistryName();
            ShapedRecipes copy = new ShapedRecipes(
                recipe.getGroup(), recipe.recipeWidth, recipe.recipeHeight, rebuilt, recipe.getRecipeOutput());
            registerReplacement(id, copy);
        }
        return changed;
    }

    private boolean replaceInputShapeless(ShapelessRecipes recipe, Ingredient match, Ingredient replacement) {
        NonNullList<Ingredient> original = recipe.recipeItems;
        boolean changed = false;
        NonNullList<Ingredient> rebuilt = NonNullList.create();
        for (Ingredient ing : original) {
            if (ing != null && ingredientOverlaps(ing, match)) {
                rebuilt.add(replacement);
                changed = true;
            } else {
                rebuilt.add(ing == null ? Ingredient.EMPTY : ing);
            }
        }
        if (changed) {
            ResourceLocation id = recipe.getRegistryName();
            ShapelessRecipes copy = new ShapelessRecipes(
                recipe.getGroup(), recipe.getRecipeOutput(), rebuilt);
            registerReplacement(id, copy);
        }
        return changed;
    }

    /** True if any matching stack of {@code ing} is also matched by {@code match}. */
    private static boolean ingredientOverlaps(Ingredient ing, Ingredient match) {
        if (ing == null || match == null) return false;
        ItemStack[] ingStacks = ing.getMatchingStacks();
        if (ingStacks == null) return false;
        for (ItemStack stack : ingStacks) {
            if (stack != null && !stack.isEmpty() && match.apply(stack)) return true;
        }
        return false;
    }

    /** Re-register a rebuilt recipe under the same id (after removing the old entry). */
    private void registerReplacement(ResourceLocation id, IRecipe replacement) {
        if (id == null) return;
        // Remove the old entry from the forge registry BiMaps, then register the new one.
        try {
            ((ForgeRegistryMixin) (Object) ForgeRegistries.RECIPES).nekojs$removeEntry(id);
        } catch (ClassCastException ignored) {
            // mixin not applied — fall through; register() will still attempt insertion.
        }
        replacement.setRegistryName(id);
        ForgeRegistries.RECIPES.register(replacement);
    }

    // ========== Recipe-creation stubs (UnsupportedOperationException) ==========
    //
    // On 1.12.2 recipe creation is handled by MinecraftRecipeHandler (reachable as
    // event.recipes.minecraft.shaped / .shapeless / .smelting). The datapack-era
    // event.shaped(...) etc. return a RecipeJsonBuilder which only makes sense for the
    // JSON pipeline; on 1.12.2 there is no JSON tree to build, so we refuse these with a
    // pointer to the working alternative.

    public Object shaped(ItemStack result, List<String> pattern, Map<String, Ingredient> keys) {
        throw new UnsupportedOperationException(
            "event.shaped(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.shaped(output, pattern, keys) instead.");
    }

    public Object shapeless(ItemStack result, List<Ingredient> ingredients) {
        throw new UnsupportedOperationException(
            "event.shapeless(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.shapeless(output, ingredients) instead.");
    }

    public Object smelting(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.smelting(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.smelting(input, output, xp) instead.");
    }

    public Object blasting(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.blasting(...) is not supported on 1.12.2 (1.12.2 has no blasting recipe type).");
    }

    public Object smoking(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.smoking(...) is not supported on 1.12.2 (1.12.2 has no smoking recipe type).");
    }

    public Object campfireCooking(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.campfireCooking(...) is not supported on 1.12.2 (1.12.2 has no campfire cooking recipe type).");
    }

    public Object custom(String type, Object value) {
        throw new UnsupportedOperationException(
            "event.custom(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.shaped/shapeless/smelting instead.");
    }

    public Object builder(String type) {
        throw new UnsupportedOperationException(
            "event.builder(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.* instead.");
    }

    public void validateRecipe(String id) {
        // 1.12.2 recipes validate at construction; nothing to JSON-validate here.
    }

    // ========== 1.12.2-specific accessors ==========

    public List<String> getRecipeIds() {
        return Collections.unmodifiableList(recipeIds);
    }

    public RecipeRegistryProxy getRecipes() {
        RecipeRegistryProxy proxy = recipesProxy;
        if (proxy == null) {
            proxy = new RecipeRegistryProxy(this);
            recipesProxy = proxy;
        }
        return proxy;
    }

    /** Get the raw IRecipe for a given ID. */
    public IRecipe getRecipe(String id) {
        return CraftingManager.getRecipe(new ResourceLocation(id));
    }

    /** Remove a recipe by its registry name (string overload; matches existing scripts). */
    public void remove(String recipeId) {
        removeById(recipeId);
    }

    /** Iterate over recipe ids (string form). Use {@link #forEach(Consumer)} for entries. */
    public void forEachId(Consumer<String> callback) {
        for (String id : recipeIds) {
            callback.accept(id);
        }
    }

    // ========== Static recipe introspection helpers ==========

    /**
     * Best-effort recipe type id for 1.12.2. There is no recipe-type registry keyed by
     * id, so this derives a type string from the concrete class. Returns the same ids
     * the datapack era would use ({@code minecraft:crafting_shaped}, etc.) where possible.
     */
    public static String getRecipeTypeId(IRecipe recipe) {
        if (recipe == null) return "unknown";
        if (recipe instanceof ShapedRecipes) return "minecraft:crafting_shaped";
        if (recipe instanceof ShapelessRecipes) return "minecraft:crafting_shapeless";
        // FurnaceRecipes is keyed by ItemStack, not registered as an IRecipe; smelting
        // entries won't reach here through CraftingManager.REGISTRY. Anything else falls
        // back to the simple class name lowercased.
        String name = recipe.getClass().getSimpleName();
        return name.isEmpty() ? "unknown" : name.toLowerCase(Locale.ROOT);
    }

    /** The registry id of a recipe's output item, or {@code null}. */
    public static String getRecipeOutputId(IRecipe recipe) {
        if (recipe == null) return null;
        ItemStack out = recipe.getRecipeOutput();
        if (out == null || out.isEmpty()) return null;
        ResourceLocation rl = out.getItem().getRegistryName();
        return rl == null ? null : rl.toString();
    }

    /** The input ingredients of a recipe, flattened (never null). */
    public static List<Ingredient> getIngredients(IRecipe recipe) {
        if (recipe == null) return Collections.emptyList();
        try {
            NonNullList<Ingredient> list = recipe.getIngredients();
            if (list != null) {
                List<Ingredient> out = new ArrayList<>(list.size());
                for (Ingredient ing : list) {
                    if (ing != null) out.add(ing);
                }
                return out;
            }
        } catch (Throwable ignored) {
            // some modded IRecipe impls throw on getIngredients()
        }
        return Collections.emptyList();
    }
}
