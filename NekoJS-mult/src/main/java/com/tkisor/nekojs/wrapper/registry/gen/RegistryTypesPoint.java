package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.plugin.MergePolicy;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 内置扩展点 {@code nekojs:registry_types}（ADR-0004 决策 4）：
 * 按注册表分组的命名 builder 类型 + 每注册表一个 default 类型。
 *
 * <p><b>V2 依赖语义用例②</b>：本点 {@code dependsOn(nekojs:registry_infos)}（时序），
 * initializer 里读取其产物（数据依赖免声明）供类型登记校验。
 *
 * <p>类型层冲突 merge = {@link MergePolicy#overrideWarn}（内置类型可被第三方覆盖 + warn，
 * addon 生态惯例）；脚本糖方法（{@code event.item(id, cb)}）由 default 类型自动派生。
 */
public final class RegistryTypesPoint {

    /** 扩展点 id。 */
    public static final String ID = "nekojs:registry_types";

    private static final Logger LOGGER = LoggerFactory.getLogger("nekojs.bootstrap");
    private static final MergePolicy POLICY = MergePolicy.overrideWarn();

    private RegistryTypesPoint() {
    }

    /** 贡献面：实现本接口的插件登记命名类型与 default 类型。 */
    public interface Contributor extends NekoJSPlugin {

        /** 默认空实现。 */
        default void registerRegistryTypes(RegistryTypesCollector collector) {
        }
    }

    /** 收集期累积器：{@code (registry, name) → 工厂} 分组表 + default 表。 */
    public static final class RegistryTypesCollector {
        final Map<ResourceKey<? extends Registry<?>>, Map<String, Function<Identifier, ?>>> byRegistry = new LinkedHashMap<>();
        final Map<ResourceKey<? extends Registry<?>>, String> defaults = new LinkedHashMap<>();
        /** (registry, name) → builder 类（声明/契约派生用；未提供的类型无派生声明）。 */
        final Map<ResourceKey<? extends Registry<?>>, Map<String, Class<? extends RegistryObjectBuilder<?>>>> builderClasses = new LinkedHashMap<>();

        /** 登记一个命名类型（同 (registry, name) 后到覆盖 + warn，overrideWarn）。 */
        public <B extends RegistryObjectBuilder<?>> void registerType(
                ResourceKey<? extends Registry<?>> registry, String name, Function<Identifier, B> factory) {
            registerType(registry, name, null, factory);
        }

        /**
         * 登记一个命名类型并声明 builder 类（ticket 15）：builder 类是 typed Builder 公开
         * 成员 / 校验 / 声明派生的契约反射输入（AC7/AC9）——runtime member、fingerprint、
         * TS/Python declaration 与 golden 都从它派生，不手写第二份成员表。
         */
        public <B extends RegistryObjectBuilder<?>> void registerType(
                ResourceKey<? extends Registry<?>> registry, String name,
                Class<B> builderType, Function<Identifier, B> factory) {
            byRegistry.computeIfAbsent(registry, k -> new LinkedHashMap<>()).merge(name, factory,
                    (oldFactory, newFactory) -> {
                        boolean keepNew = POLICY.resolveDuplicate(ID + "(" + registry.identifier() + ")",
                                "plugin", name, LOGGER);
                        return keepNew ? newFactory : oldFactory;
                    });
            if (builderType != null) {
                builderClasses.computeIfAbsent(registry, k -> new LinkedHashMap<>())
                        .put(name, builderType);
            }
        }

        /** 设定注册表的 default 类型名（脚本糖方法免名直达）。 */
        public void setDefault(ResourceKey<? extends Registry<?>> registry, String name) {
            defaults.put(registry, name);
        }
    }

    /** 产物：不可变类型表 + default 表。 */
    record RegistryTypes(
            Map<ResourceKey<? extends Registry<?>>, Map<String, Function<Identifier, ?>>> byRegistry,
            Map<ResourceKey<? extends Registry<?>>, String> defaults,
            Map<ResourceKey<? extends Registry<?>>, Map<String, Class<? extends RegistryObjectBuilder<?>>>> builderClasses) {
        RegistryTypes {
            byRegistry = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byRegistry));
            defaults = Map.copyOf(defaults);
            builderClasses = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(builderClasses));
        }

        /** 取某注册表的命名类型工厂（无则 null）。 */
        @SuppressWarnings("unchecked")
        public Function<Identifier, RegistryObjectBuilder<?>> type(ResourceKey<? extends Registry<?>> registry, String name) {
            return (Function<Identifier, RegistryObjectBuilder<?>>) byRegistry.getOrDefault(registry, Map.of()).get(name);
        }

        /** 取某注册表的 default 类型工厂（未设 default 则 null）。 */
        @SuppressWarnings("unchecked")
        public Function<Identifier, RegistryObjectBuilder<?>> defaultType(ResourceKey<? extends Registry<?>> registry) {
            String name = defaults.get(registry);
            return name == null ? null : type(registry, name);
        }

        /** 某注册表下全部已登记类型名（错误提示 / 目录用）。 */
        public java.util.Set<String> typeNames(ResourceKey<? extends Registry<?>> registry) {
            Map<String, Function<Identifier, ?>> names = byRegistry.get(registry);
            return names == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(names.keySet());
        }

        /** 按类型名反查归属注册表（可能多个：{@code event.custom} 的全局名解析用）。 */
        public java.util.List<ResourceKey<? extends Registry<?>>> registriesOf(String typeName) {
            return byRegistry.entrySet().stream()
                    .filter(entry -> entry.getValue().containsKey(typeName))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        /** 某注册表下已声明 builder 类的 (类型名 → builder 类) 只读视图（契约/声明派生输入）。 */
        public Map<String, Class<? extends RegistryObjectBuilder<?>>> builderClassesOf(
                ResourceKey<? extends Registry<?>> registry) {
            return java.util.Collections.unmodifiableMap(builderClasses.getOrDefault(registry, Map.of()));
        }
    }

    /** 扩展点定义（由 NekoRegistryPointsPlugin 注册）。 */
    public static final NekoPluginExtensionPoint<Contributor, RegistryTypesCollector, RegistryTypes> POINT =
            NekoPluginExtensionPoint.<Contributor, RegistryTypesCollector, RegistryTypes>builder(ID, Contributor.class)
                    .merge(POLICY)
                    .dependsOn(RegistryInfosPoint.POINT)
                    .initializer(context -> {
                        // 数据依赖（ADR-0002 ②）：registry_infos 产物已就绪（dependsOn 拓扑保证）
                        context.result(RegistryInfosPoint.POINT);
                        return new RegistryTypesCollector();
                    })
                    .collector(Contributor::registerRegistryTypes)
                    .finish(collector -> new RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses))
                    .build();
}
