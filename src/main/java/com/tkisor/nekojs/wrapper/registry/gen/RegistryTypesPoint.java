package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.plugin.MergePolicy;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Built-in extension point {@code nekojs:registry_types}: named builder types grouped by registry,
 * plus one default type per registry.
 *
 * <p>This point {@code dependsOn(nekojs:registry_infos)} for <b>ordering</b>, and its initializer
 * reads that output; data dependencies need no declaration. The types are then checked at
 * registration. Type-level conflicts merge with {@link MergePolicy#overrideWarn}, so a third party
 * may override a built-in type and gets a warning, matching how addons usually work. Script sugar
 * methods such as {@code event.item(id, cb)} are derived from the default type.
 */
public final class RegistryTypesPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:registry_types";

    private static final Logger LOGGER = LoggerFactory.getLogger("nekojs.bootstrap");
    private static final MergePolicy POLICY = MergePolicy.overrideWarn();

    private RegistryTypesPoint() {
    }

    /** Contribution side: plugins that implement it register named types and default types. */
    public interface Contributor extends NekoJSPlugin {

        /** Default no-op. */
        default void registerRegistryTypes(RegistryTypesCollector collector) {
        }
    }

    /** Collector used while collecting: a map from {@code (registry, name)} to a factory, grouped by
     * registry, plus a default table. */
    public static final class RegistryTypesCollector {
        final Map<ResourceKey<? extends Registry<?>>, Map<String, Function<Identifier, ?>>> byRegistry = new LinkedHashMap<>();
        final Map<ResourceKey<? extends Registry<?>>, String> defaults = new LinkedHashMap<>();
        /** Maps a registry and type name to the builder class, used to derive declarations and contracts;
         * a type registered without a class gets no derived declaration. */
        final Map<ResourceKey<? extends Registry<?>>, Map<String, Class<? extends RegistryObjectBuilder<?>>>> builderClasses = new LinkedHashMap<>();

        /** Registers one named type; a later registration for the same (registry, name) overrides the
         * earlier one and logs a warning (overrideWarn). */
        public <B extends RegistryObjectBuilder<?>> void registerType(
                ResourceKey<? extends Registry<?>> registry, String name, Function<Identifier, B> factory) {
            registerType(registry, name, null, factory);
        }

        /**
         * Registers one named type and declares its builder class. That class is the reflection input
         * for the members, validation and declarations of the typed builder: runtime members,
         * fingerprints, TS/Python declarations and golden files all derive from it, so there is no
         * second member table written by hand.
         */
        public <B extends RegistryObjectBuilder<?>> void registerType(
                ResourceKey<? extends Registry<?>> registry, String name,
                Class<B> builderType, Function<Identifier, B> factory) {
            java.util.Objects.requireNonNull(factory);
            var factories = byRegistry.computeIfAbsent(registry, k -> new LinkedHashMap<>());
            if (factories.containsKey(name) && !POLICY.resolveDuplicate(ID + "(" + registry.identifier() + ")",
                    "plugin", name, LOGGER)) {
                return;
            }
            factories.put(name, factory);
            // Class metadata belongs to the selected factory; an untyped override invalidates the old class.
            if (builderType != null) {
                builderClasses.computeIfAbsent(registry, k -> new LinkedHashMap<>())
                        .put(name, builderType);
            } else if (builderClasses.containsKey(registry)) {
                builderClasses.get(registry).remove(name);
            }
        }

        /** Sets the registry's default type name, which the script sugar method uses without naming a type. */
        public void setDefault(ResourceKey<? extends Registry<?>> registry, String name) {
            defaults.put(registry, name);
        }
    }

    /** Output: the immutable type table plus the default table. */
    record RegistryTypes(
            Map<ResourceKey<? extends Registry<?>>, Map<String, Function<Identifier, ?>>> byRegistry,
            Map<ResourceKey<? extends Registry<?>>, String> defaults,
            Map<ResourceKey<? extends Registry<?>>, Map<String, Class<? extends RegistryObjectBuilder<?>>>> builderClasses) {
        RegistryTypes {
            byRegistry = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byRegistry));
            defaults = Map.copyOf(defaults);
            builderClasses = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(builderClasses));
        }

        /** Returns the named type factory of a registry, or null when it has none. */
        @SuppressWarnings("unchecked")
        public Function<Identifier, RegistryObjectBuilder<?>> type(ResourceKey<? extends Registry<?>> registry, String name) {
            return (Function<Identifier, RegistryObjectBuilder<?>>) byRegistry.getOrDefault(registry, Map.of()).get(name);
        }

        /** Returns the default type factory of a registry, or null when no default is set. */
        @SuppressWarnings("unchecked")
        public Function<Identifier, RegistryObjectBuilder<?>> defaultType(ResourceKey<? extends Registry<?>> registry) {
            String name = defaults.get(registry);
            return name == null ? null : type(registry, name);
        }

        /** Names of every type registered under one registry. Used for error messages and for listings. */
        public java.util.Set<String> typeNames(ResourceKey<? extends Registry<?>> registry) {
            Map<String, Function<Identifier, ?>> names = byRegistry.get(registry);
            return names == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(names.keySet());
        }

        /**
         * Looks up the registries that own a type name. More than one registry can own it;
         * {@code event.custom} uses this to resolve global names.
         */
        public java.util.List<ResourceKey<? extends Registry<?>>> registriesOf(String typeName) {
            return byRegistry.entrySet().stream()
                    .filter(entry -> entry.getValue().containsKey(typeName))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        /**
         * Read-only view of the (type name -> builder class) pairs declared for one registry. It is input
         * for deriving builder contracts and declarations.
         */
        public Map<String, Class<? extends RegistryObjectBuilder<?>>> builderClassesOf(
                ResourceKey<? extends Registry<?>> registry) {
            return java.util.Collections.unmodifiableMap(builderClasses.getOrDefault(registry, Map.of()));
        }
    }

    /** The extension point definition, registered by NekoRegistryPointsPlugin. */
    public static final NekoPluginExtensionPoint<Contributor, RegistryTypesCollector, RegistryTypes> POINT =
            NekoPluginExtensionPoint.<Contributor, RegistryTypesCollector, RegistryTypes>builder(ID, Contributor.class)
                    .merge(POLICY)
                    .dependsOn(RegistryInfosPoint.POINT)
                    .initializer(context -> {
                        // Data dependency: the registry_infos output is already in place, as the dependsOn order guarantees.
                        context.result(RegistryInfosPoint.POINT);
                        return new RegistryTypesCollector();
                    })
                    .collector(Contributor::registerRegistryTypes)
                    .finish(collector -> new RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses))
                    .build();
}
