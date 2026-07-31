package com.tkisor.nekojs.api.registry;

import com.tkisor.nekojs.api.registry.RegistryQueryService;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * NeoForge 1.21.1 的只读注册表查询实现。
 *
 * <p>通过 {@link RegistryAccess#registry(ResourceKey)} 拿到具体化的 {@link Registry}，
 * 用其 {@code keySet()}/{@code get()}/{@code getTag()} 查询（1.21.1 的 {@code Registry}
 * 接口完整暴露这些能力）。
 */
public final class NeoForgeRegistryQueryService implements RegistryQueryService {
    public static final NeoForgeRegistryQueryService INSTANCE = new NeoForgeRegistryQueryService();

    private static final RegistryAccess REGISTRY_ACCESS =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

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
                        .map(ResourceLocation::toString)
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public boolean has(String registryId, String id) {
        ResourceLocation location = tryParse(id);
        if (location == null) {
            return false;
        }
        return resolveKey(registryId)
                .flatMap(this::registry)
                .map(registryValue -> registryValue.containsKey(location))
                .orElse(false);
    }

    @Override
    public List<String> tag(String registryId, String tagId) {
        ResourceLocation location = tryParse(tagId);
        if (location == null) {
            return List.of();
        }
        return resolveKey(registryId)
                .flatMap(this::registry)
                .flatMap(registryValue -> tagInRegistry(registryValue, location))
                .orElseGet(List::of);
    }

    // 泛型捕获：Registry<T> 的 key() 返回 ResourceKey<? extends Registry<T>>，
    // 恰好满足 TagKey.create 的签名，通配符在具体化后自然闭合。
    private static <T> Optional<List<String>> tagInRegistry(Registry<T> registry, ResourceLocation location) {
        TagKey<T> tagKey = TagKey.create(registry.key(), location);
        return registry.getTag(tagKey)
                .map(holders -> holders.stream()
                        .map(holder -> holder.unwrapKey()
                                .map(key -> key.location().toString())
                                .orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Registry<?>> registry(ResourceKey<? extends Registry<?>> key) {
        // RegistryAccess.registry 返回 Optional<Registry<E>>，E 是捕获类型；
        // 这里只需要 Registry<?> 的能力，显式收窄到通配符。
        return (Optional) REGISTRY_ACCESS.registry((ResourceKey) key);
    }

    private Optional<ResourceKey<? extends Registry<?>>> resolveKey(String registryId) {
        return REGISTRY_ACCESS.listRegistries()
                .filter(key -> key.location().toString().equals(registryId))
                .findFirst();
    }

    private static ResourceLocation tryParse(String value) {
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
