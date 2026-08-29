//? if neoforge {
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
//? if <26 {
/*import com.google.gson.JsonElement;
*///?}

/**
 * NeoForge 26.x 的只读注册表查询实现。
 *
 * <p>与 1.21.1 不同，26.x 的 {@code DefaultedRegistry} 没有 {@code asLookup()}，因此从
 * vanilla {@code RegistryAccess} 取 {@link Registry}（与 {@code IngredientResolver} 一致）。
 */
public final class NeoForgeRegistryQueryService implements RegistryQueryService {
    public static final NeoForgeRegistryQueryService INSTANCE = new NeoForgeRegistryQueryService();

    private static final RegistryAccess REGISTRY_ACCESS =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    /**
     * registryId → ResourceKey 的进程级快照缓存：{@link #REGISTRY_ACCESS} 是 static final、
     * 只包内置注册表（{@code BuiltInRegistries.REGISTRY}），其 key 集合在 mod 加载后即冻结
     * （datapack 重载只替换动态注册表内容，不会增删内置 key），而本服务的调用方是脚本侧
     * {@code Registry.get(...)}（仅脚本执行期可达）。首次 resolveKey 时一次性建表，
     * 之后未知 id 直接查表为 null，免去每次 ~500 key 的线性扫描与 toString 分配。
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
//? if >=26 {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
//?} else {
/*        Identifier location = tryParse(id);
        if (location == null) {
*///?}
            return false;
        }
        return resolveKey(registryId)
                .flatMap(this::registry)
//? if >=26 {
                .map(registryValue -> registryValue.containsKey(identifier))
//?} else {
/*                .map(registryValue -> registryValue.containsKey(location))
*///?}
                .orElse(false);
    }

    @Override
    public List<String> tag(String registryId, String tagId) {
//? if >=26 {
        Identifier identifier = Identifier.tryParse(tagId);
        if (identifier == null) {
//?} else {
/*        Identifier location = tryParse(tagId);
        if (location == null) {
*///?}
            return List.of();
        }
        return resolveKey(registryId)
                .flatMap(this::registry)
//? if >=26 {
                .flatMap(registryValue -> tagInRegistry(registryValue, identifier))
//?} else {
/*                .flatMap(registryValue -> tagInRegistry(registryValue, location))
*///?}
                .orElseGet(List::of);
    }

    // 泛型捕获：Registry<T> 的 key() 返回 ResourceKey<? extends Registry<T>>，
    // 恰好满足 TagKey.create 的签名，通配符在具体化后自然闭合。
    private static <T> Optional<List<String>> tagInRegistry(Registry<T> registry, Identifier location) {
        TagKey<T> tagKey = TagKey.create(registry.key(), location);
//? if >=26 {
        return registry.get(tagKey)
//?} else {
/*        return registry.getTag(tagKey)
*///?}
                .map(holders -> holders.stream()
                        .map(holder -> holder.unwrapKey()
//? if >=26 {
                                .map(key -> key.identifier().toString())
//?} else {
/*                                .map(key -> key.location().toString())
*///?}
                                .orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    @Override
    public List<String> dataMapIds(String registryId) {
//? if >=26 {
        Identifier registryIdentifier = Identifier.tryParse(registryId);
        if (registryIdentifier == null) {
//?} else {
/*        Identifier registryLocation = tryParse(registryId);
        if (registryLocation == null) {
*///?}
            return List.of();
        }
        return RegistryManager.getDataMaps().entrySet().stream()
//? if >=26 {
                .filter(entry -> entry.getKey().identifier().equals(registryIdentifier))
//?} else {
/*                .filter(entry -> entry.getKey().location().equals(registryLocation))
*///?}
                .flatMap(entry -> entry.getValue().keySet().stream())
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String dataMapValue(String registryId, String dataMapTypeId, String id) {
//? if >=26 {
        Identifier registryIdentifier = Identifier.tryParse(registryId);
        Identifier typeIdentifier = Identifier.tryParse(dataMapTypeId);
        Identifier entryIdentifier = Identifier.tryParse(id);
        if (registryIdentifier == null || typeIdentifier == null || entryIdentifier == null) {
//?} else {
/*        Identifier registryLocation = tryParse(registryId);
        Identifier typeLocation = tryParse(dataMapTypeId);
        Identifier entryLocation = tryParse(id);
        if (registryLocation == null || typeLocation == null || entryLocation == null) {
*///?}
            return null;
        }
        Optional<ResourceKey<? extends Registry<?>>> key = resolveKey(registryId);
        if (key.isEmpty()) {
            return null;
        }
//? if >=26 {
        DataMapType<?, ?> type = RegistryManager.getDataMap(rawKey(key.get()), typeIdentifier);
//?} else {
/*        DataMapType<?, ?> type = RegistryManager.getDataMap(rawKey(key.get()), typeLocation);
*///?}
        if (type == null) {
            return null;
        }
        Registry<?> registryValue = registry(key.get()).orElse(null);
        if (registryValue == null) {
            return null;
        }
//? if >=26 {
        Object value = dataMapValueFor(registryValue, type, entryIdentifier);
//?} else {
/*        Object value = dataMapValueFor(registryValue, type, entryLocation);
*///?}
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
        // RegistryAccess.lookup 返回 Optional<Registry<E>>，E 是捕获类型；
        // 这里只需要 Registry<?> 的能力，实参收窄到 raw 后 E 推断为 Object，无需再转 Optional。
//? if >=26 {
        return REGISTRY_ACCESS.lookup((ResourceKey) key);
//?} else {
/*        return REGISTRY_ACCESS.registry((ResourceKey) key);
*///?}
    }

    private Optional<ResourceKey<? extends Registry<?>>> resolveKey(String registryId) {
        return Optional.ofNullable(registryKeysById().get(registryId));
    }

    private static Map<String, ResourceKey<? extends Registry<?>>> registryKeysById() {
        Map<String, ResourceKey<? extends Registry<?>>> map = registryKeysById;
        if (map == null) {
            Map<String, ResourceKey<? extends Registry<?>>> built = new ConcurrentHashMap<>();
//? if >=26 {
            REGISTRY_ACCESS.listRegistryKeys().forEach(key -> built.put(key.identifier().toString(), key));
//?} else {
/*            REGISTRY_ACCESS.listRegistries().forEach(key -> built.put(key.location().toString(), key));
*///?}
            registryKeysById = map = built;
        }
        return map;
    }
//? if <26 {
/*    private static Identifier tryParse(String value) {
        try {
            return Identifier.parse(value);
        } catch (RuntimeException error) {
            return null;
        }
    }
*///?}
}
//?}
