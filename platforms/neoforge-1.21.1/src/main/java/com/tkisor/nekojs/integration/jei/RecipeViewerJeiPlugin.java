package com.tkisor.nekojs.integration.jei;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.RecipeViewerEvents;
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
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JEI 集成插件：在 JEI 运行时重建（资源 reload）时触发
 * {@link RecipeViewerEvents} 脚本事件，并把结果应用到 JEI。
 *
 * <p>应用时机与 KubeJS 对齐：
 * <ul>
 *   <li>{@code registerRecipes}（注册期）：{@code addInformation} —— JEI 仅此阶段
 *       提供 {@code addIngredientInfo} API；</li>
 *   <li>{@code onRuntimeAvailable}（运行时）：条目增删、配方/类别隐藏。</li>
 * </ul>
 */
@JeiPlugin
public class RecipeViewerJeiPlugin implements IModPlugin {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(NekoJS.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
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
        removeCategories(runtime.getRecipeManager());
        removeRecipes(runtime.getRecipeManager());
        removeEntries(runtime.getIngredientManager());
        addEntries(runtime.getIngredientManager());
    }

    private void removeCategories(IRecipeManager recipeManager) {
        RecipeViewerCategoryListJS event = new RecipeViewerCategoryListJS();
        RecipeViewerEvents.REMOVE_CATEGORIES.post(event);
        if (event.categories().isEmpty()) {
            return;
        }

        Set<ResourceLocation> ids = new HashSet<>();
        for (Object category : event.categories()) {
            ids.add(ResourceLocation.parse(String.valueOf(category)));
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

        Set<ResourceLocation> ids = new HashSet<>();
        for (Object recipe : event.recipes()) {
            ids.add(ResourceLocation.parse(String.valueOf(recipe)));
        }
        ResourceLocation categoryId = event.category() == null ? null : ResourceLocation.parse(String.valueOf(event.category()));

        for (IRecipeCategory<?> category : recipeManager.createRecipeCategoryLookup().get().toList()) {
            if (categoryId != null && !categoryId.equals(category.getRecipeType().getUid())) {
                continue;
            }
            hideRecipesFromCategory(recipeManager, category, ids);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void hideRecipesFromCategory(IRecipeManager recipeManager, IRecipeCategory category, Set<ResourceLocation> ids) {
        IRecipeLookup lookup = recipeManager.createRecipeLookup(category.getRecipeType());
        List toHide = new ArrayList();
        for (Object recipe : lookup.get().toList()) {
            ResourceLocation id = category.getRegistryName(recipe);
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
        for (Object entry : event.entries()) {
            ItemStack stack = resolveItem(entry);
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
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

    /** 把脚本条目解析为 ItemStack（id 字符串 / ItemStack / Item / FluidStack 忽略）。 */
    private static ItemStack resolveItem(Object entry) {
        if (entry instanceof ItemStack stack) {
            return stack;
        }
        if (entry instanceof Item item) {
            return new ItemStack(item);
        }
        if (entry instanceof String id) {
            return ItemStackAdapter.stringToItemStack(id);
        }
        ScriptType.CLIENT.logger().warn("Unsupported item entry type: {}", entry.getClass().getName());
        return null;
    }

    /** 把脚本条目解析为 FluidStack（id 字符串 / FluidStack）。 */
    private static FluidStack resolveFluid(Object entry) {
        if (entry instanceof FluidStack stack) {
            return stack;
        }
        if (entry instanceof String id) {
            return FluidResolver.stackFromString(id);
        }
        ScriptType.CLIENT.logger().warn("Unsupported fluid entry type: {}", entry.getClass().getName());
        return null;
    }
}
