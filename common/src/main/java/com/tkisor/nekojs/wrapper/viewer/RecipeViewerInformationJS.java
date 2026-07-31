package com.tkisor.nekojs.wrapper.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 条目信息（tooltip）事件的对象（{@code RecipeViewerEvents.addInformation}）。
 *
 * <p>脚本通过 {@code event.add(entry, 'text')} 或 {@code event.addAll(entry, ...)}
 * 为条目附加查看器 tooltip 信息；平台层在 JEI 注册期（{@code registerRecipes}）
 * 应用，因此信息在每次资源 reload 后更新。
 */
public final class RecipeViewerInformationJS {
    private final List<Object> entries = new ArrayList<>();
    private final List<List<String>> information = new ArrayList<>();

    /** 为条目添加一行 tooltip。 */
    public void add(Object entry, String info) {
        addAll(entry, info);
    }

    /** 为条目添加多行 tooltip（可变参数，可传单个字符串或字符串数组）。 */
    public void addAll(Object entry, String... info) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(info, "info");
        List<String> lines = new ArrayList<>();
        for (String line : info) {
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
        }
        if (!lines.isEmpty()) {
            entries.add(entry);
            information.add(List.copyOf(lines));
        }
    }

    /** 已收集的条目（与 {@link #information()} 平行，只读视图）。 */
    public List<Object> entries() {
        return List.copyOf(entries);
    }

    /** 每条目对应的 tooltip 行（与 {@link #entries()} 平行，只读视图）。 */
    public List<List<String>> information() {
        return information.stream().map(List::copyOf).toList();
    }
}
