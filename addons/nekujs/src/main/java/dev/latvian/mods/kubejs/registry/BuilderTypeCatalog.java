package dev.latvian.mods.kubejs.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Startup hook sink. Captures real factories, but does not run registry installation. */
public final class BuilderTypeCatalog implements BuilderTypeRegistry {
    private static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("kubejs", "default");
    private final Map<ResourceKey<?>, BuilderType<?>> defaults = new LinkedHashMap<>();
    private final Map<ResourceKey<?>, Map<ResourceLocation, BuilderType<?>>> types = new LinkedHashMap<>();

    @Override public <T> void of(ResourceKey<Registry<T>> registry, Consumer<Callback<T>> callback) {
        Objects.requireNonNull(registry);
        Objects.requireNonNull(callback).accept(new Callback<>() {
            @Override public void addDefault(Class<? extends BuilderBase<? extends T>> builderType, BuilderFactory factory) {
                BuilderType<T> type = new BuilderType<>(DEFAULT, Objects.requireNonNull(builderType), Objects.requireNonNull(factory));
                if (defaults.putIfAbsent(registry, type) != null) {
                    throw new IllegalArgumentException("NEKO-4251: Duplicate default builder type: " + registry.location());
                }
            }
            @Override public void add(ResourceLocation id, Class<? extends BuilderBase<? extends T>> builderType, BuilderFactory factory) {
                BuilderType<T> type = new BuilderType<>(Objects.requireNonNull(id), Objects.requireNonNull(builderType), Objects.requireNonNull(factory));
                if (types.computeIfAbsent(registry, key -> new LinkedHashMap<>()).putIfAbsent(id, type) != null) {
                    throw new IllegalArgumentException("NEKO-4252: Duplicate named builder type: " + registry.location() + "/" + id);
                }
            }
        });
    }
    public Map<ResourceKey<?>, BuilderType<?>> defaultTypes() { return Collections.unmodifiableMap(defaults); }
    public Map<ResourceKey<?>, Map<ResourceLocation, BuilderType<?>>> namedTypes() {
        Map<ResourceKey<?>, Map<ResourceLocation, BuilderType<?>>> snapshot = new LinkedHashMap<>();
        types.forEach((registry, definitions) -> snapshot.put(registry, Collections.unmodifiableMap(definitions)));
        return Collections.unmodifiableMap(snapshot);
    }
    public void createRegistryObjects() {
        throw new UnsupportedOperationException("NEKO-4253: Builder catalog does not install native registry objects");
    }
}
