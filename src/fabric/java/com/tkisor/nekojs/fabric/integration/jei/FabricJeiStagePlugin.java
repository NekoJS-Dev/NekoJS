package com.tkisor.nekojs.fabric.integration.jei;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.core.stage.ClientStageMirror;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

@JeiPlugin
public final class FabricJeiStagePlugin implements IModPlugin {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(NekoJS.MODID, "fabric_jei_stage_visibility");
    private static final Object SYNC_LOCK = new Object();
    private static final Map<NekoId, List<ItemStack>> HIDDEN_ITEMS = new LinkedHashMap<>();
    private static final Map<Object, Map<NekoId, List<Object>>> HIDDEN_RECIPES = new LinkedHashMap<>();
    private static final Set<NekoId> SYNCED_RECIPE_IDS = new HashSet<>();
    private static volatile @Nullable IJeiRuntime activeRuntime;

    static {
        ClientStageMirror.SHARED.addViewerRefreshListener(FabricJeiStagePlugin::syncVisibility);
    }

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        synchronized (SYNC_LOCK) {
            activeRuntime = null;
            clearTracking();
            activeRuntime = runtime;
            syncVisibilityLocked(runtime);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        synchronized (SYNC_LOCK) {
            activeRuntime = null;
            clearTracking();
        }
    }

    private static void syncVisibility() {
        synchronized (SYNC_LOCK) {
            IJeiRuntime runtime = activeRuntime;
            if (runtime != null) {
                syncVisibilityLocked(runtime);
            }
        }
    }

    private static void syncVisibilityLocked(IJeiRuntime runtime) {
        syncItems(runtime.getIngredientManager(), ClientStageMirror.SHARED.hiddenItems());
        syncRecipes(runtime.getRecipeManager(), ClientStageMirror.SHARED.hiddenRecipes());
    }

    private static void clearTracking() {
        HIDDEN_ITEMS.clear();
        HIDDEN_RECIPES.clear();
        SYNCED_RECIPE_IDS.clear();
    }

    private static void syncItems(IIngredientManager ingredients, Set<NekoId> desired) {
        List<ItemStack> restore = new ArrayList<>();
        Iterator<Map.Entry<NekoId, List<ItemStack>>> iterator = HIDDEN_ITEMS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<NekoId, List<ItemStack>> entry = iterator.next();
            if (!desired.contains(entry.getKey())) {
                restore.addAll(entry.getValue());
                iterator.remove();
            }
        }
        if (!restore.isEmpty()) {
            ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, restore);
        }

        Set<NekoId> newlyHidden = new LinkedHashSet<>();
        for (NekoId itemId : desired) {
            if (itemId != null && !HIDDEN_ITEMS.containsKey(itemId)) {
                newlyHidden.add(itemId);
            }
        }
        if (newlyHidden.isEmpty()) {
            return;
        }

        Map<NekoId, List<ItemStack>> matched = new LinkedHashMap<>();
        Collection<ItemStack> all = ingredients.getAllIngredients(VanillaTypes.ITEM_STACK);
        if (all != null) {
            for (ItemStack stack : all) {
                NekoId itemId = itemIdOf(stack);
                if (itemId != null && newlyHidden.contains(itemId)) {
                    matched.computeIfAbsent(itemId, unused -> new ArrayList<>()).add(stack);
                }
            }
        }

        List<ItemStack> remove = new ArrayList<>();
        for (NekoId itemId : newlyHidden) {
            List<ItemStack> stacks = matched.get(itemId);
            if (stacks == null || stacks.isEmpty()) {
                Identifier id = Identifier.fromNamespaceAndPath(itemId.namespace(), itemId.path());
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) {
                    ItemStack fallback = new ItemStack(item);
                    if (!fallback.isEmpty()) {
                        stacks = List.of(fallback);
                    }
                }
            }
            List<ItemStack> recorded = stacks == null || stacks.isEmpty() ? List.of() : List.copyOf(stacks);
            HIDDEN_ITEMS.put(itemId, recorded);
            remove.addAll(recorded);
        }
        if (!remove.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, remove);
        }
    }

    private static void syncRecipes(IRecipeManager recipes, Set<NekoId> desired) {
        if (desired.equals(SYNCED_RECIPE_IDS)) {
            return;
        }

        boolean hasNewLocks = false;
        for (NekoId recipeId : desired) {
            if (recipeId != null && !SYNCED_RECIPE_IDS.contains(recipeId)) {
                hasNewLocks = true;
                break;
            }
        }
        Set<IRecipeCategory<?>> categories = new LinkedHashSet<>();
        for (Object rawCategory : HIDDEN_RECIPES.keySet()) {
            @SuppressWarnings("unchecked")
            IRecipeType<Object> type = (IRecipeType<Object>) rawCategory;
            IRecipeCategory<Object> category = recipes.getRecipeCategory(type);
            if (category != null) {
                categories.add(category);
            }
        }
        if (hasNewLocks) {
            categories.addAll(recipes.createRecipeCategoryLookup().get().toList());
        }
        for (IRecipeCategory<?> category : categories) {
            syncCategoryRecipes(recipes, category, desired);
        }

        SYNCED_RECIPE_IDS.clear();
        for (NekoId recipeId : desired) {
            if (recipeId != null) {
                SYNCED_RECIPE_IDS.add(recipeId);
            }
        }
    }

    private static <T> void syncCategoryRecipes(
            IRecipeManager recipes,
            IRecipeCategory<T> category,
            Set<NekoId> desired) {
        IRecipeType<T> type = category.getRecipeType();
        Map<NekoId, List<Object>> hidden = HIDDEN_RECIPES.get(type);
        if (hidden != null && !hidden.isEmpty()) {
            List<T> restore = new ArrayList<>();
            Iterator<Map.Entry<NekoId, List<Object>>> iterator = hidden.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<NekoId, List<Object>> entry = iterator.next();
                if (!desired.contains(entry.getKey())) {
                    for (Object raw : entry.getValue()) {
                        @SuppressWarnings("unchecked")
                        T recipe = (T) raw;
                        restore.add(recipe);
                    }
                    iterator.remove();
                }
            }
            if (!restore.isEmpty()) {
                recipes.unhideRecipes(type, restore);
            }
            if (hidden.isEmpty()) {
                HIDDEN_RECIPES.remove(type);
                hidden = null;
            }
        }
        if (desired.isEmpty()) {
            return;
        }

        boolean scanNeeded = false;
        for (NekoId recipeId : desired) {
            if (recipeId != null && !SYNCED_RECIPE_IDS.contains(recipeId)) {
                scanNeeded = true;
                break;
            }
        }
        if (!scanNeeded) {
            return;
        }

        IRecipeLookup<T> lookup = recipes.createRecipeLookup(type);
        List<T> remove = new ArrayList<>();
        for (T recipe : lookup.get().toList()) {
            Identifier id = category.getIdentifier(recipe);
            if (id == null || id.getNamespace().isBlank() || id.getPath().isBlank()) {
                continue;
            }
            NekoId recipeId = NekoId.of(id.getNamespace(), id.getPath());
            if (!desired.contains(recipeId)) {
                continue;
            }
            if (hidden == null) {
                hidden = new LinkedHashMap<>();
                HIDDEN_RECIPES.put(type, hidden);
            }
            List<Object> bucket = hidden.computeIfAbsent(recipeId, unused -> new ArrayList<>());
            if (!bucket.contains(recipe)) {
                bucket.add(recipe);
                remove.add(recipe);
            }
        }
        if (!remove.isEmpty()) {
            recipes.hideRecipes(type, remove);
        }
    }

    private static @Nullable NekoId itemIdOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || id.getNamespace().isBlank() || id.getPath().isBlank()) {
            return null;
        }
        return NekoId.of(id.getNamespace(), id.getPath());
    }
}
