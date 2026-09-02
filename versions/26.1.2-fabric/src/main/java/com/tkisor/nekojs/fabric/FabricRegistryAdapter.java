package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryEventJS;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryEvents;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryObjectBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryRepository;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 通用注册表的 Fabric 适配层（ADR-0004 平台层的 fabric 形态）：
 * 与 NeoForge 侧逐 pass 抽干不同，fabric 在 {@code onInitialize} 内<b>单批</b>
 * 完成——先 post 一次平台无关收集事件（脚本回调攒 builder），再逐注册表抽干、
 * 以 vanilla {@link Registry#register} 直注。跨注册表互引经 builder 的懒
 * {@code get()} 解析，单批内的注册次序无关紧要。
 */
public final class FabricRegistryAdapter {
    private FabricRegistryAdapter() {}

    private static final RegistryRepository REPOSITORY = new RegistryRepository();
    private static boolean collected;

    /** mod 入口在 STARTUP 脚本加载完成后调用（收集依赖脚本侧已挂好监听）。 */
    public static void onInitialize() {
        if (!collected) {
            collected = true;
            RegistryEvents.REGISTER.post(RegistryEventJS.create(REPOSITORY));
        }
        // 快照 key 集再逐个抽干（drain 会改结构）
        for (ResourceKey<? extends Registry<?>> key : REPOSITORY.undrained().keySet().stream().toList()) {
            drainRegistry(key);
        }
        REPOSITORY.undeliveredAdditional().keySet().stream().toList().forEach(FabricRegistryAdapter::drainRegistry);
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
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drainRegistry(ResourceKey<? extends Registry<?>> key) {
        // REGISTRY 是根注册表（Registry of Registry），泛型捕获与任一具体键不兼容——raw 收窄
        java.util.Optional<?> resolved = BuiltInRegistries.REGISTRY.get((ResourceKey) key);
        Registry<?> registry = resolved.isPresent() ? (Registry<?>) ((Holder<?>) resolved.get()).value() : null;
        if (registry == null) {
            NekoJS.LOGGER.error("Registry '{}' collected objects but no such vanilla registry exists; content NOT registered", key);
            REPOSITORY.drain(key);
            REPOSITORY.drainAdditional(key);
            return;
        }
        for (RegistryObjectBuilder<?> builder : REPOSITORY.drain(key)) {
            register(registry, builder.id, builder.get());
        }
        for (RegistryRepository.Additional additional : REPOSITORY.drainAdditional(key)) {
            register(registry, additional.id(), additional.supplier().get());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(Registry<?> registry, Identifier id, Object value) {
        Registry.register((Registry) registry, id, value);
    }
}
