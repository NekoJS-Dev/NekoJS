package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerCategoryListJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerEntryListJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerInformationJS;
import com.tkisor.nekojs.wrapper.viewer.RecipeViewerRecipeListJS;

/**
 * 配方查看器集成事件（JEI）。脚本在 CLIENT 脚本中监听，事件由
 * {@code RecipeViewerJeiPlugin} 在 JEI 运行时重建（资源 reload）时触发。
 *
 * <p>与 KubeJS 的 RecipeViewerEvents 对齐（裁剪）：条目事件按类型定向
 * （{@code 'item'} / {@code 'fluid'}），配方/类别按 id 定向。
 */
public interface RecipeViewerEvents {
    EventGroup GROUP = EventGroup.of("RecipeViewerEvents");

    /** 条目类型 key：脚本以 {@code RecipeViewerEvents.addEntries('item', ...)} 定向。 */
    DispatchKey<RecipeViewerEntryListJS, String> ENTRY_TYPE_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(RecipeViewerEntryListJS event) {
            return event.getType();
        }
    };

    /** 向查看器添加条目（脚本传物品/流体 id 或对象）。 */
    EventBusJS<RecipeViewerEntryListJS, String> ADD_ENTRIES =
            GROUP.client("addEntries", RecipeViewerEntryListJS.class, ENTRY_TYPE_KEY);

    /** 从查看器移除条目（非彻底移除，仍可由其他途径查看）。 */
    EventBusJS<RecipeViewerEntryListJS, String> REMOVE_ENTRIES =
            GROUP.client("removeEntries", RecipeViewerEntryListJS.class, ENTRY_TYPE_KEY);

    /** 按配方 id 隐藏配方（可定向类别）。 */
    EventBusJS<RecipeViewerRecipeListJS, Void> REMOVE_RECIPES =
            GROUP.client("removeRecipes", RecipeViewerRecipeListJS.class);

    /** 按类别 id 隐藏整个查看器类别。 */
    EventBusJS<RecipeViewerCategoryListJS, Void> REMOVE_CATEGORIES =
            GROUP.client("removeCategories", RecipeViewerCategoryListJS.class);

    /** 为条目附加 tooltip 信息（JEI 注册期应用，资源 reload 后更新）。 */
    EventBusJS<RecipeViewerInformationJS, Void> ADD_INFORMATION =
            GROUP.client("addInformation", RecipeViewerInformationJS.class);
}
