package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.core.plugin.EventsPoint;
import com.tkisor.nekojs.core.plugin.TypeDocsPoint;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionHandle;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionRegistry;

import net.minecraft.core.registries.Registries;

/**
 * 通用注册表的扩展点定义插件：以与第三方完全相同的 provider 路径注册
 * {@code nekojs:registry_infos} / {@code nekojs:registry_types} 两个引擎级扩展点，
 * 并把单一注册入口 {@link RegistryEvents#REGISTER} 挂进事件组、登记内置 builder 类型。
 *
 * <p>这两点定义在版本树（触及 MC 类型，ADR-0007 L3 层），故不经 common 的
 * {@code NekoBuiltinPointsPlugin} 清单，而由本插件（{@code @RegisterNekoJSPlugin}
 * 扫描发现）注册——V2 模型下"引擎 EP + 版本树定义"的规范形态。
 * registry_types 经 {@code dependsOn(registry_infos)} 声明依赖，拓扑序由引擎保证。
 *
 * <p>内置类型清单随 Builder 重写逐个扩充；当前先落首个类型
 * （sound_event/basic）做垂直切片。
 */
@RegisterNekoJSPlugin
public final class NekoRegistryPointsPlugin
        implements NekoJSPlugin, NekoPluginExtensionProvider,
        EventsPoint.Contributor, RegistryTypesPoint.Contributor, TypeDocsPoint.Contributor {

    private static NekoPluginExtensionHandle<RegistryInfosPoint.RegistryInfos> infosHandle;
    private static NekoPluginExtensionHandle<RegistryTypesPoint.RegistryTypes> typesHandle;

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        infosHandle = registry.register(RegistryInfosPoint.POINT);
        typesHandle = registry.register(RegistryTypesPoint.POINT);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(RegistryEvents.GROUP);
    }

    @Override
    public void registerTypeDocs(com.tkisor.nekojs.core.plugin.TypeDocsRegister registry) {
        NekoRegistryDeclarations.register(registry);
    }

    @Override
    public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
        // 平台无关类型（纯 vanilla 构建）
        collector.registerType(Registries.SOUND_EVENT, "basic", SoundEventBuilder::new);
        collector.setDefault(Registries.SOUND_EVENT, "basic");
        collector.registerType(Registries.MOB_EFFECT, "basic", MobEffectBuilder::new);
        collector.setDefault(Registries.MOB_EFFECT, "basic");
        collector.registerType(Registries.POTION, "basic", PotionBuilder::new);
        collector.setDefault(Registries.POTION, "basic");
        collector.registerType(Registries.PAINTING_VARIANT, "basic", PaintingVariantBuilder::new);
        collector.setDefault(Registries.PAINTING_VARIANT, "basic");
        collector.registerType(Registries.VILLAGER_TYPE, "basic", VillagerTypeBuilder::new);
        collector.setDefault(Registries.VILLAGER_TYPE, "basic");
        // builder 本体平台无关（零 loader 依赖），fabric 由 FabricRegistryAdapter 单批直注 +
        // 收尾挂实体属性/groupTab 追加（NeoForge 侧经 EntityAttributeCreationEvent /
        // BuildCreativeModeTabContentsEvent 消费同一批记账）。
        collector.registerType(Registries.ITEM, "basic", ItemBuilder::new);
        collector.setDefault(Registries.ITEM, "basic");
        collector.registerType(Registries.BLOCK, "basic", BlockBuilder::new);
        collector.setDefault(Registries.BLOCK, "basic");
//? if neoforge {
        // 流体体系是 NeoForge 面（FluidStack/FluidIngredient），fabric 等价物需重设计
        collector.registerType(Registries.FLUID, "basic", FluidBuilder::new);
        collector.setDefault(Registries.FLUID, "basic");
//?}
        collector.registerType(Registries.ENTITY_TYPE, "basic", EntityTypeBuilder::new);
        collector.setDefault(Registries.ENTITY_TYPE, "basic");
        collector.registerType(Registries.ENCHANTMENT, "basic", EnchantmentBuilder::new);
        collector.setDefault(Registries.ENCHANTMENT, "basic");
        collector.registerType(Registries.PARTICLE_TYPE, "basic", ParticleTypeBuilder::new);
        collector.setDefault(Registries.PARTICLE_TYPE, "basic");
        collector.registerType(Registries.CREATIVE_MODE_TAB, "basic", CreativeTabBuilder::new);
        collector.setDefault(Registries.CREATIVE_MODE_TAB, "basic");
    }

    /** registry_infos 产物（bootstrap 完成后可取；adapter 构建脚本面事件时调用）。 */
    static RegistryInfosPoint.RegistryInfos registryInfos() {
        return requireResult(infosHandle, RegistryInfosPoint.ID);
    }

    /** registry_types 产物（bootstrap 完成后可取；adapter 构建脚本面事件时调用）。 */
    static RegistryTypesPoint.RegistryTypes registryTypes() {
        return requireResult(typesHandle, RegistryTypesPoint.ID);
    }

    private static <R> R requireResult(NekoPluginExtensionHandle<R> handle, String pointId) {
        if (handle == null || !handle.isFinished()) {
            throw new IllegalStateException("extension point '" + pointId + "' has not finished yet (bootstrap incomplete)");
        }
        return handle.result();
    }
}
