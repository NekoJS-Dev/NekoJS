package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.NekoBindings;
import com.tkisor.nekojs.api.data.NekoJSTypeAdapters;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.script.ScriptType;

import java.util.*;

/**
 * NekoJS 上下文全景快照。
 * <p>
 * 外部 mod（如 .d.ts 生成器）可通过此类获取特定 {@link ScriptType} 下
 * JS 沙盒中所有可用的全局绑定、事件组、配方命名空间、类型适配器目标类等信息。
 * </p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * NekoContextSnapshot snapshot = NekoContextSnapshot.of(ScriptType.SERVER);
 * Map<String, Binding> bindings = snapshot.bindings();
 * Map<String, EventGroup> events = snapshot.eventGroups();
 * Set<String> recipeNamespaces = snapshot.recipeNamespaces();
 * Set<Class<?>> adapterTargets = snapshot.typeAdapterTargets();
 * }</pre>
 *
 * @since 1.1
 */
public final class NekoContextSnapshot {

    private final ScriptType scriptType;
    private final Map<String, Binding> bindings;
    private final Map<String, EventGroup> eventGroups;
    private final Set<String> recipeNamespaces;
    private final Set<Class<?>> typeAdapterTargets;

    private NekoContextSnapshot(
            ScriptType scriptType,
            Map<String, Binding> bindings,
            Map<String, EventGroup> eventGroups,
            Set<String> recipeNamespaces,
            Set<Class<?>> typeAdapterTargets
    ) {
        this.scriptType = scriptType;
        this.bindings = Collections.unmodifiableMap(bindings);
        this.eventGroups = Collections.unmodifiableMap(eventGroups);
        this.recipeNamespaces = Collections.unmodifiableSet(recipeNamespaces);
        this.typeAdapterTargets = Collections.unmodifiableSet(typeAdapterTargets);
    }

    /**
     * 获取指定 {@link ScriptType} 的上下文快照。
     */
    public static NekoContextSnapshot of(ScriptType scriptType) {
        // 1. 全局绑定
        Map<String, Binding> bindings = NekoBindings.getFor(scriptType);

        // 2. 事件组（只保留对该 ScriptType 可用的事件总线）
        Map<String, EventGroup> rawGroups = NekoEventGroups.all();
        Map<String, EventGroup> filteredGroups = new LinkedHashMap<>();
        for (Map.Entry<String, EventGroup> entry : rawGroups.entrySet()) {
            EventGroup group = entry.getValue();
            // 检查该组内是否至少有一个总线适用于目标 ScriptType
            Map<String, EventGroup.BusHolder> buses = group.viewBuses();
            boolean hasApplicableBus = false;
            for (EventGroup.BusHolder holder : buses.values()) {
                if (holder.canApplyOn(scriptType)) {
                    hasApplicableBus = true;
                    break;
                }
            }
            if (hasApplicableBus) {
                filteredGroups.put(entry.getKey(), group);
            }
        }

        // 3. 配方命名空间
        Set<String> recipeNamespaces = NekoRecipeNamespaces.getNamespaces();

        // 4. 类型适配器目标类
        Set<Class<?>> adapterTargets = new LinkedHashSet<>();
        for (JSTypeAdapter<?> adapter : NekoJSTypeAdapters.all()) {
            adapterTargets.add(adapter.getTargetClass());
        }

        return new NekoContextSnapshot(scriptType, bindings, filteredGroups, recipeNamespaces, adapterTargets);
    }

    /** @return 此快照所属的脚本类型 */
    public ScriptType scriptType() {
        return scriptType;
    }

    /**
     * 全局绑定表。
     * key 为 JS 侧变量名，value 为 {@link Binding}（包含对应 Java 类型）。
     */
    public Map<String, Binding> bindings() {
        return bindings;
    }

    /**
     * 事件组表（仅含适用于此 ScriptType 的事件）。
     * key 为事件组名（如 {@code "ServerEvents"}），value 为 {@link EventGroup}。
     */
    public Map<String, EventGroup> eventGroups() {
        return eventGroups;
    }

    /**
     * 配方命名空间集合。
     * 例如 {@code {"minecraft"}} —— 对应 JS 中的 {@code event.recipes.minecraft}。
     */
    public Set<String> recipeNamespaces() {
        return recipeNamespaces;
    }

    /**
     * 类型适配器目标 Java 类集合。
     * <p>
     * 这些类在 JS 与 Java 间传递时会自动转换，
     * 生成 .d.ts 时可为其生成对应的 TS 接口。
     * </p>
     */
    public Set<Class<?>> typeAdapterTargets() {
        return typeAdapterTargets;
    }
}