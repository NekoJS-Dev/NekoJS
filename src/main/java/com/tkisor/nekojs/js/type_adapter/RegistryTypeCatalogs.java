//~ mc_legacy_api
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Collects current registry literals from platform sources and published registry adapters. */
public final class RegistryTypeCatalogs {
    private RegistryTypeCatalogs() {}

    /** Returns the platform aliases without retaining registry state between catalog snapshots. */
    public static Map<String, Registry<?>> builtInSources() {
        Map<String, Registry<?>> sources = new LinkedHashMap<>();
        sources.put("Item", BuiltInRegistries.ITEM);
        sources.put("Block", BuiltInRegistries.BLOCK);
        sources.put("Fluid", BuiltInRegistries.FLUID);
        sources.put("EntityType", BuiltInRegistries.ENTITY_TYPE);
        sources.put("MobEffect", BuiltInRegistries.MOB_EFFECT);
        sources.put("Potion", BuiltInRegistries.POTION);
        sources.put("SoundEvent", BuiltInRegistries.SOUND_EVENT);
        sources.put("ParticleType", BuiltInRegistries.PARTICLE_TYPE);
        sources.put("BlockEntityType", BuiltInRegistries.BLOCK_ENTITY_TYPE);
        sources.put("CreativeModeTab", BuiltInRegistries.CREATIVE_MODE_TAB);
        return sources;
    }

    /**
     * Keeps platform aliases first and appends adapter sources in name order. Registry objects remain
     * owned by their existing callers; only current ids and tags are copied into the catalog.
     * Different registry objects cannot share an alias, even when their current contents are equal.
     */
    public static List<RegistryTypeCatalogEntry> snapshot(Map<String, Registry<?>> platformSources,
            List<JSTypeAdapter<?>> adapters, Function<Registry<?>, List<String>> tagIds) {
        List<RegistryTypeCatalogEntry> entries = new ArrayList<>();
        collectSources(platformSources, adapters).forEach((name, registry) -> entries.add(entry(name, registry, tagIds)));
        validateSymbols(entries);
        return List.copyOf(entries);
    }

    /**
     * Preserves an existing provider's explicit metadata and appends missing published adapter aliases.
     * Physical source identity is checked where known; legacy metadata does not carry that identity.
     */
    public static List<RegistryTypeCatalogEntry> append(Collection<RegistryTypeCatalogEntry> provided,
            Map<String, Registry<?>> platformSources, List<JSTypeAdapter<?>> adapters,
            Function<Registry<?>, List<String>> tagIds) {
        Map<String, Registry<?>> sources = collectSources(platformSources, adapters);
        Map<String, RegistryTypeCatalogEntry> entries = new LinkedHashMap<>();
        for (RegistryTypeCatalogEntry entry : provided) {
            RegistryTypeCatalogEntry previous = entries.putIfAbsent(entry.typeName(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new IllegalStateException("[NEKO-4052] Registry type catalog rejected alias '"
                        + entry.typeName() + "'; conflicting explicit metadata");
            }
        }
        Set<String> adapterNames = adapters.stream().filter(SimpleRegistryBasedAdapter.class::isInstance)
                .map(adapter -> ((SimpleRegistryBasedAdapter<?>) adapter).registryTypeName()).collect(Collectors.toSet());
        sources.forEach((name, registry) -> {
            if (adapterNames.contains(name) && !entries.containsKey(name)) entries.put(name, entry(name, registry, tagIds));
        });
        validateSymbols(entries.values());
        return List.copyOf(entries.values());
    }

    private static Map<String, Registry<?>> collectSources(Map<String, Registry<?>> platformSources,
            List<JSTypeAdapter<?>> adapters) {
        Map<String, Registry<?>> sources = new LinkedHashMap<>(platformSources);
        List<SimpleRegistryBasedAdapter<?>> registryAdapters = new ArrayList<>();
        for (JSTypeAdapter<?> adapter : adapters) {
            if (adapter instanceof SimpleRegistryBasedAdapter<?> registryAdapter) registryAdapters.add(registryAdapter);
        }
        registryAdapters.sort(Comparator.comparing((SimpleRegistryBasedAdapter<?> adapter) -> adapter.registryTypeName())
                .thenComparing(adapter -> adapter.sourceRegistry().key().identifier().toString()));
        for (SimpleRegistryBasedAdapter<?> adapter : registryAdapters) {
            Registry<?> previous = sources.putIfAbsent(adapter.registryTypeName(), adapter.sourceRegistry());
            if (previous != null && previous != adapter.sourceRegistry()) {
                throw new IllegalStateException("[NEKO-4052] Registry type catalog rejected alias '"
                        + adapter.registryTypeName() + "'; distinct registry sources: " + previous.key().identifier()
                        + " and " + adapter.sourceRegistry().key().identifier());
            }
        }
        return sources;
    }

    private static void validateSymbols(Collection<RegistryTypeCatalogEntry> entries) {
        Map<String, String> symbols = new LinkedHashMap<>();
        symbols.put("Namespace", "<namespaces>");
        for (RegistryTypeCatalogEntry entry : entries) {
            String name = entry.typeName();
            for (String symbol : List.of(name, name + "Tag")) {
                String previous = symbols.putIfAbsent(symbol, name);
                if (previous != null) {
                    throw new IllegalStateException("[NEKO-4052] Registry type catalog rejected symbol '" + symbol
                            + "'; conflicting aliases: " + previous + " and " + name);
                }
            }
        }
    }

    private static RegistryTypeCatalogEntry entry(String name, Registry<?> registry,
            Function<Registry<?>, List<String>> tagIds) {
        return new RegistryTypeCatalogEntry(name,
                registry.keySet().stream().map(Object::toString).sorted().toList(),
                tagIds.apply(registry).stream().distinct().sorted().toList());
    }
}
