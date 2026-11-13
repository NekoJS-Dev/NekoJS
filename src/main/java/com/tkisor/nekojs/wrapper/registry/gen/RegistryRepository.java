package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Platform-independent builder repository, the collecting side of "collect first, build later":
 * while scripts are collected it only holds {@link RegistryObjectBuilder} instances and derived
 * entries. Object creation is deferred to the platform adapter, which drains each registry's entries
 * during that registry's pass by registering suppliers.
 *
 * <p>Object-level conflicts fail fast: a duplicate id in the same registry throws
 * {@link IllegalStateException} during collection, whether the clash is between two main objects,
 * between a main object and a derived entry, or between two derived entries.
 *
 * <p>A main entry carries request metadata: {@link Entry#typeName()} for the named or default type
 * and {@link Entry#origin()} for where the request came from (sugar method, named type, custom or
 * bare supplier). The Runtime drain and diagnostics use it to report the definition, registry,
 * node and error source.
 */
public final class RegistryRepository {

    /** Derived entry: target registry, object id, lazy builder and the id of the source object, for
     * diagnostics. */
    public record Additional(
            ResourceKey<? extends Registry<?>> registry,
            Identifier id,
            Supplier<?> supplier,
            Identifier source) {}

    /** Main entry: the builder plus request metadata. */
    public record Entry(RegistryObjectBuilder<?> builder, String typeName, String origin) {}

    private final Map<ResourceKey<? extends Registry<?>>, LinkedHashMap<Identifier, Entry>> byRegistry =
            new LinkedHashMap<>();
    private final Map<ResourceKey<? extends Registry<?>>, List<Additional>> additionalByRegistry = new LinkedHashMap<>();

    /** Adds one main builder, keeping collection order. A duplicate id in the same registry throws
     * {@link IllegalStateException}. */
    public void add(ResourceKey<? extends Registry<?>> registry, RegistryObjectBuilder<?> builder) {
        add(registry, builder, defaultTypeName(builder), "direct");
    }

    /** Adds one main builder together with its type name and request origin (sugar method, named type,
     * custom or bare supplier). */
    public void add(
            ResourceKey<? extends Registry<?>> registry, RegistryObjectBuilder<?> builder, String typeName, String origin) {
        LinkedHashMap<Identifier, Entry> builders =
                byRegistry.computeIfAbsent(registry, key -> new LinkedHashMap<>());
        Entry previous = builders.get(builder.id);
        if (previous != null) {
            throw new IllegalStateException("Duplicate registration '" + builder.id + "' in registry '" + name(registry)
                    + "' (first from " + previous.origin() + ", second from " + origin + ")");
        }
        builder.freezeDefinition();
        builders.put(builder.id, new Entry(builder, typeName, origin));
    }

    /** Drains all main entries of one registry: removes them and returns a frozen snapshot in collection
     * order. This is the collecting-side guarantee that each pass drains once. */
    public List<Entry> drain(ResourceKey<? extends Registry<?>> registry) {
        LinkedHashMap<Identifier, Entry> builders = byRegistry.remove(registry);
        return builders == null ? List.of() : List.copyOf(builders.values());
    }

    /** Adds one derived entry, delivered by a builder's {@code handleAdditionalObjects} callback. A
     * conflict fails fast. */
    public void addAdditional(
            ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier, Identifier source) {
        LinkedHashMap<Identifier, Entry> builders = byRegistry.get(registry);
        if (builders != null && builders.containsKey(id)) {
            throw new IllegalStateException("Duplicate registration '" + id + "' in registry '" + name(registry)
                    + "' (additional object of '" + source + "' collides with a main entry)");
        }
        List<Additional> list = additionalByRegistry.computeIfAbsent(registry, key -> new ArrayList<>());
        if (list.stream().anyMatch(additional -> additional.id().equals(id))) {
            throw new IllegalStateException("Duplicate registration '" + id + "' in registry '" + name(registry)
                    + "' (additional object of '" + source + "' collides with another additional object)");
        }
        list.add(new Additional(registry, id, supplier, source));
    }

    /** Drains all derived entries of one registry: removes them and returns a frozen snapshot in delivery
     * order. */
    public List<Additional> drainAdditional(ResourceKey<? extends Registry<?>> registry) {
        List<Additional> list = additionalByRegistry.remove(registry);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Main objects no pass has drained yet, for the load-complete check: a misspelled registry name or a
     * loader that never fires that pass. */
    public Map<ResourceKey<? extends Registry<?>>, List<Entry>> undrained() {
        Map<ResourceKey<? extends Registry<?>>, List<Entry>> copy = new LinkedHashMap<>();
        byRegistry.forEach((registry, builders) -> copy.put(registry, List.copyOf(builders.values())));
        return copy;
    }

    /** Derived entries not yet delivered, for the load-complete check: the target registry's pass ran
     * before the source registry's. */
    public Map<ResourceKey<? extends Registry<?>>, List<Additional>> undeliveredAdditional() {
        Map<ResourceKey<? extends Registry<?>>, List<Additional>> copy = new LinkedHashMap<>();
        additionalByRegistry.forEach((registry, list) -> copy.put(registry, List.copyOf(list)));
        return copy;
    }

    /** Whether the main objects and the derived entries are both empty. */
    public boolean isEmpty() {
        return byRegistry.isEmpty() && additionalByRegistry.isEmpty();
    }

    private static String defaultTypeName(RegistryObjectBuilder<?> builder) {
        return builder.getClass().getSimpleName();
    }

    private static String name(ResourceKey<? extends Registry<?>> registry) {
        return registry.identifier().toString();
    }
}
