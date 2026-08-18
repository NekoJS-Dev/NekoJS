package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.recipe.RecipeEntryJS;
import com.tkisor.nekojs.api.recipe.RecipeFieldRoles;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.definition.LegacyRecipeSchemaScanner;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.mixin.ForgeRegistryMixin;
import com.tkisor.nekojs.wrapper.RecipeRegistryProxy;
import com.tkisor.nekojs.wrapper.ReflectiveRecipeBuilder;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
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
@Doc("Server recipes event: iterate, filter, remove, and modify crafting recipes.")
@Doc("Recipes are live IRecipe objects, not datapack JSON; creation goes through event.recipes.minecraft.*.")
public class RecipeEventJS implements RecipeLifecycleContext {
    // Lazily created by getRecipes(): constructing the proxy in a constructor or field
    // initializer would hand a partially initialized `this` to another object (this-escape).
    private volatile RecipeRegistryProxy recipesProxy;
    private final List<String> recipeIds;
    private final Map<String, String> recipeJsons = new LinkedHashMap<>();

    /** registerSchema 注册的 script schema（key = "ns:type"），每次事件运行累积。 */
    private final Map<String, RecipeTypeDefinition> scriptSchemas = new LinkedHashMap<>();
    /** script schema 声明的构造器字段名序列（key = "ns:type"；缺省 null = 无参+字段注入）。 */
    private final Map<String, List<String>> scriptCtorFields = new LinkedHashMap<>();
    /** script schema 声明的配方类 FQN（key = "ns:type"；缺省用自动扫描的 recipeClass）。 */
    private final Map<String, String> scriptClasses = new LinkedHashMap<>();
    /** 等待 flush 的反射 builder（构造即入队）。 */
    private final List<ReflectiveRecipeBuilder> pendingBuilders = new ArrayList<>();

    /** Creates an event snapshotting the current crafting registry. */
    public RecipeEventJS() {
        this.recipeIds = new ArrayList<>();
        for (ResourceLocation id : CraftingManager.REGISTRY.getKeys()) {
            this.recipeIds.add(id.toString());
        }
    }

    /** Creates an event from an explicit id list (testing / custom pipelines). */
    public RecipeEventJS(List<String> recipeIds) {
        this.recipeIds = new ArrayList<>(recipeIds);
    }

    // ========== RecipeLifecycleContext ==========

    @Doc("Lists the registry ids of all crafting recipes.")
    @Return("set of recipe ids like 'minecraft:stick'; never null")
    @Override
    public Set<String> ids() {
        return new LinkedHashSet<>(recipeIds);
    }

    @Doc("Counts all crafting recipes.")
    @Return("the number of recipes")
    @Override
    public int count() {
        return recipeIds.size();
    }

    @Doc("Checks whether a recipe with the given id exists.")
    @Param(name = "id", value = "recipe registry id like 'minecraft:stick'")
    @Return("true if the recipe exists")
    @Override
    public boolean exists(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        return CraftingManager.getRecipe(rl) != null;
    }

    @Doc("Gets the staged JSON of a recipe.")
    @Doc("Live recipes are not JSON-backed on 1.12.2; only recipes staged via setJson have JSON.")
    @Param(name = "id", value = "recipe registry id")
    @Return("the staged JSON string, or null for unknown ids; throws UnsupportedOperationException for live recipes")
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

    @Doc("Stages a JSON document for a recipe id (1.12.2: bookkeeping only).")
    @Param(name = "id", value = "recipe registry id")
    @Param(name = "json", value = "the JSON document to stage")
    @Override
    public void setJson(String id, String json) {
        recipeJsons.put(id, json);
    }

    @Doc("Removes a recipe by its registry id.")
    @Param(name = "id", value = "recipe registry id like 'minecraft:stick'")
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

    @Doc("Dumps all recipes as a JSON object string.")
    @Return("JSON object mapping recipe id to {type, group, output}")
    @Override
    public String dump() {
        return dump(null);
    }

    @Doc("Prints all recipes to the log.")
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
    @Doc("Lists all recipes as entries.")
    @Return("a new list of RecipeEntryJS; never null")
    public List<RecipeEntryJS> all() {
        List<RecipeEntryJS> recipes = new ArrayList<>();
        for (String id : recipeIds) {
            IRecipe recipe = CraftingManager.getRecipe(new ResourceLocation(id));
            if (recipe != null) recipes.add(new RecipeEntryJS(this, recipe));
        }
        return recipes;
    }

    /** Recipes matching the filter. {@code filter == null} returns an empty list (matches datapack-era semantics). */
    @Doc("Lists recipes matching a filter.")
    @Param(name = "filter", value = "recipe filter; null returns an empty list (datapack-era semantics)")
    @Return("the matching recipes as entries")
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
    @Doc("Gets a single recipe entry by id.")
    @Param(name = "id", value = "recipe registry id like 'minecraft:stick'")
    @Return("the recipe entry, or null if no such recipe exists")
    public RecipeEntryJS get(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        IRecipe recipe = CraftingManager.getRecipe(rl);
        return recipe == null ? null : new RecipeEntryJS(this, recipe);
    }

    /** Counts recipes matching a filter. */
    @Doc("Counts recipes matching a filter.")
    @Param(name = "filter", value = "recipe filter; null counts nothing")
    @Return("the number of matching recipes")
    public int count(RecipeFilter filter) {
        return find(filter).size();
    }

    /** Checks whether any recipe matches a filter. */
    @Doc("Checks whether any recipe matches a filter.")
    @Param(name = "filter", value = "recipe filter")
    @Return("true if at least one recipe matches")
    public boolean exists(RecipeFilter filter) {
        return !find(filter).isEmpty();
    }

    /** Removes all recipes matching a filter. */
    @Doc("Removes all recipes matching a filter.")
    @Param(name = "filter", value = "recipe filter; null removes nothing")
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

    /** Iterates matching recipes. */
    @Doc("Iterates over recipes matching a filter.")
    @Param(name = "filter", value = "recipe filter; null iterates nothing")
    @Param(name = "callback", value = "callback invoked with each matching RecipeEntryJS")
    public void forEach(RecipeFilter filter, Consumer<RecipeEntryJS> callback) {
        for (RecipeEntryJS recipe : find(filter)) {
            callback.accept(recipe);
        }
    }

    /** Iterates all recipes. */
    @Doc("Iterates over all recipes.")
    @Param(name = "callback", value = "callback invoked with each RecipeEntryJS")
    public void forEach(Consumer<RecipeEntryJS> callback) {
        for (RecipeEntryJS recipe : all()) {
            callback.accept(recipe);
        }
    }

    /** Alias for {@link #forEach(RecipeFilter, Consumer)} (datapack-era scripts use {@code modify}). */
    @Doc("Alias for forEach(filter, callback), kept for datapack-era scripts.")
    @Param(name = "filter", value = "recipe filter")
    @Param(name = "callback", value = "callback invoked with each matching RecipeEntryJS")
    public void modify(RecipeFilter filter, Consumer<RecipeEntryJS> callback) {
        forEach(filter, callback);
    }

    /** Dumps matching recipes as JSON. */
    @Doc("Dumps matching recipes as a JSON object string.")
    @Param(name = "filter", value = "recipe filter; null dumps all recipes")
    @Return("JSON object mapping recipe id to {type, group, output}")
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

    /** Prints matching recipes to the log. */
    @Doc("Prints matching recipes to the log.")
    @Param(name = "filter", value = "recipe filter; null prints all recipes")
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

    /** Replaces matching input ingredients in shaped/shapeless recipes. */
    @Doc("Replaces matching input ingredients in shaped and shapeless crafting recipes.")
    @Doc("Non-crafting recipe types (e.g. furnace) are skipped.")
    @Param(name = "filter", value = "recipe filter selecting the recipes to rewrite")
    @Param(name = "match", value = "ingredient whose stacks should be replaced")
    @Param(name = "replacement", value = "the substitute ingredient")
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

    /** Replaces matching outputs in shaped/shapeless recipes. */
    @Doc("Replaces the output of recipes whose current output matches the given ingredient.")
    @Doc("The original output count is preserved when the replacement is a single item.")
    @Param(name = "filter", value = "recipe filter selecting the recipes to rewrite")
    @Param(name = "match", value = "ingredient matching the current outputs to replace")
    @Param(name = "replacement", value = "the new output item stack")
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

    @Doc("Not supported on 1.12.2 (JSON recipe builder is datapack-era).")
    @Doc("Use event.recipes.minecraft.shaped(output, pattern, keys) instead.")
    @Param(name = "result", value = "the result item stack")
    @Param(name = "pattern", value = "rows of pattern characters")
    @Param(name = "keys", value = "map of pattern character to ingredient")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object shaped(ItemStack result, List<String> pattern, Map<String, Ingredient> keys) {
        throw new UnsupportedOperationException(
            "event.shaped(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.shaped(output, pattern, keys) instead.");
    }

    @Doc("Not supported on 1.12.2 (JSON recipe builder is datapack-era).")
    @Doc("Use event.recipes.minecraft.shapeless(output, ingredients) instead.")
    @Param(name = "result", value = "the result item stack")
    @Param(name = "ingredients", value = "list of ingredients")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object shapeless(ItemStack result, List<Ingredient> ingredients) {
        throw new UnsupportedOperationException(
            "event.shapeless(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.shapeless(output, ingredients) instead.");
    }

    @Doc("Not supported on 1.12.2 (JSON recipe builder is datapack-era).")
    @Doc("Use event.recipes.minecraft.smelting(input, output, xp) instead.")
    @Param(name = "result", value = "the result item stack")
    @Param(name = "ingredient", value = "the input ingredient")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object smelting(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.smelting(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.smelting(input, output, xp) instead.");
    }

    @Doc("Not supported on 1.12.2 (1.12.2 has no blasting recipe type).")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object blasting(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.blasting(...) is not supported on 1.12.2 (1.12.2 has no blasting recipe type).");
    }

    @Doc("Not supported on 1.12.2 (1.12.2 has no smoking recipe type).")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object smoking(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.smoking(...) is not supported on 1.12.2 (1.12.2 has no smoking recipe type).");
    }

    @Doc("Not supported on 1.12.2 (1.12.2 has no campfire cooking recipe type).")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object campfireCooking(ItemStack result, Ingredient ingredient) {
        throw new UnsupportedOperationException(
            "event.campfireCooking(...) is not supported on 1.12.2 (1.12.2 has no campfire cooking recipe type).");
    }

    @Doc("Not supported on 1.12.2 (JSON recipe builder is datapack-era).")
    @Doc("Use event.recipes.minecraft.shaped/shapeless/smelting instead.")
    @Param(name = "type", value = "recipe type id")
    @Param(name = "value", value = "recipe data")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object custom(String type, Object value) {
        throw new UnsupportedOperationException(
            "event.custom(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.shaped/shapeless/smelting instead.");
    }

    @Doc("Not supported on 1.12.2 (JSON recipe builder is datapack-era).")
    @Doc("Use event.recipes.minecraft.* instead.")
    @Param(name = "type", value = "recipe type id")
    @Return("never returns; always throws UnsupportedOperationException")
    public Object builder(String type) {
        throw new UnsupportedOperationException(
            "event.builder(...) is not supported on 1.12.2 (JSON recipe builder is datapack-era). " +
            "Use event.recipes.minecraft.* instead.");
    }

    /** No-op: 1.12.2 recipes validate at construction. */
    @Doc("No-op on 1.12.2; recipes validate at construction.")
    @Param(name = "id", value = "recipe registry id")
    public void validateRecipe(String id) {
        // 1.12.2 recipes validate at construction; nothing to JSON-validate here.
    }

    // ========== 1.12.2-specific accessors ==========

    /** Unmodifiable snapshot of recipe ids. */
    @Doc("Lists the recipe ids known to this event.")
    @Return("an unmodifiable list of recipe id strings")
    public List<String> getRecipeIds() {
        return Collections.unmodifiableList(recipeIds);
    }

    /** Namespace proxy for typed recipe creation ({@code event.recipes.*}). */
    @Doc("Returns the recipe namespace proxy for typed recipe creation ({@code event.recipes.minecraft.shaped(...)}).")
    @Return("the lazily created RecipeRegistryProxy bound to this event")
    public RecipeRegistryProxy getRecipes() {
        RecipeRegistryProxy proxy = recipesProxy;
        if (proxy == null) {
            proxy = new RecipeRegistryProxy(this);
            recipesProxy = proxy;
        }
        return proxy;
    }

    // ========== Recipe schema（自动发现 + 脚本注册 + 反射构造） ==========

    /** 当前生效的 recipe 类型定义（auto + plugin + data + script 四层合并）。 */
    @Doc("Returns the currently effective recipe type definitions (auto + plugin + data + script layers merged).")
    @Return("the active RecipeTypeDefinitionRegistry")
    public RecipeTypeDefinitionRegistry getRecipeTypeDefinitions() {
        return RecipeTypeDefinitionStorage.current();
    }

    /**
     * 脚本侧注册 recipe schema（1.12.2 兜底口）。
     * spec: { fields: {name: kind}, ctor?: [fieldName...], class?: fqn }
     * kinds: ingredient | itemstack | int | number | boolean | string | json
     */
    @Doc("Registers a recipe schema from a script (1.12.2 fallback channel).")
    @Doc("spec shape: { fields: { name: kind }, ctor?: [fieldName...], class?: fqn }; kinds: ingredient, itemstack, int, number, boolean, string, json.")
    @Param(name = "namespace", value = "schema namespace, e.g. the mod id or 'nekojs'")
    @Param(name = "type", value = "schema type name; combined key is 'namespace:type'")
    @Param(name = "spec", value = "spec object with a required fields map and optional ctor/class entries")
    public void registerSchema(String namespace, String type, Value spec) {
        if (spec == null || !spec.hasMember("fields")) {
            throw new IllegalArgumentException("registerSchema requires spec.fields = { name: kind, ... }");
        }
        String key = namespace + ":" + type;
        Value fieldsVal = spec.getMember("fields");
        Map<String, RecipeFieldDefinition> fields = new LinkedHashMap<>();
        for (String name : fieldsVal.getMemberKeys()) {
            RecipeFieldKind kind = RecipeFieldKind.parse(fieldsVal.getMember(name).asString());
            fields.put(name, new RecipeFieldDefinition(name, name, kind, false, false, null,
                    RecipeFieldRoles.roleOfName(name)));
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("registerSchema: fields must not be empty for " + key);
        }

        List<List<String>> constructors = new ArrayList<>();
        List<String> ctor = null;
        if (spec.hasMember("ctor")) {
            ctor = new ArrayList<>();
            Value ctorVal = spec.getMember("ctor");
            for (long i = 0; i < ctorVal.getArraySize(); i++) {
                String fieldName = ctorVal.getArrayElement(i).asString();
                if (!fields.containsKey(fieldName)) {
                    throw new IllegalArgumentException("registerSchema: ctor field '" + fieldName
                            + "' is not declared in fields for " + key);
                }
                ctor.add(fieldName);
            }
            constructors.add(List.copyOf(ctor));
        } else {
            constructors.add(List.of()); // 无 ctor = 无参 + 字段注入；脚本用命名对象 { ... } 传参
        }
        scriptCtorFields.put(key, ctor);

        if (spec.hasMember("class")) {
            scriptClasses.put(key, spec.getMember("class").asString());
        }

        scriptSchemas.put(key, new RecipeTypeDefinition(namespace, type, key, namespace + "_" + type,
                constructors, Map.copyOf(fields), List.of()));
        RecipeTypeDefinitionRegistry.Builder builder = RecipeTypeDefinitionRegistry.builder();
        scriptSchemas.values().forEach(builder::add);
        RecipeTypeDefinitionStorage.replaceScript(builder.build());
    }

    /** 反射 builder 的配方类：script schema 的 class FQN → 自动扫描的 recipeClass。 */
    @Doc("Resolves the recipe class for a script schema: script class FQN, else the auto-scanned recipe class.")
    @Param(name = "namespace", value = "schema namespace")
    @Param(name = "type", value = "schema type name")
    @Return("the recipe class, or null if it cannot be loaded")
    public Class<?> recipeClassFor(String namespace, String type) {
        String fqn = scriptClasses.get(namespace + ":" + type);
        if (fqn != null) {
            return loadClass(fqn);
        }
        return LegacyRecipeSchemaScanner.recipeClass(namespace, type);
    }

    /** script schema 声明的构造器字段名序列；null = 无参 + 字段注入。 */
    @Doc("Returns the constructor field names declared by a script schema.")
    @Param(name = "namespace", value = "schema namespace")
    @Param(name = "type", value = "schema type name")
    @Return("the constructor field name list, or null for no-arg construction with field injection")
    public List<String> scriptCtorFieldsFor(String namespace, String type) {
        return scriptCtorFields.get(namespace + ":" + type);
    }

    private static Class<?> loadClass(String fqn) {
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                RecipeEventJS.class.getClassLoader(),
                Loader.instance().getModClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                return Class.forName(fqn, false, cl);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // 尝试下一个 classloader
            }
        }
        return null;
    }

    /* ================= pending builders（schema 反射构造） ================= */

    /** Enqueues a reflective recipe builder for the event flush. */
    @Doc("Enqueues a reflective recipe builder; registered when the event completes.")
    @Param(name = "builder", value = "the builder to enqueue")
    public void addPendingBuilder(ReflectiveRecipeBuilder builder) {
        pendingBuilders.add(builder);
    }

    /** 事件收尾：构造并注册所有 schema 驱动配方。逐条记录错误，不中断。 */
    @Doc("Registers all schema-driven recipes staged by reflective builders.")
    @Doc("Called automatically when the event completes; errors are logged per recipe without aborting.")
    public void flushPendingRecipeBuilders() {
        for (ReflectiveRecipeBuilder builder : pendingBuilders) {
            builder.register();
        }
        pendingBuilders.clear();
    }

    /** Get the raw IRecipe for a given ID. */
    @Doc("Gets the raw Forge IRecipe behind an id.")
    @Param(name = "id", value = "recipe registry id")
    @Return("the raw IRecipe, or null if not found")
    public IRecipe getRecipe(String id) {
        return CraftingManager.getRecipe(new ResourceLocation(id));
    }

    /** Remove a recipe by its registry name (string overload; matches existing scripts). */
    @Doc("Removes a recipe by its registry id (string overload).")
    @Param(name = "recipeId", value = "recipe registry id like 'minecraft:stick'")
    public void remove(String recipeId) {
        removeById(recipeId);
    }

    /** Iterate over recipe ids (string form). Use {@link #forEach(Consumer)} for entries. */
    @Doc("Iterates over recipe ids as strings.")
    @Param(name = "callback", value = "callback invoked with each recipe id")
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
    @Doc("Derives a best-effort recipe type id from the concrete recipe class.")
    @Param(name = "recipe", value = "the recipe to classify; null yields 'unknown'")
    @Return("a type id like 'minecraft:crafting_shaped' where possible, else the lowercased class name")
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
    @Doc("Gets the registry id of a recipe's output item.")
    @Param(name = "recipe", value = "the recipe to inspect")
    @Return("the output item id like 'minecraft:stick', or null if there is no output")
    public static String getRecipeOutputId(IRecipe recipe) {
        if (recipe == null) return null;
        ItemStack out = recipe.getRecipeOutput();
        if (out == null || out.isEmpty()) return null;
        ResourceLocation rl = out.getItem().getRegistryName();
        return rl == null ? null : rl.toString();
    }

    /** The input ingredients of a recipe, flattened (never null). */
    @Doc("Flattens the input ingredients of a recipe.")
    @Param(name = "recipe", value = "the recipe to inspect")
    @Return("the non-null ingredients; empty list if the recipe provides none or throws")
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
