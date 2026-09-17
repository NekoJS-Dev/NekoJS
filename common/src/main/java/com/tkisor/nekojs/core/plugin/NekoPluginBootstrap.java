package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiRuntimeProvider;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;
import com.tkisor.nekojs.core.api.FrozenApiRegistrySet;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 插件 bootstrap 引擎：扩展点注册 → 点优先收集 → 产出 {@link NekoPluginRuntime}。
 *
 * <p><b>点优先执行序：</b>对每个扩展点依次执行
 * {@code initializer 建累积器 → 遍历插件 collector 收集 → finisher 产结果 → 发布 → 下一个点}。
 * 扩展点按注册顺序（内置 {@code nekojs:*} 在前、自定义在后）逐点完成，后注册点在
 * initializer / collector 里可引用先注册点的产物（{@link NekoPluginExtensionContext#result}），
 * 这是扩展点间依赖的引擎语义。产物同时发布到三处：bootstrap context（供后续点引用）、
 * 注册句柄 {@link NekoPluginExtensionHandle}（供注册方取回）与结果容器
 * （{@code Map<pointId, 产物>}，最终交给 {@link NekoPluginRuntime}）。
 */
public final class NekoPluginBootstrap {

    private NekoPluginBootstrap() {
    }

    public static NekoPluginRuntime bootstrap(List<NekoJSPlugin> plugins, ScriptPropertyRegistry scriptProperties) {
        return new NekoPluginRuntime(collect(plugins, scriptProperties), null, Map.of());
    }

    public static NekoPluginRuntime bootstrapOwned(
            List<OwnedPlugin> ownedPlugins,
            ScriptPropertyRegistry scriptProperties,
            VerifiedContractSet contracts) {
        return bootstrapOwned(ownedPlugins, scriptProperties, contracts, List.of(), Map.of());
    }

    static NekoPluginRuntime bootstrapOwned(
            List<OwnedPlugin> ownedPlugins,
            ScriptPropertyRegistry scriptProperties,
            VerifiedContractSet contracts,
            List<ApiContributionRegistry> builtInContributions,
            Map<ApiSymbolId, Object> managedApiImplementations) {
        Objects.requireNonNull(ownedPlugins, "ownedPlugins");
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(builtInContributions, "builtInContributions");
        Objects.requireNonNull(managedApiImplementations, "managedApiImplementations");

        List<NekoJSPlugin> plugins = ownedPlugins.stream()
                .map(OwnedPlugin::plugin)
                .toList();

        Map<String, Object> products = collect(plugins, scriptProperties);

        List<ApiContributionRegistry> managedContributions = new ArrayList<>(builtInContributions);
        for (OwnedPlugin owned : ownedPlugins) {
            List<VerifiedApiContract> ownerContracts = contracts.forOwner(owned.identity().ownerId());
            if (ownerContracts.isEmpty()) {
                continue;
            }
            VerifiedContractSet ownerContractSet = VerifiedContractSet.of(
                    ownerContracts.toArray(VerifiedApiContract[]::new));
            ApiContributionRegistry registry = ApiContributionRegistry.ownedBy(
                    owned.identity(), ownerContractSet);
            owned.plugin().registerApiSurface(registry);
            managedContributions.add(registry);
        }

        List<LegacyGlobalReservation> legacyReservations = buildLegacyReservations(products);

        List<EnvironmentKey> environmentKeys = buildEnvironmentKeys();

        ApiRuntimeProvider apiRuntimeProvider = new FrozenApiRegistrySet(
                contracts, managedContributions, legacyReservations, environmentKeys);

        return new NekoPluginRuntime(products, apiRuntimeProvider, managedApiImplementations);
    }

    static ScriptTypePredicate bindingPredicate(boolean client) {
        return client
                ? ScriptTypePredicate.any()
                : ScriptTypePredicate.exact(ScriptType.CLIENT).negate();
    }

    /**
     * 注册并按拓扑序收集全部扩展点，返回 {@code pointId → 产物} 的结果容器。
     *
     * <p>每次 bootstrap 全程重建：扩展点列表（含内置点）与各累积器均为本轮新建，
     * 扩展点实例与累积器都不携带跨轮状态——连续两轮 bootstrap（完整 reload）互不串扰。
     *
     * <p>执行序（ADR-0002）：freeze 时对全部 {@code dependsOn} 边做 Kahn 拓扑排序
     * （同层按注册序，内置先行是图的根部天然保证）；环 fail-fast 打印完整环路径，
     * 未注册依赖 id 在 freeze 早爆。环境谓词为 false 的点跳过并登记，
     * 供 {@link NekoPluginExtensionContext} 两档访问区分"跳过"与"未完成"。
     */
    static Map<String, Object> collect(List<NekoJSPlugin> plugins, ScriptPropertyRegistry scriptProperties) {
        BootstrapContext context = new BootstrapContext(Platform.isClient());
        ExtensionRegistry registry = new ExtensionRegistry();
        // ADR-0003：内置点定义插件显式先行（与第三方同一条 provider 路径；内置先行相位
        // 是引擎对依赖图根部的保证，不依赖插件 priority）
        new NekoBuiltinPointsPlugin(scriptProperties, context.client()).registerPluginExtensionPoints(registry);
        for (NekoJSPlugin plugin : plugins) {
            if (plugin instanceof NekoPluginExtensionProvider provider) {
                provider.registerPluginExtensionPoints(registry);
            }
        }
        List<NekoPluginExtensionPoint<?, ?, ?>> extensionPoints = registry.freeze();
        context.registeredIds(extensionPoints.stream()
                .map(NekoPluginExtensionPoint::id)
                .collect(Collectors.toList()));

        Map<String, Object> products = new LinkedHashMap<>();
        for (NekoPluginExtensionPoint<?, ?, ?> extensionPoint : extensionPoints) {
            if (!extensionPoint.enabled().test(context)) {
                context.markSkipped(extensionPoint.id());
                continue;
            }
            Object product = collectPoint(extensionPoint, plugins, context);
            context.publish(extensionPoint.id(), product);
            products.put(extensionPoint.id(), product);
            registry.publishProduct(extensionPoint.id(), product);
        }
        return Collections.unmodifiableMap(products);
    }

    /** 单个扩展点的收集：init 累积器 → 遍历插件 collect（插件按传入列表序）→ finisher 产结果 → 密封累积器。 */
    private static <P extends NekoJSPlugin, A> Object collectPoint(
            NekoPluginExtensionPoint<P, A, ?> extensionPoint,
            List<NekoJSPlugin> plugins,
            NekoPluginExtensionContext context) {
        A accumulator = extensionPoint.initializer().apply(context);
        for (NekoJSPlugin plugin : plugins) {
            if (extensionPoint.pluginType().isInstance(plugin)) {
                extensionPoint.collector().accept(extensionPoint.pluginType().cast(plugin), accumulator);
            }
        }
        Object product = extensionPoint.finisher().apply(accumulator);
        // ADR-0001：finish 后累积器密封（实现了 Sealable 的 bucket 此后拒绝任何收集）
        if (accumulator instanceof Sealable sealable) {
            sealable.seal();
        }
        return product;
    }

    private static List<LegacyGlobalReservation> buildLegacyReservations(Map<String, Object> products) {
        List<LegacyGlobalReservation> reservations = new ArrayList<>();
        Map<ScriptType, Map<String, Binding>> bindings = cast(products.get(BindingsPoint.ID));
        for (Map<String, Binding> typeBindings : bindings.values()) {
            for (Binding binding : typeBindings.values()) {
                String diagId = "global:" + binding.name() + "#" + binding.valueType().getName();
                reservations.add(new LegacyGlobalReservation(
                        binding.name(), new ApiSymbolId("global", diagId)));
            }
        }
        for (com.tkisor.nekojs.api.event.EventGroup group : EventsPoint.mergedEventGroups(products).values()) {
            String diagId = "event-group:" + group.name();
            reservations.add(new LegacyGlobalReservation(
                    group.name(), new ApiSymbolId("global", diagId)));
        }
        return List.copyOf(reservations);
    }

    private static List<EnvironmentKey> buildEnvironmentKeys() {
        List<EnvironmentKey> keys = new ArrayList<>();
        for (ScriptType type : ScriptType.values()) {
            keys.add(EnvironmentKey.current(type));
        }
        return List.copyOf(keys);
    }

    static Map<ScriptType, Map<String, Binding>> freezeBindings(ScriptTypedValue<BindingRegistry> registries) {
        return registries.stream()
                .collect(Collectors.toMap(
                        BindingRegistry::scriptType,
                        reg -> Map.copyOf(reg.viewRegistered())
                ));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    /** 单次 bootstrap 的扩展点注册器：注册窗口在 freeze 后关闭（与收集阶段同步）。 */
    private static final class ExtensionRegistry implements NekoPluginExtensionRegistry {
        private final Map<String, NekoPluginExtensionPoint<?, ?, ?>> extensionPoints = new LinkedHashMap<>();
        private final Map<String, NekoPluginExtensionHandle<Object>> handles = new LinkedHashMap<>();
        private boolean frozen;

        @Override
        public <P extends NekoJSPlugin, A, R> NekoPluginExtensionHandle<R> register(
                NekoPluginExtensionPoint<P, A, R> extensionPoint) {
            if (frozen) {
                throw new IllegalStateException("Plugin extension registry is frozen after bootstrap collection");
            }
            if (extensionPoints.containsKey(extensionPoint.id())) {
                throw new IllegalArgumentException("Plugin extension point '" + extensionPoint.id() + "' is already registered");
            }
            extensionPoints.put(extensionPoint.id(), extensionPoint);
            NekoPluginExtensionHandle<R> handle = new NekoPluginExtensionHandle<>(extensionPoint.id());
            @SuppressWarnings("unchecked")
            NekoPluginExtensionHandle<Object> erased = (NekoPluginExtensionHandle<Object>) handle;
            handles.put(extensionPoint.id(), erased);
            return handle;
        }

        List<NekoPluginExtensionPoint<?, ?, ?>> freeze() {
            frozen = true;
            // ADR-0002 ①：未注册依赖 id 早爆（拼写错误在收集开始前暴露）。可选依赖
            // （dependsOnOptionalId）不参与早爆：对方缺席的 bootstrap（如不含版本树
            // 插件的 common 测试轮）不建边不报错，见 NekoPluginExtensionPoint javadoc ③
            for (NekoPluginExtensionPoint<?, ?, ?> point : extensionPoints.values()) {
                for (Object dependency : point.dependsOn()) {
                    String depId = NekoPluginExtensionPoint.dependencyId(dependency);
                    if (!extensionPoints.containsKey(depId)) {
                        throw new IllegalStateException("Plugin extension point '" + point.id()
                                + "' dependsOn unregistered point '" + depId + "' (typo?)");
                    }
                }
            }
            // ADR-0002：Kahn 拓扑排序，同层按注册序（注册序仅作稳定 tiebreaker，不承担依赖语义）；
            // 环 fail-fast，报错打印完整环路径
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            Map<String, List<String>> dependents = new LinkedHashMap<>();
            for (String id : extensionPoints.keySet()) {
                inDegree.put(id, 0);
                dependents.put(id, new ArrayList<>());
            }
            for (NekoPluginExtensionPoint<?, ?, ?> point : extensionPoints.values()) {
                for (Object dependency : point.dependsOn()) {
                    dependents.get(NekoPluginExtensionPoint.dependencyId(dependency)).add(point.id());
                    inDegree.merge(point.id(), 1, Integer::sum);
                }
                // 可选时序依赖：仅对方已注册时建边（在场即与硬依赖等效，含环检测）
                for (Object dependency : point.optionalDependsOn()) {
                    String depId = NekoPluginExtensionPoint.dependencyId(dependency);
                    if (extensionPoints.containsKey(depId)) {
                        dependents.get(depId).add(point.id());
                        inDegree.merge(point.id(), 1, Integer::sum);
                    }
                }
            }
            List<NekoPluginExtensionPoint<?, ?, ?>> order = new ArrayList<>();
            java.util.Deque<String> ready = new java.util.ArrayDeque<>();
            for (String id : extensionPoints.keySet()) {
                if (inDegree.get(id) == 0) {
                    ready.add(id);
                }
            }
            while (!ready.isEmpty()) {
                String id = ready.poll();
                order.add(extensionPoints.get(id));
                for (String dependent : dependents.get(id)) {
                    if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                        ready.add(dependent);
                    }
                }
            }
            if (order.size() != extensionPoints.size()) {
                List<String> cycle = new ArrayList<>();
                for (String id : extensionPoints.keySet()) {
                    if (inDegree.get(id) > 0) {
                        cycle.add(id);
                    }
                }
                throw new IllegalStateException("Plugin extension point dependency cycle fail-fast: "
                        + String.join(" -> ", cycle) + " -> " + cycle.get(0));
            }
            return List.copyOf(order);
        }

        void publishProduct(String pointId, Object product) {
            NekoPluginExtensionHandle<Object> handle = handles.get(pointId);
            if (handle == null) {
                throw new IllegalStateException("No handle for extension point '" + pointId + "'");
            }
            handle.publish(product);
        }
    }

    /** 单次 bootstrap 的收集上下文：环境标志 + 注册表 + 跳过登记 + 按点逐步充实的产物表。 */
    private static final class BootstrapContext implements NekoPluginExtensionContext {
        private final boolean client;
        private final Map<String, Object> products = new LinkedHashMap<>();
        private final java.util.Set<String> registeredIds = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> skippedIds = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> finishedIds = new java.util.LinkedHashSet<>();

        BootstrapContext(boolean client) {
            this.client = client;
        }

        /** freeze 后登记本轮全部扩展点 id（供两档访问区分"未注册"与"未完成"）。 */
        void registeredIds(java.util.Collection<String> ids) {
            registeredIds.addAll(ids);
        }

        /** 环境谓词为 false 的点登记为跳过。 */
        void markSkipped(String pointId) {
            skippedIds.add(pointId);
        }

        @Override
        public boolean client() {
            return client;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R result(NekoPluginExtensionPoint<?, ?, R> point) {
            return (R) lookup(point.id());
        }

        @Override
        public <R> R result(String pointId, Class<R> type) {
            Object product = lookup(pointId);
            if (product == null) {
                return null;
            }
            return checkType(pointId, product, type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R resultOrThrow(NekoPluginExtensionPoint<?, ?, R> point) {
            return (R) require(point.id());
        }

        @Override
        public <R> R resultOrThrow(String pointId, Class<R> type) {
            return checkType(pointId, require(pointId), type);
        }

        /** 可选依赖档：未注册 / 跳过 → null；已注册未完成 → 违序抛错（ADR-0002 ②）。 */
        private Object lookup(String pointId) {
            if (finishedIds.contains(pointId)) {
                return products.get(pointId);
            }
            if (!registeredIds.contains(pointId) || skippedIds.contains(pointId)) {
                return null;
            }
            throw new IllegalStateException("Data-dependency ordering violation: extension point '"
                    + pointId + "' is registered but has not finished yet. Declare dependsOn('"
                    + pointId + "') (or dependsOnId) so the bootstrap topological order runs it first.");
        }

        /** 必需依赖档：任何形式缺席 → 抛错（附缺席原因）。 */
        private Object require(String pointId) {
            if (finishedIds.contains(pointId)) {
                return products.get(pointId);
            }
            if (!registeredIds.contains(pointId)) {
                throw new IllegalStateException("Required extension point '" + pointId
                        + "' is not registered in this bootstrap");
            }
            if (skippedIds.contains(pointId)) {
                throw new IllegalStateException("Required extension point '" + pointId
                        + "' is skipped in this environment (e.g. client-only on a dedicated server)");
            }
            throw new IllegalStateException("Required extension point '" + pointId
                    + "' has not finished yet; declare dependsOn('" + pointId + "')");
        }

        private <R> R checkType(String pointId, Object product, Class<R> type) {
            if (!type.isInstance(product)) {
                throw new IllegalStateException("Plugin extension point '" + pointId
                        + "' product type mismatch: expected " + type.getName()
                        + " but was " + product.getClass().getName());
            }
            return type.cast(product);
        }

        void publish(String pointId, Object product) {
            products.put(pointId, product);
            finishedIds.add(pointId);
        }
    }
}
