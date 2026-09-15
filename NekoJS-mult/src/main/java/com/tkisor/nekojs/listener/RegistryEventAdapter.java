//? if neoforge {
//~ mc_legacy_api
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Supplier;

/**
 * 通用注册表的 NeoForge 适配层（ADR-0004 三层解耦的平台层；ticket 15 起为
 * {@link StartupRegistryRuntime} 的薄接线）：收集、校验、指纹、抽干与连带投递
 * 都在 Runtime（版本树共享 MC-facing 层——持有 MC 类型令牌做校验/键表达，不创建 MC 对象，
 * MC/loader 对象创建只发生在本类与 fabric 对应物这样的 Adapter）；本类只把 {@link RegisterEvent} 的各 pass 接到
 * {@code runtime.drainFor}（对象以 Supplier 注册，由平台在注册冻结前构建）。
 *
 * <p>epoch：每轮游戏启动（mod 构造期）{@link #beginBoot()} 换新 Runtime，
 * 上一轮残留被丢弃并诊断——失败不留下可污染下一轮启动的进程级暂存（AC2）。
 *
 * <p>另承接注册表体系的两个后置平台事件：实体属性表（builder build 期记账）
 * 与创造标签页内容（{@code ItemBuilder} groupTab 的分配）。
 */
public final class RegistryEventAdapter {
    private RegistryEventAdapter() {}

    private static volatile StartupRegistryRuntime runtime = new StartupRegistryRuntime(nodeLabel());

    /** 当前 epoch 的 Runtime（注册 pass 接线用）。 */
    public static StartupRegistryRuntime runtime() {
        return runtime;
    }

    /** 新一轮游戏启动：换新 epoch；上一轮若有未清空暂存，先丢弃并记录诊断。 */
    public static void beginBoot() {
        StartupRegistryRuntime previous = runtime;
        if (previous != null && !previous.isFullyDrained()) {
            previous.reportUndelivered(message -> NekoJS.LOGGER.error(
                    "[registry-startup] stale staging from a previous boot discarded: {}", message));
        }
        runtime = new StartupRegistryRuntime(nodeLabel());
    }

    public static void onRegister(RegisterEvent event) {
        ResourceKey<? extends Registry<?>> key = event.getRegistryKey();
        StartupRegistryRuntime current = runtime;
        current.collectOnce();
        current.drainFor(key, (registry, id, supplier) -> registerInto(event, registry, id, supplier))
                .errors().forEach(error -> NekoJS.LOGGER.error(
                        "[registry-startup] {} in registry '{}' (node {}, source {}): {}",
                                error.definition(), error.registry() == null ? "?" : error.registry().identifier(),
                                error.node(), error.source(), error.message()));
    }

    /** load-complete 诊断：收集了却未被任何 pass 消化的内容（注册表名写错 / 目标 pass 先于来源）。 */
    public static void onLoadComplete() {
        runtime.reportUndelivered(message -> NekoJS.LOGGER.error("{}", message));
    }

    /** 实体属性表：builder build 期记账，本事件（注册完成后）统一挂载。 */
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        EntityTypeBuilder.drainPendingAttributes().forEach(event::put);
    }

    /**
     * 创造标签页内容构建：把 {@link ItemBuilder#GROUP_ASSIGNMENTS} 里分配到当前
     * 标签页的物品追加进去（脚本经 {@code b.groupTab = 'tab_id'} 分配）。
     * 在所有注册完成之后触发，故可安全从 {@link BuiltInRegistries#ITEM} 解析。
     */
    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // 26.x: ResourceKey.identifier()（Identifier 已重命名为 Identifier）
//? if >=26 {
        var tabId = event.getTabKey().identifier();
//?} else {
/*        var tabId = event.getTabKey().location();
*///?}
        ItemBuilder.GROUP_ASSIGNMENTS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(tabId))
                .forEach(entry -> acceptCreativeTabEntry(event, entry.getKey()));
    }

    private static void acceptCreativeTabEntry(BuildCreativeModeTabContentsEvent event, Identifier itemId) {
        // 26.x: Registry.get(Identifier) 返回 Optional<Reference<Item>>，取 .value()
//? if >=26 {
        Item item = BuiltInRegistries.ITEM.get(itemId).map(Holder::value).orElse(null);
//?} else {
/*        Item item = BuiltInRegistries.ITEM.get(itemId);
*///?}
        if (item != null && item != Items.AIR) {
            event.accept(new net.minecraft.world.item.ItemStack(item),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static String nodeLabel() {
        try {
            return com.tkisor.nekojs.platform.Platform.getLoaderId() + ":" + com.tkisor.nekojs.platform.Platform.getMcVersion();
        } catch (IllegalStateException notBootstrapped) {
            return "neoforge:?";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerInto(
            RegisterEvent event, ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier) {
        event.register((ResourceKey) registry, id, (Supplier) supplier);
    }
}
//?}
