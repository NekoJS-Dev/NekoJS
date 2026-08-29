//? if neoforge {
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryEventJS;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryEvents;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryObjectBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryRepository;
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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 通用注册表的 NeoForge 适配层（ADR-0004 三层解耦的平台层）。
 *
 * <p>订阅 {@link RegisterEvent}：首个 pass 前投递一次平台无关收集事件
 * （{@link RegistryEvents#REGISTER}，脚本回调把 builder 攒进仓库），随后按
 * loader 序逐 pass 抽干 {@link RegistryRepository}——主对象与连带派生条目都在
 * <b>目标注册表自身的 pass</b> 内以 Supplier 注册（KubeJS 同款语义：loader
 * 注册序不可回溯，目标 pass 已过的派生条目立即报错跳过）。
 *
 * <p>另承接注册表体系的两个后置平台事件：实体属性表（builder build 期记账）
 * 与创造标签页内容（{@code ItemBuilder.groupTab} 的分配）。
 */
public final class RegistryEventAdapter {
    private RegistryEventAdapter() {}

    private static final RegistryRepository REPOSITORY = new RegistryRepository();
    private static final Set<ResourceKey<? extends Registry<?>>> PASSED = new HashSet<>();
    private static boolean collected;

    public static void onRegister(RegisterEvent event) {
        ResourceKey<? extends Registry<?>> key = event.getRegistryKey();
        if (!collected) {
            collected = true;
            RegistryEvents.REGISTER.post(RegistryEventJS.create(REPOSITORY));
        }
        for (RegistryObjectBuilder<?> builder : REPOSITORY.drain(key)) {
            registerInto(event, key, builder.id, builder);
            collectAdditionalOf(builder);
        }
        for (RegistryRepository.Additional additional : REPOSITORY.drainAdditional(key)) {
            registerInto(event, key, additional.id(), additional.supplier());
        }
        // 本 pass 结束后才计入 PASSED：builder 派生条目指向本注册表时仍是合法的当轮注册
        PASSED.add(key);
    }

    /** 抽干期回调：builder 的连带派生条目入仓库，等目标注册表自己的 pass 投递。 */
    private static void collectAdditionalOf(RegistryObjectBuilder<?> builder) {
        try {
            builder.handleAdditionalObjects((registry, id, supplier) -> {
                if (PASSED.contains(registry)) {
                    NekoJS.LOGGER.error("Additional object '{}' of '{}' targets registry '{}'"
                            + " whose registration already passed; NOT registered",
                            id, builder.id, registry);
                    return;
                }
                REPOSITORY.addAdditional(registry, id, supplier, builder.id);
            });
        } catch (Exception e) {
            NekoJS.LOGGER.error("Failed to collect additional objects of '{}'", builder.id, e);
        }
    }

    /** load-complete 诊断：收集了却未被任何 pass 消化的内容（注册表名写错 / 目标 pass 先于来源）。 */
    public static void onLoadComplete() {
        REPOSITORY.undrained().forEach((registry, builders) -> NekoJS.LOGGER.error(
                "Registry '{}' collected {} object(s) but its RegisterEvent never fired; content NOT registered",
                registry, builders.size()));
        REPOSITORY.undeliveredAdditional().forEach((registry, additionals) -> NekoJS.LOGGER.error(
                "Registry '{}' has {} undelivered additional object(s); content NOT registered",
                registry, additionals.size()));
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
        // 26.x: ResourceKey.identifier()（ResourceLocation 已重命名为 Identifier）
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerInto(
            RegisterEvent event, ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier) {
        event.register((ResourceKey) registry, id, (Supplier) supplier);
    }
}
//?}
