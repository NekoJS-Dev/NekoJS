package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 平台无关 builder 仓库（ADR-0004「先攒后建」的攒侧）：
 * 脚本收集期只收 {@link RegistryObjectBuilder} 与连带派生条目，对象创建推迟到
 * 平台 adapter 在各注册表 pass 的抽干期（以 Supplier 注册）。
 *
 * <p>对象层冲突 fail-fast（ADR-0004 决策 5）：同注册表同 id 重复注册，
 * 不论主对象之间、主对象与派生条目之间还是派生条目之间，收集期即抛
 * {@link IllegalStateException}。
 */
public final class RegistryRepository {

    /** 连带派生条目：目标注册表 + 对象 id + 懒构建器 + 来源对象 id（诊断用）。 */
    public record Additional(
            ResourceKey<? extends Registry<?>> registry,
            Identifier id,
            Supplier<?> supplier,
            Identifier source) {}

    private final Map<ResourceKey<? extends Registry<?>>, LinkedHashMap<Identifier, RegistryObjectBuilder<?>>> byRegistry =
            new LinkedHashMap<>();
    private final Map<ResourceKey<? extends Registry<?>>, List<Additional>> additionalByRegistry = new LinkedHashMap<>();

    /** 收一条主对象 builder（保持收集序）。同注册表同 id 重复抛 {@link IllegalStateException}。 */
    public void add(ResourceKey<? extends Registry<?>> registry, RegistryObjectBuilder<?> builder) {
        LinkedHashMap<Identifier, RegistryObjectBuilder<?>> builders =
                byRegistry.computeIfAbsent(registry, key -> new LinkedHashMap<>());
        RegistryObjectBuilder<?> previous = builders.putIfAbsent(builder.id, builder);
        if (previous != null) {
            throw new IllegalStateException("Duplicate registration '" + builder.id + "' in registry '" + name(registry) + "'");
        }
    }

    /** 抽干某注册表的全部主对象 builder（移除并返回，保持收集序）。 */
    public List<RegistryObjectBuilder<?>> drain(ResourceKey<? extends Registry<?>> registry) {
        LinkedHashMap<Identifier, RegistryObjectBuilder<?>> builders = byRegistry.remove(registry);
        return builders == null ? List.of() : List.copyOf(builders.values());
    }

    /** 收一条连带派生条目（builder 的 {@code handleAdditionalObjects} 回调投递）。冲突 fail-fast。 */
    public void addAdditional(
            ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier, Identifier source) {
        LinkedHashMap<Identifier, RegistryObjectBuilder<?>> builders = byRegistry.get(registry);
        if (builders != null && builders.containsKey(id)) {
            throw new IllegalStateException("Duplicate registration '" + id + "' in registry '" + name(registry)
                    + "' (additional object of '" + source + "' collides with a main entry)");
        }
        List<Additional> list = additionalByRegistry.computeIfAbsent(registry, key -> new ArrayList<>());
        if (list.stream().anyMatch(additional -> additional.id().equals(id))) {
            throw new IllegalStateException("Duplicate registration '" + id + "' in registry '" + name(registry)
                    + "' (additional object of '" + source + "' collides with another additional object)");
        }
        list.add(new Additional(registry, id, supplier, source));
    }

    /** 抽干某注册表的全部连带派生条目（保持投递序）。 */
    public List<Additional> drainAdditional(ResourceKey<? extends Registry<?>> registry) {
        List<Additional> list = additionalByRegistry.remove(registry);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 尚未被任何 pass 抽干的主对象（load-complete 诊断用：注册表名写错 / loader 未触发该 pass）。 */
    public Map<ResourceKey<? extends Registry<?>>, List<RegistryObjectBuilder<?>>> undrained() {
        Map<ResourceKey<? extends Registry<?>>, List<RegistryObjectBuilder<?>>> copy = new LinkedHashMap<>();
        byRegistry.forEach((registry, builders) -> copy.put(registry, List.copyOf(builders.values())));
        return copy;
    }

    /** 尚未投递的连带派生条目（load-complete 诊断用：目标注册表 pass 先于来源注册表）。 */
    public Map<ResourceKey<? extends Registry<?>>, List<Additional>> undeliveredAdditional() {
        Map<ResourceKey<? extends Registry<?>>, List<Additional>> copy = new LinkedHashMap<>();
        additionalByRegistry.forEach((registry, list) -> copy.put(registry, List.copyOf(list)));
        return copy;
    }

    /** 主对象与派生条目是否都已清空。 */
    public boolean isEmpty() {
        return byRegistry.isEmpty() && additionalByRegistry.isEmpty();
    }

    private static String name(ResourceKey<? extends Registry<?>> registry) {
        return registry.identifier().toString();
    }
}
