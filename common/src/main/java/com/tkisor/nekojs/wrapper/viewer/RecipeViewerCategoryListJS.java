package com.tkisor.nekojs.wrapper.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 类别移除事件的对象（{@code RecipeViewerEvents.removeCategories}）。
 *
 * <p>脚本通过 {@code event.remove('minecraft:crafting')} 按类别 id 收集要隐藏
 * 的查看器类别；平台层在事件结束后隐藏对应类别。
 */
public final class RecipeViewerCategoryListJS {
    private final List<Object> categories = new ArrayList<>();

    /** 移除指定类别（类别 id，如 {@code 'minecraft:crafting'}）。 */
    public void remove(Object category) {
        categories.add(Objects.requireNonNull(category, "category"));
    }

    /** 已收集的类别（只读视图）。 */
    public List<Object> categories() {
        return List.copyOf(categories);
    }
}
