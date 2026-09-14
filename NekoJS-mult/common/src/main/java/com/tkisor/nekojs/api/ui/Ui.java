package com.tkisor.nekojs.api.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * JSX UI 的公开静态事实视图（managed contract 的 {@code global:Ui}）。
 *
 * <p>只暴露登记表快照的一个派生视图：primitive 标签。唯一登记表的权威副本在
 * {@link UiProp}（属性词表与取值种类）与引擎侧 primitive 登记表（primitive 到允许属性的
 * 映射）；本类按 Java 端登记表迭代产出，不是第二事实源。
 *
 * <p>本类<b>不</b>持有 UI 专用 global singleton 或静态跨 generation registry：
 * 每次调用都重新计算并返回不可变列表（AC7）。
 */
public final class Ui {

    private Ui() {
    }

    /** 初始 primitive 集合的稳定标签（登记表快照）。 */
    public static List<String> primitives() {
        List<String> tags = new ArrayList<>();
        for (UiPrimitive primitive : UiPrimitive.values()) {
            tags.add(primitive.tag());
        }
        return List.copyOf(tags);
    }
}
