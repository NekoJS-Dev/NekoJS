package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptTypePredicate;

public record EventCatalogEntry(
        String group,
        String name,
        ScriptTypePredicate scriptType,
        Class<?> eventType,
        Class<?> dispatchKeyType,
        boolean cancellable,
        boolean dispatchable,
        String snippet
) {
    public static EventCatalogEntry of(
            String group,
            String name,
            ScriptTypePredicate scriptType,
            Class<?> eventType,
            Class<?> dispatchKeyType,
            boolean cancellable,
            boolean dispatchable
    ) {
        return new EventCatalogEntry(group, name, scriptType, eventType, dispatchKeyType, cancellable, dispatchable, group + "." + name + "(event => {\n  $0\n})");
    }

    /**
     * {@code ScriptEvents} 声明的自定义事件：载荷是脚本自己传进来的值，没有事件类
     * （{@code eventType == null}，probe 渲染成 {@code any}），并且脚本可以自己 post。
     */
    public static EventCatalogEntry ofScriptEvent(String group, String name, ScriptTypePredicate scriptType) {
        return new EventCatalogEntry(group, name, scriptType, null, null, false, false,
                group + "." + name + "(payload => {\n  $0\n})");
    }

    /** 是否为脚本声明的自定义事件（无事件类；probe 需要额外渲染 {@code post}）。 */
    public boolean scriptDefined() {
        return eventType == null;
    }
}
