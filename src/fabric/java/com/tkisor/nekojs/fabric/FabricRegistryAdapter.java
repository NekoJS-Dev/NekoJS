package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Registers a collected startup epoch through Fabric's vanilla registry sinks.
 * Each pass refreshes the pending registries so co-registered entries are included.
 * ITEM runs after the other registries that can produce block items or spawn eggs;
 * each registry still receives only one pass within the runtime epoch.
 */
public final class FabricRegistryAdapter {
    private FabricRegistryAdapter() {}

    private static volatile StartupRegistryRuntime runtime = new StartupRegistryRuntime(nodeLabel());

    /** 当前 epoch 的 Runtime。 */
    public static StartupRegistryRuntime runtime() {
        return runtime;
    }

    /** 新一轮游戏启动：换新 epoch；上一轮若有未清空暂存，先丢弃并记录诊断（AC2）。 */
    public static void beginBoot() {
        StartupRegistryRuntime previous = runtime;
        if (previous != null && !previous.isFullyDrained()) {
            previous.reportUndelivered(message -> NekoJS.LOGGER.error(
                    "[registry-startup] stale staging from a previous boot discarded: {}", message));
        }
        runtime = new StartupRegistryRuntime(nodeLabel());
    }

    /** mod 入口在 STARTUP 脚本加载完成后调用（收集依赖脚本侧已挂好监听）。 */
    public static void onInitialize() {
        StartupRegistryRuntime current = runtime;
        current.collectOnce();
        drainPendingRegistries(current, key -> drainRegistry(current, key));
        // 实体属性挂载：EntityTypeBuilder build 期记账的属性表统一注册（NeoForge 侧由
        // EntityAttributeCreationEvent 消费同一 drainPendingAttributes）
        com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder.drainPendingAttributes()
                .forEach(net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry::register);
        // groupTab 分配消费：GROUP_ASSIGNMENTS 按标签页分组注册 modifyOutput 追加
        //（NeoForge 侧由 BuildCreativeModeTabContentsEvent 消费）；modifyOutput 是懒回调，
        // 注册表此时已冻结完毕，物品解析在回调期安全
        java.util.Map<Identifier, java.util.List<Identifier>> byTab = new java.util.HashMap<>();
        com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder.GROUP_ASSIGNMENTS.forEach(
                (itemId, tabId) -> byTab.computeIfAbsent(tabId, k -> new java.util.ArrayList<>()).add(itemId));
        byTab.forEach((tabId, itemIds) ->
                net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(
                                ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, tabId))
                        .register(output -> itemIds.forEach(id ->
                                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item ->
                                        output.accept(new net.minecraft.world.item.ItemStack(item))))));
        // 物品燃料（NeoForge 侧走 getBurnTime override；fabric 经 FuelValueEvents.BUILD 灌表，
        // 每次燃料表构建（服务器启动/数据包重载）都会重放——幂等）
        if (!com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder.FUEL_ASSIGNMENTS.isEmpty()) {
            net.fabricmc.fabric.api.registry.FuelValueEvents.BUILD.register((builder, ctx) ->
                    com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder.FUEL_ASSIGNMENTS.forEach((id, time) ->
                            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> builder.add(item, time))));
        }
    }

    static void drainPendingRegistries(StartupRegistryRuntime current,
            java.util.function.Consumer<ResourceKey<? extends Registry<?>>> drain) {
        java.util.Set<ResourceKey<? extends Registry<?>>> visited = new java.util.HashSet<>();
        java.util.Comparator<ResourceKey<? extends Registry<?>>> order = java.util.Comparator
                .comparing((ResourceKey<? extends Registry<?>> key) -> key.equals(Registries.ITEM))
                .thenComparing(key -> key.identifier().toString());
        while (true) {
            java.util.Set<ResourceKey<? extends Registry<?>>> pending = current.snapshotUndrainedRegistries();
            if (pending.isEmpty()) return;
            ResourceKey<? extends Registry<?>> next = pending.stream().min(order).orElseThrow();
            if (!visited.add(next)) {
                throw new IllegalStateException("[NEKO-4028] Registry pass did not drain its pending content: "
                        + next.identifier());
            }
            drain.accept(next);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drainRegistry(StartupRegistryRuntime current, ResourceKey<? extends Registry<?>> key) {
        // REGISTRY 是根注册表（Registry of Registry），泛型捕获与任一具体键不兼容——raw 收窄
        java.util.Optional<?> resolved = BuiltInRegistries.REGISTRY.get((ResourceKey) key);
        Registry<?> registry = resolved.isPresent() ? (Registry<?>) ((Holder<?>) resolved.get()).value() : null;
        if (registry == null) {
            NekoJS.LOGGER.error("Registry '{}' collected objects but no such vanilla registry exists; content NOT registered", key);
            // sink 抛错：每条进 DrainResult.errors（source=platform-register，含定义/注册表/节点），
            // 不冒充已注册（审查 F7，与 AC10 可观察语义一致）；条目仍被 drain（无残留）
            current.drainFor(key, (reg, id, supplier) -> {
                throw new IllegalStateException("no such vanilla registry '" + key + "' on fabric; content NOT registered");
            }).errors().forEach(error -> NekoJS.LOGGER.error(
                    "[registry-startup] {} in registry '{}' (node {}, source {}): {}",
                            error.definition(), error.registry() == null ? "?" : error.registry().identifier(),
                            error.node(), error.source(), error.message()));
            return;
        }
        current.drainFor(key, (reg, id, supplier) -> register(registry, id, supplier.get()))
                .errors().forEach(error -> NekoJS.LOGGER.error(
                        "[registry-startup] {} in registry '{}' (node {}, source {}): {}",
                                error.definition(), error.registry() == null ? "?" : error.registry().identifier(),
                                error.node(), error.source(), error.message()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(Registry<?> registry, Identifier id, Object value) {
        Registry.register((Registry) registry, id, value);
    }

    private static String nodeLabel() {
        try {
            return com.tkisor.nekojs.platform.Platform.getLoaderId() + ":" + com.tkisor.nekojs.platform.Platform.getMcVersion();
        } catch (IllegalStateException notBootstrapped) {
            return "fabric:?";
        }
    }
}
