package com.tkisor.nekojs.wrapper.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 配方移除事件的对象（{@code RecipeViewerEvents.removeRecipes}）。
 *
 * <p>脚本通过 {@code event.remove('minecraft:xxx')} 按配方 id 收集要隐藏的
 * 配方；可选 {@code removeFromCategory(category, recipe)} 定向到具体类别
 * （如 {@code 'minecraft:crafting'}）。平台层在事件结束后按类别查配方并隐藏。
 */
public final class RecipeViewerRecipeListJS {
    private Object category;
    private final List<Object> recipes = new ArrayList<>();

    /** 从所有类别移除指定配方。 */
    public void remove(Object recipe) {
        recipes.add(Objects.requireNonNull(recipe, "recipe"));
    }

    /** 仅从指定类别移除配方（类别为查看器类别 id，如 {@code 'minecraft:crafting'}）。 */
    public void removeFromCategory(Object category, Object recipe) {
        this.category = Objects.requireNonNull(category, "category");
        recipes.add(Objects.requireNonNull(recipe, "recipe"));
    }

    /** 定向类别；未指定时为 null。 */
    public Object category() {
        return category;
    }

    /** 已收集的配方（只读视图）。 */
    public List<Object> recipes() {
        return List.copyOf(recipes);
    }
}
