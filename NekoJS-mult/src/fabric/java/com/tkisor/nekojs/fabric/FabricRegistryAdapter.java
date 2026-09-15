package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 通用注册表的 Fabric 适配层（ADR-0004 平台层的 fabric 形态；ticket 15 起为
 * {@link StartupRegistryRuntime} 的薄接线）：与 NeoForge 侧逐 pass 抽干不同，
 * fabric 在 {@code onInitialize} 内<b>单批</b>完成——先收集一次（同一
 * {@code collectOnce} epoch 语义），再逐注册表 {@code drainFor}、以 vanilla
 * {@link Registry#register} 直注（sink 立即执行 supplier，校验即时可观察）。
 * 跨注册表互引经 builder 的懒 {@code get()} 解析，单批内的注册次序无关紧要。
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
        // 快照 key 集再逐个抽干（drain 会改结构）
        for (ResourceKey<? extends Registry<?>> key : current.snapshotUndrainedRegistries()) {
            drainRegistry(current, key);
        }
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drainRegistry(StartupRegistryRuntime current, ResourceKey<? extends Registry<?>> key) {
        // REGISTRY 是根注册表（Registry of Registry），泛型捕获与任一具体键不兼容——raw 收窄
        java.util.Optional<?> resolved = BuiltInRegistries.REGISTRY.get((ResourceKey) key);
        Registry<?> registry = resolved.isPresent() ? (Registry<?>) ((Holder<?>) resolved.get()).value() : null;
        if (registry == null) {
            NekoJS.LOGGER.error("Registry '{}' collected objects but no such vanilla registry exists; content NOT registered", key);
            current.drainFor(key, (reg, id, supplier) -> { });
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
