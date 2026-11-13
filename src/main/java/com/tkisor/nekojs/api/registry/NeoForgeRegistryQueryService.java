// Not ported to fabric yet: deferred to the LoaderBridge fabric port
//? if neoforge {
//~ mc_legacy_api
// This file turns on file-level mc_legacy_api to absorb the version differences below. The
// thresholds on both sides are verified: the 1.21.1 evaluated form has no .identifier(), and the
// 26.x form has neither .identifier() nor .listRegistryKeys(). Two pure renames are covered:
// .identifier() to .identifier() and .listRegistryKeys() to .listRegistryKeys().
// tryParse is promoted to a method neutral for both sides: Identifier.parse in 26.x throws on
// invalid input just like 1.21.1 (26.x also has a non-throwing tryParse, which this class used in
// its original 26.x form; the catch version here is equivalent).
// The 2 remaining guards are real API renames that a rename table cannot express safely: Registry
// get/getTag and RegistryAccess lookup/registry.
package com.tkisor.nekojs.api.registry;

import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.IRegistryExtension;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only registry query implementation for NeoForge 26.x.
 *
 * <p>Unlike 1.21.1, {@code DefaultedRegistry} in 26.x has no {@code asLookup()}, so this class
 * takes the {@link Registry} from the vanilla {@code RegistryAccess}, the same way
 * {@code IngredientResolver} does.
 */
public final class NeoForgeRegistryQueryService implements RegistryQueryService {
    public static final NeoForgeRegistryQueryService INSTANCE = new NeoForgeRegistryQueryService();

    private static final RegistryAccess REGISTRY_ACCESS =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    /**
     * Process-wide snapshot cache from registry id to ResourceKey. {@link #REGISTRY_ACCESS} is
     * static final and wraps only the built-in registries ({@code BuiltInRegistries.REGISTRY}),
     * whose key set is frozen once mods load; a datapack reload only replaces dynamic registry
     * contents and never adds or removes built-in keys. Callers are scripts going through
     * {@code Registry.get(...)}, which only runs during script execution. The table is built once
     * on the first resolveKey; after that an unknown id resolves to null with one table lookup,
     * instead of a linear scan over ~500 keys plus a toString allocation on every call.
     */
    private static volatile Map<String, ResourceKey<? extends Registry<?>>> registryKeysById;

    private NeoForgeRegistryQueryService() {
    }

    @Override
    public boolean hasRegistry(String registryId) {
        return resolveKey(registryId).isPresent();
    }

    @Override
    public List<String> all(String registryId) {
        return resolveKey(registryId)
                .map(key -> registry(key)
                        .map(Registry::keySet)
                        .orElseGet(Set::of)
                        .stream()
                        .map(Identifier::toString)
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public boolean has(String registryId, String id) {
        Identifier identifier = tryParse(id);
        if (identifier == null) {
            return false;
        }
        return resolveKey(registryId)
                .flatMap(this::registry)
                .map(registryValue -> registryValue.containsKey(identifier))
                .orElse(false);
    }

    @Override
    public List<String> tag(String registryId, String tagId) {
        Identifier identifier = tryParse(tagId);
        if (identifier == null) {
            return List.of();
        }
        return resolveKey(registryId)
                .flatMap(this::registry)
                .flatMap(registryValue -> tagInRegistry(registryValue, identifier))
                .orElseGet(List::of);
    }

    // Generic capture: key() on Registry<T> returns ResourceKey<? extends Registry<T>>, which fits
    // TagKey.create's signature exactly, and the wildcard closes on its own once concretized.
    private static <T> Optional<List<String>> tagInRegistry(Registry<T> registry, Identifier location) {
        TagKey<T> tagKey = TagKey.create(registry.key(), location);
//? if >=26 {
        return registry.get(tagKey)
//?} else {
        /*return registry.getTag(tagKey)
*///?}
                .map(holders -> holders.stream()
                        .map(holder -> holder.unwrapKey()
                                .map(key -> key.identifier().toString())
                                .orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    @Override
    public List<String> dataMapIds(String registryId) {
        Identifier registryIdentifier = tryParse(registryId);
        if (registryIdentifier == null) {
            return List.of();
        }
        return RegistryManager.getDataMaps().entrySet().stream()
                .filter(entry -> entry.getKey().identifier().equals(registryIdentifier))
                .flatMap(entry -> entry.getValue().keySet().stream())
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String dataMapValue(String registryId, String dataMapTypeId, String id) {
        Identifier registryIdentifier = tryParse(registryId);
        Identifier typeIdentifier = tryParse(dataMapTypeId);
        Identifier entryIdentifier = tryParse(id);
        if (registryIdentifier == null || typeIdentifier == null || entryIdentifier == null) {
            return null;
        }
        Optional<ResourceKey<? extends Registry<?>>> key = resolveKey(registryId);
        if (key.isEmpty()) {
            return null;
        }
        DataMapType<?, ?> type = RegistryManager.getDataMap(rawKey(key.get()), typeIdentifier);
        if (type == null) {
            return null;
        }
        Registry<?> registryValue = registry(key.get()).orElse(null);
        if (registryValue == null) {
            return null;
        }
        Object value = dataMapValueFor(registryValue, type, entryIdentifier);
        return value == null ? null : encodeValue(type, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object dataMapValueFor(Registry<?> registryValue, DataMapType<?, ?> type, Identifier entryLocation) {
        Map<ResourceKey<?>, ?> values = ((IRegistryExtension) registryValue).getDataMap((DataMapType) type);
        return values.get(ResourceKey.create(registryValue.key(), entryLocation));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String encodeValue(DataMapType<?, ?> type, Object value) {
        try {
            var result = ((DataMapType) type).codec().encodeStart(JsonOps.INSTANCE, value);
            Optional<?> encoded = result.result();
            return encoded.map(e -> e.toString()).orElseGet(() -> value.toString());
        } catch (RuntimeException error) {
            return value.toString();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey rawKey(ResourceKey<?> key) {
        return key;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Registry<?>> registry(ResourceKey<? extends Registry<?>> key) {
        // RegistryAccess.lookup returns Optional<Registry<E>> with E a captured type. Only the
        // Registry<?> capability is needed here: narrowing the argument to raw lets E infer as
        // Object, so no Optional cast is needed.
        //? if >=26 {
        return REGISTRY_ACCESS.lookup((ResourceKey) key);
//?} else {
        /*return REGISTRY_ACCESS.registry((ResourceKey) key);
*///?}
    }

    private Optional<ResourceKey<? extends Registry<?>>> resolveKey(String registryId) {
        return Optional.ofNullable(registryKeysById().get(registryId));
    }

    private static Map<String, ResourceKey<? extends Registry<?>>> registryKeysById() {
        Map<String, ResourceKey<? extends Registry<?>>> map = registryKeysById;
        if (map == null) {
            Map<String, ResourceKey<? extends Registry<?>>> built = new ConcurrentHashMap<>();
            REGISTRY_ACCESS.listRegistryKeys().forEach(key -> built.put(key.identifier().toString(), key));
            registryKeysById = map = built;
        }
        return map;
    }

    /**
     * Fault-tolerant parsing that is neutral for both sides: {@code Identifier.parse} throws on
     * invalid input in 26.x and 1.21.1 alike.
     */
    private static Identifier tryParse(String value) {
        try {
            return Identifier.parse(value);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
//?}
