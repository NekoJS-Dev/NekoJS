package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.plugin.MergePolicy;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Built-in extension point {@code nekojs:registry_infos}.
 *
 * <p>The default scan root is vanilla {@link Registries}: it reflects the public static
 * {@link ResourceKey} fields, so a version that ships a different field set needs no change.
 * Plugins add scan classes or manual entries through {@link Contributor}, for example a mod's own
 * registries. The merge policy is {@link MergePolicy#append}, because contributors add scan classes
 * and info entries and keys do not collide.
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

    /** Contribution side: plugins that implement it add registry scan roots or manual entries. */
    public interface Contributor extends NekoJSPlugin {

        /** Default no-op: scans only the built-in roots. */
        default void registerRegistryInfos(RegistryInfosCollector collector) {
        }
    }

    /** Collector used while collecting: a class list and a manual entry list, merged with append. */
    public static final class RegistryInfosCollector {
        final List<Class<?>> classesToScan = new ArrayList<>();
        final List<RegistryInfo> additionalInfos = new ArrayList<>();

        /** Adds a scan root class; its public static ResourceKey fields are reflected and registered. */
        public void addClassToScan(Class<?> root) {
            classesToScan.add(Objects.requireNonNull(root, "root"));
        }

        /** Registers one registry info entry by hand, for cases reflection cannot discover. */
        public void addInfo(RegistryInfo info) {
            additionalInfos.add(Objects.requireNonNull(info, "info"));
        }
    }

    /** Output: the full registry info table from scan roots and manual entries, de-duplicated by key
     * with the first entry winning. */
    record RegistryInfos(Map<ResourceKey<? extends Registry<?>>, RegistryInfo> infos) {
        RegistryInfos {
            infos = Map.copyOf(infos);
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
        return new RegistryInfos(map);
    }

    /** The extension point definition; NekoRegistryPointsPlugin registers it. */
    public static final NekoPluginExtensionPoint<Contributor, RegistryInfosCollector, RegistryInfos> POINT =
            NekoPluginExtensionPoint.<Contributor, RegistryInfosCollector, RegistryInfos>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new RegistryInfosCollector())
                    .collector(Contributor::registerRegistryInfos)
                    .finish(RegistryInfosPoint::scan)
                    .build();
}
