package com.tkisor.nekojs.wrapper.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 配方查看器条目事件的对象（{@code RecipeViewerEvents.addEntries} /
 * {@code removeEntries}）。
 *
 * <p>脚本按条目类型定向（{@code 'item'} / {@code 'fluid'}），通过
 * {@code event.add(...)} 收集条目（物品 id 字符串、ItemStack 等，由平台层
 * 解析为查看器可识别的对象），事件结束后平台层一次性应用到查看器运行时。
 */
public final class RecipeViewerEntryListJS {
    private final String type;
    private final List<Object> entries = new ArrayList<>();

    public RecipeViewerEntryListJS(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /** 条目类型（{@code 'item'} / {@code 'fluid'}）。 */
    public String getType() {
        return type;
    }

    /** 添加条目（物品 id 字符串 / ItemStack 等）。 */
    public void add(Object entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
    }

    /** 已收集的条目（只读视图）。 */
    public List<Object> entries() {
        return List.copyOf(entries);
    }
}
