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
 * Plugin that defines the extension points for the common registries. It registers the two
 * engine-level extension points {@code nekojs:registry_infos} and {@code nekojs:registry_types}
 * through the same provider path a third-party plugin uses, attaches the single registration entry
 * point {@link RegistryEvents#REGISTER} to the event group, and registers the built-in builder types.
 *
 * <p>Both points touch Minecraft types, so they live in the version tree. That is why they are
 * registered here, by this plugin (found by the {@code @RegisterNekoJSPlugin} scan), instead of
 * going through the common {@code NekoBuiltinPointsPlugin} manifest: an engine extension point
 * defined in the version tree is registered this way. registry_types declares
 * {@code dependsOn(registry_infos)}, and the engine guarantees the topological order.
 *
 * <p>The built-in type list grows as builders are rewritten; for now it carries the first type
 * (sound_event/basic).
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
        // Runtime factories and builder contracts also supply the event payload and both declaration backends.
        RegistryBuilderSurfaces.register(registry, registryInfos(), registryTypes());
    }

    @Override
    public void registerRegistryTypes(RegistryTypesPoint.RegistryTypesCollector collector) {
        // Platform-neutral types, built from vanilla only. The builder class is registered with the type
        // because the contract reads it by reflection
        collector.registerType(Registries.SOUND_EVENT, "basic", SoundEventBuilder.class, SoundEventBuilder::new);
        collector.setDefault(Registries.SOUND_EVENT, "basic");
        collector.registerType(Registries.MOB_EFFECT, "basic", MobEffectBuilder.class, MobEffectBuilder::new);
        collector.setDefault(Registries.MOB_EFFECT, "basic");
        collector.registerType(Registries.POTION, "basic", PotionBuilder.class, PotionBuilder::new);
        collector.setDefault(Registries.POTION, "basic");
        collector.registerType(Registries.VILLAGER_TYPE, "basic", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.setDefault(Registries.VILLAGER_TYPE, "basic");
        // The builders themselves are platform-neutral and depend on no loader. Fabric injects them in one
        // batch through FabricRegistryAdapter and appends entity attributes and groupTab entries at the end
        // (NeoForge consumes the same bookkeeping through EntityAttributeCreationEvent and
        // BuildCreativeModeTabContentsEvent).
        collector.registerType(Registries.ITEM, "basic", ItemBuilder.class, ItemBuilder::new);
        collector.setDefault(Registries.ITEM, "basic");
        collector.registerType(Registries.BLOCK, "basic", BlockBuilder.class, BlockBuilder::new);
        collector.setDefault(Registries.BLOCK, "basic");
//? if neoforge {
// The fluid system is a NeoForge feature (FluidStack/FluidIngredient); the fabric equivalent
// would have to be redesigned
        collector.registerType(Registries.FLUID, "basic", FluidBuilder.class, FluidBuilder::new);
        collector.setDefault(Registries.FLUID, "basic");
//?}
        collector.registerType(Registries.ENTITY_TYPE, "basic", EntityTypeBuilder.class, EntityTypeBuilder::new);
        collector.setDefault(Registries.ENTITY_TYPE, "basic");
        collector.registerType(Registries.ENCHANTMENT, "basic", EnchantmentBuilder.class, EnchantmentBuilder::new);
        collector.setDefault(Registries.ENCHANTMENT, "basic");
        collector.registerType(Registries.PARTICLE_TYPE, "basic", ParticleTypeBuilder.class, ParticleTypeBuilder::new);
        collector.setDefault(Registries.PARTICLE_TYPE, "basic");
        collector.registerType(Registries.CREATIVE_MODE_TAB, "basic", CreativeTabBuilder.class, CreativeTabBuilder::new);
        collector.setDefault(Registries.CREATIVE_MODE_TAB, "basic");
    }

    /** The registry_infos result. Available once bootstrap finishes; the adapter reads it when it builds
     * the script-facing events. */
    static RegistryInfosPoint.RegistryInfos registryInfos() {
        return requireResult(infosHandle, RegistryInfosPoint.ID);
    }

    /** The registry_types result. Available once bootstrap finishes; the adapter reads it when it builds
     * the script-facing events. */
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
