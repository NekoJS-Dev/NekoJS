package com.tkisor.nekojs.api.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * JSX UI 的公开静态事实视图（managed contract 的 {@code global:Ui}）。
 *
 * <p>只暴露登记表快照：primitive 标签、props 名、事件名与三个类型清单。唯一登记表的
 * 权威副本在 {@code com.tkisor.nekojs.core.ui.UiPrimitiveRegistry} 与 {@link UiProp}；
 * 本类方法按 Java 端登记表迭代产出，是派生视图，不是第二事实源。
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

    /** 全部受控属性名（含事件属性）。 */
    public static List<String> propNames() {
        List<String> names = new ArrayList<>();
        for (UiProp prop : UiProp.values()) {
            names.add(prop.jsName());
        }
        return List.copyOf(names);
    }

    /** 全部受控事件属性名（lowerCamelCase）。 */
    public static List<String> eventNames() {
        List<String> names = new ArrayList<>();
        for (UiProp prop : UiProp.values()) {
            if (prop.isEvent()) names.add(prop.jsName());
        }
        return List.copyOf(names);
    }

    /** 全部稳定脚本事件类型名。 */
    public static List<String> eventTypeNames() {
        List<String> names = new ArrayList<>();
        for (UiEventType type : UiEventType.values()) {
            names.add(type.jsName());
        }
        return List.copyOf(names);
    }

    /** props 受控值类型清单。 */
    public static List<String> propValueKinds() {
        return List.of("primitive", "resource", "event");
    }

    /** UI capability id 清单（与契约 capabilities 同源派生）。 */
    public static List<String> capabilityIds() {
        return UiSpec.CAPABILITY_IDS;
    }
}
