//? if neoforge {
package com.tkisor.nekojs.integration.jei;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.bindings.event.RecipeViewerEvents;
import com.tkisor.nekojs.core.stage.ClientStageMirror;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerCategoryListJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerEntryListJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerInformationJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerRecipeListJS;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JEI integration plugin: posts the {@link RecipeViewerEvents} script events when the JEI
 * runtime rebuilds (resource reload), applies static script modifications, and synchronizes
 * dynamic stage-gated item and recipe visibility from {@link ClientStageMirror#SHARED}.
 *
 * <p>Timing matches KubeJS:
 * <ul>
 *   <li>{@code registerRecipes} (registration phase): {@code addInformation}, because JEI
 *       provides the {@code addIngredientInfo} API only in this phase;</li>
 *   <li>{@code onRuntimeAvailable} (runtime): adds and removes entries, hides recipes and
 *       categories, then applies current stage visibility gates;</li>
 *   <li>{@code onRuntimeUnavailable} (teardown): clears the active runtime reference and all
 *       per-session stage visibility tracking state.</li>
 * </ul>
 */
@JeiPlugin
public class RecipeViewerJeiPlugin implements IModPlugin {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NekoJS.MODID, "jei");

    private static final Object STAGE_SYNC_LOCK = new Object();
    private static volatile @Nullable IJeiRuntime activeRuntime;

    /**
     * Tracks {@link ItemStack} ingredients hidden specifically by stage gates during the current
     * {@link IJeiRuntime} session, kept separate from static {@link RecipeViewerEvents} removals.
     */
    private static final Map<NekoId, List<ItemStack>> STAGE_HIDDEN_ITEMS = new LinkedHashMap<>();

    /**
     * Tracks recipe instances hidden specifically by stage gates during the current
     * {@link IJeiRuntime} session, keyed by JEI recipe type and {@link NekoId}.
     */
    private static final Map<Object, Map<NekoId, List<Object>>> STAGE_HIDDEN_RECIPES = new LinkedHashMap<>();

    /** Retains categories with stage-hidden recipes so empty categories can still be unhidden. */
    private static final Map<Object, IRecipeCategory<?>> STAGE_HIDDEN_CATEGORIES = new LinkedHashMap<>();

    /** Tracks the set of recipe IDs already evaluated against JEI in the active runtime session. */
    private static final Set<NekoId> SYNCED_HIDDEN_RECIPE_IDS = new HashSet<>();

    /** Records item IDs statically removed by {@code RecipeViewerEvents.removeEntries} in this session. */
    private static final Set<NekoId> STATICALLY_REMOVED_ITEMS = new HashSet<>();

    static {
        ClientStageMirror.SHARED.addViewerRefreshListener(RecipeViewerJeiPlugin::syncStageVisibility);
    }

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeViewerInformationJS event = new RecipeViewerInformationJS();
        RecipeViewerEvents.ADD_INFORMATION.post(event);

        List<Object> entries = event.entries();
        List<List<String>> information = event.information();
        for (int i = 0; i < entries.size(); i++) {
            ItemStack stack = resolveItem(entries.get(i));
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Component[] lines = information.get(i).stream()
                    .map(Component::literal)
                    .toArray(Component[]::new);
            registration.addIngredientInfo(stack, VanillaTypes.ITEM_STACK, lines);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        synchronized (STAGE_SYNC_LOCK) {
            activeRuntime = null;
            clearTrackedStageState();
        }

        removeCategories(runtime.getRecipeManager());
        removeRecipes(runtime.getRecipeManager());
        removeEntries(runtime.getIngredientManager());
        addEntries(runtime.getIngredientManager());

        activeRuntime = runtime;
        syncStageVisibility();
    }

    @Override
    public void onRuntimeUnavailable() {
        synchronized (STAGE_SYNC_LOCK) {
            activeRuntime = null;
            clearTrackedStageState();
        }
    }

    /**
     * Synchronizes dynamic stage-gated item and recipe visibility in JEI against
     * {@link ClientStageMirror#SHARED}. Safe to invoke when JEI runtime is not yet available.
     */
    public static void syncStageVisibility() {
        synchronized (STAGE_SYNC_LOCK) {
            IJeiRuntime runtime = activeRuntime;
            if (runtime == null) {
                return;
            }
            syncStageItems(runtime.getIngredientManager(), ClientStageMirror.SHARED.hiddenItems());
            syncStageRecipes(runtime.getRecipeManager(), ClientStageMirror.SHARED.hiddenRecipes());
        }
    }

    private static void clearTrackedStageState() {
        STAGE_HIDDEN_ITEMS.clear();
        STAGE_HIDDEN_RECIPES.clear();
        STAGE_HIDDEN_CATEGORIES.clear();
        SYNCED_HIDDEN_RECIPE_IDS.clear();
        STATICALLY_REMOVED_ITEMS.clear();
    }

    private static void syncStageItems(IIngredientManager ingredientManager, Set<NekoId> desiredHiddenItems) {
        List<ItemStack> toRestore = new ArrayList<>();
        Iterator<Map.Entry<NekoId, List<ItemStack>>> iterator = STAGE_HIDDEN_ITEMS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<NekoId, List<ItemStack>> entry = iterator.next();
            if (!desiredHiddenItems.contains(entry.getKey())) {
                toRestore.addAll(entry.getValue());
                iterator.remove();
            }
        }
        if (!toRestore.isEmpty()) {
            ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toRestore);
        }

        Set<NekoId> newlyHiddenIds = new LinkedHashSet<>();
        for (NekoId itemId : desiredHiddenItems) {
            if (itemId != null && !STAGE_HIDDEN_ITEMS.containsKey(itemId)) {
                newlyHiddenIds.add(itemId);
            }
        }
        if (newlyHiddenIds.isEmpty()) {
            return;
        }

        Map<NekoId, List<ItemStack>> matchedById = new LinkedHashMap<>();
        Collection<ItemStack> allIngredients = ingredientManager.getAllIngredients(VanillaTypes.ITEM_STACK);
        if (allIngredients != null) {
            for (ItemStack stack : allIngredients) {
                NekoId stackId = itemIdOf(stack);
                if (stackId != null && newlyHiddenIds.contains(stackId)) {
                    matchedById.computeIfAbsent(stackId, unused -> new ArrayList<>()).add(stack);
                }
            }
        }

        List<ItemStack> toRemove = new ArrayList<>();
        for (NekoId itemId : newlyHiddenIds) {
            List<ItemStack> stacks = matchedById.get(itemId);
            if ((stacks == null || stacks.isEmpty()) && !STATICALLY_REMOVED_ITEMS.contains(itemId)) {
                Identifier nativeId = Identifier.fromNamespaceAndPath(itemId.namespace(), itemId.path());
                Item item = BuiltInRegistries.ITEM.getOptional(nativeId).orElse(null);
                if (item != null) {
                    ItemStack fallback = new ItemStack(item);
                    if (!fallback.isEmpty()) {
                        stacks = List.of(fallback);
                    }
                }
            }
            if (stacks != null && !stacks.isEmpty()) {
                List<ItemStack> recorded = List.copyOf(stacks);
                STAGE_HIDDEN_ITEMS.put(itemId, recorded);
                toRemove.addAll(recorded);
            } else {
                STAGE_HIDDEN_ITEMS.put(itemId, List.of());
            }
        }
        if (!toRemove.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toRemove);
        }
    }

    private static void syncStageRecipes(IRecipeManager recipeManager, Set<NekoId> desiredHiddenRecipeIds) {
        if (desiredHiddenRecipeIds.equals(SYNCED_HIDDEN_RECIPE_IDS)) {
            return;
        }

        boolean hasNewLocks = false;
        for (NekoId recipeId : desiredHiddenRecipeIds) {
            if (recipeId != null && !SYNCED_HIDDEN_RECIPE_IDS.contains(recipeId)) {
                hasNewLocks = true;
                break;
            }
        }

        Set<IRecipeCategory<?>> categories = new LinkedHashSet<>(STAGE_HIDDEN_CATEGORIES.values());
        if (hasNewLocks) {
            categories.addAll(recipeManager.createRecipeCategoryLookup().get().toList());
        }
        for (IRecipeCategory<?> category : categories) {
            syncCategoryStageRecipes(recipeManager, category, desiredHiddenRecipeIds);
        }

        SYNCED_HIDDEN_RECIPE_IDS.clear();
        for (NekoId recipeId : desiredHiddenRecipeIds) {
            if (recipeId != null) {
                SYNCED_HIDDEN_RECIPE_IDS.add(recipeId);
            }
        }
    }

    private static <T> void syncCategoryStageRecipes(
            IRecipeManager recipeManager,
            IRecipeCategory<T> category,
            Set<NekoId> desiredHiddenRecipeIds) {
        var recipeType = category.getRecipeType();
        Map<NekoId, List<Object>> hiddenById = STAGE_HIDDEN_RECIPES.get(recipeType);
        if (hiddenById != null && !hiddenById.isEmpty()) {
            List<T> toUnhide = new ArrayList<>();
            Iterator<Map.Entry<NekoId, List<Object>>> iterator = hiddenById.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<NekoId, List<Object>> entry = iterator.next();
                if (!desiredHiddenRecipeIds.contains(entry.getKey())) {
                    for (Object rawRecipe : entry.getValue()) {
                        @SuppressWarnings("unchecked")
                        T typedRecipe = (T) rawRecipe;
                        toUnhide.add(typedRecipe);
                    }
                    iterator.remove();
                }
            }
            if (!toUnhide.isEmpty()) {
                recipeManager.unhideRecipes(recipeType, toUnhide);
            }
            if (hiddenById.isEmpty()) {
                STAGE_HIDDEN_RECIPES.remove(recipeType);
                STAGE_HIDDEN_CATEGORIES.remove(recipeType);
                hiddenById = null;
            }
        }

        if (desiredHiddenRecipeIds.isEmpty()) {
            return;
        }

        boolean scanNeeded = false;
        for (NekoId recipeId : desiredHiddenRecipeIds) {
            if (recipeId != null && !SYNCED_HIDDEN_RECIPE_IDS.contains(recipeId)) {
                scanNeeded = true;
                break;
            }
        }
        if (!scanNeeded) {
            return;
        }

        IRecipeLookup<T> lookup = recipeManager.createRecipeLookup(recipeType);
        List<T> toHide = new ArrayList<>();
        for (T recipe : lookup.get().toList()) {
//? if >=26 {
            Identifier id = category.getIdentifier(recipe);
//?} else {
            /*Identifier id = category.getRegistryName(recipe);
*///?}
            if (id == null || id.getNamespace().isBlank() || id.getPath().isBlank()) {
                continue;
            }
            NekoId nekoId = NekoId.of(id.getNamespace(), id.getPath());
            if (!desiredHiddenRecipeIds.contains(nekoId)) {
                continue;
            }
            if (hiddenById == null) {
                hiddenById = new LinkedHashMap<>();
                STAGE_HIDDEN_RECIPES.put(recipeType, hiddenById);
                STAGE_HIDDEN_CATEGORIES.put(recipeType, category);
            }
            List<Object> bucket = hiddenById.computeIfAbsent(nekoId, unused -> new ArrayList<>());
            if (!bucket.contains(recipe)) {
                bucket.add(recipe);
                toHide.add(recipe);
            }
        }
        if (!toHide.isEmpty()) {
            recipeManager.hideRecipes(recipeType, toHide);
        }
    }

    private void removeCategories(IRecipeManager recipeManager) {
        RecipeViewerCategoryListJS event = new RecipeViewerCategoryListJS();
        RecipeViewerEvents.REMOVE_CATEGORIES.post(event);
        if (event.categories().isEmpty()) {
            return;
        }

        Set<Identifier> ids = new HashSet<>();
        for (Object category : event.categories()) {
            ids.add(Identifier.parse(String.valueOf(category)));
        }

        for (IRecipeCategory<?> category : recipeManager.createRecipeCategoryLookup().get().toList()) {
            if (ids.contains(category.getRecipeType().getUid())) {
                recipeManager.hideRecipeCategory(category.getRecipeType());
            }
        }
    }

    private void removeRecipes(IRecipeManager recipeManager) {
        RecipeViewerRecipeListJS event = new RecipeViewerRecipeListJS();
        RecipeViewerEvents.REMOVE_RECIPES.post(event);
        if (event.recipes().isEmpty()) {
            return;
        }

        Set<Identifier> ids = new HashSet<>();
        for (Object recipe : event.recipes()) {
            ids.add(Identifier.parse(String.valueOf(recipe)));
        }
        Identifier categoryId = event.category() == null ? null : Identifier.parse(String.valueOf(event.category()));

        for (IRecipeCategory<?> category : recipeManager.createRecipeCategoryLookup().get().toList()) {
            if (categoryId != null && !categoryId.equals(category.getRecipeType().getUid())) {
                continue;
            }
            hideRecipesFromCategory(recipeManager, category, ids);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void hideRecipesFromCategory(IRecipeManager recipeManager, IRecipeCategory category, Set<Identifier> ids) {
        IRecipeLookup lookup = recipeManager.createRecipeLookup(category.getRecipeType());
        List toHide = new ArrayList();
        for (Object recipe : lookup.get().toList()) {
//? if >=26 {
            Identifier id = category.getIdentifier(recipe);
//?} else {
            /*Identifier id = category.getRegistryName(recipe);
*///?}
            if (id != null && ids.contains(id)) {
                toHide.add(recipe);
            }
        }
        if (!toHide.isEmpty()) {
            recipeManager.hideRecipes(category.getRecipeType(), toHide);
        }
    }

    private void removeEntries(IIngredientManager ingredientManager) {
        applyEntries(ingredientManager, true);
    }

    private void addEntries(IIngredientManager ingredientManager) {
        applyEntries(ingredientManager, false);
    }

    private static void applyEntries(IIngredientManager ingredientManager, boolean remove) {
        RecipeViewerEntryListJS event = new RecipeViewerEntryListJS("item");
        if (remove) {
            RecipeViewerEvents.REMOVE_ENTRIES.post(event, "item");
        } else {
            RecipeViewerEvents.ADD_ENTRIES.post(event, "item");
        }
        List<ItemStack> stacks = new ArrayList<>();
        synchronized (STAGE_SYNC_LOCK) {
            for (Object entry : event.entries()) {
                ItemStack stack = resolveItem(entry);
                if (stack != null && !stack.isEmpty()) {
                    stacks.add(stack);
                    NekoId itemId = itemIdOf(stack);
                    if (itemId != null) {
                        if (remove) {
                            STATICALLY_REMOVED_ITEMS.add(itemId);
                        } else {
                            STATICALLY_REMOVED_ITEMS.remove(itemId);
                        }
                    }
                }
            }
        }
        if (!stacks.isEmpty()) {
            if (remove) {
                ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
            } else {
                ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
            }
        }

        RecipeViewerEntryListJS fluidEvent = new RecipeViewerEntryListJS("fluid");
        if (remove) {
            RecipeViewerEvents.REMOVE_ENTRIES.post(fluidEvent, "fluid");
        } else {
            RecipeViewerEvents.ADD_ENTRIES.post(fluidEvent, "fluid");
        }
        List<FluidStack> fluids = new ArrayList<>();
        for (Object entry : fluidEvent.entries()) {
            FluidStack stack = resolveFluid(entry);
            if (stack != null && !stack.isEmpty()) {
                fluids.add(stack);
            }
        }
        if (!fluids.isEmpty()) {
            if (remove) {
                ingredientManager.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluids);
            } else {
                ingredientManager.addIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluids);
            }
        }
    }

    private static @Nullable NekoId itemIdOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || key.getNamespace().isBlank() || key.getPath().isBlank()) {
            return null;
        }
        return NekoId.of(key.getNamespace(), key.getPath());
    }

    /** Resolves a script entry into an ItemStack (id string / ItemStack / Item; FluidStack is ignored). */
    private static ItemStack resolveItem(Object entry) {
        if (entry == null) {
            return null;
        }
        if (entry instanceof ItemStack stack) {
            return stack;
        }
        if (entry instanceof Item item) {
            return new ItemStack(item);
        }
        if (entry instanceof String id) {
            return ItemStackAdapter.stringToItemStack(id);
        }
        com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.CLIENT).warn("Unsupported item entry type: {}", entry.getClass().getName());
        return null;
    }

    /** Resolves a script entry into a FluidStack (id string / FluidStack). */
    private static FluidStack resolveFluid(Object entry) {
        if (entry == null) {
            return null;
        }
        if (entry instanceof FluidStack stack) {
            return stack;
        }
        if (entry instanceof String id) {
            return FluidResolver.stackFromString(id);
        }
        com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.CLIENT).warn("Unsupported fluid entry type: {}", entry.getClass().getName());
        return null;
    }
}
//?}
