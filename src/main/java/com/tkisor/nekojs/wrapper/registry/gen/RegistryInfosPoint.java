package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.plugin.NekoExtensionPoint;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Built-in extension point {@code nekojs:registry_infos}.
 *
 * <p>The default scan root is vanilla {@link Registries}: it reflects the public static
 * {@link ResourceKey} fields, so a version that ships a different field set needs no change.
 * Contributors can add scan roots and manual metadata. Opening a static registry for STARTUP writes
 * is a separate, explicit contribution.
 *
 * <p>This point and {@link RegistryTypesPoint} are the first engine-level consumers of the
 * extension point system, and they test the goal that a new built-in extension point costs one file
 * plus one manifest line: {@code NekoRegistryPointsPlugin} (a version tree provider) registers them.
 */
public final class RegistryInfosPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:registry_infos";

    private RegistryInfosPoint() {
    }

    /** Contributes registry metadata and explicitly opens static STARTUP registries. */
    public interface Contributor extends NekoJSPlugin {

        /** Default no-op. */
        default void registerRegistryInfos(RegistryInfosCollector collector) {
        }
    }

    /** Collects registry metadata and the explicit STARTUP catalog. */
    public static final class RegistryInfosCollector {
        final List<Class<?>> classesToScan = new ArrayList<>();
        final List<RegistryInfo> additionalInfos = new ArrayList<>();
        private final Set<ResourceKey<? extends Registry<?>>> startupRegistries = new LinkedHashSet<>();

        /** Adds a scan root class; its public static ResourceKey fields are reflected and registered. */
        public void addClassToScan(Class<?> root) {
            classesToScan.add(Objects.requireNonNull(root, "root"));
        }

        /** Registers one registry info entry by hand, for cases reflection cannot discover. */
        public void addInfo(RegistryInfo info) {
            additionalInfos.add(Objects.requireNonNull(info, "info"));
        }

        /** Opens a static registry to STARTUP scripts. The host mod must install it before native registration. */
        public void openStartupRegistry(ResourceKey<? extends Registry<?>> key) {
            startupRegistries.add(Objects.requireNonNull(key, "key"));
        }
    }

    /** Registry metadata and the independent catalog of static STARTUP targets. */
    record RegistryInfos(
            Map<ResourceKey<? extends Registry<?>>, RegistryInfo> infos,
            Set<ResourceKey<? extends Registry<?>>> startupRegistries) {
        RegistryInfos {
            infos = Map.copyOf(infos);
            startupRegistries = Set.copyOf(startupRegistries);
        }

        /** Looks a registry up by its key. */
        public RegistryInfo get(ResourceKey<? extends Registry<?>> key) {
            return infos.get(key);
        }
    }

    static RegistryInfos scan(RegistryInfosCollector collector) {
        Map<ResourceKey<? extends Registry<?>>, RegistryInfo> map = new LinkedHashMap<>();
        List<Class<?>> roots = new ArrayList<>(collector.classesToScan);
        roots.add(Registries.class);
        for (Class<?> root : roots) {
            for (Field field : root.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !ResourceKey.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    ResourceKey<? extends Registry<?>> key = (ResourceKey<? extends Registry<?>>) field.get(null);
                    map.putIfAbsent(key, new RegistryInfo(key, Object.class));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        for (RegistryInfo info : collector.additionalInfos) {
            map.putIfAbsent(info.key(), info);
        }
        return new RegistryInfos(map, collector.startupRegistries);
    }

    /** The extension point definition; NekoRegistryPointsPlugin registers it. */
    public static final NekoExtensionPoint<RegistryInfos> POINT =
            NekoExtensionPoint.builder(ID).requirePluginType(Contributor.class)
                    .initializer(context -> new RegistryInfosCollector())
                    .collector(Contributor::registerRegistryInfos)
                    .finisher(RegistryInfosPoint::scan)
                    .build();
}
