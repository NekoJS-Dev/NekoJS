package dev.latvian.mods.kubejs.registry;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Builder metadata is available without pretending that native registry installation occurred. */
public abstract class BuilderBase<T> implements Supplier<T> {
    public final ResourceLocation id;
    public ResourceKey<Registry<T>> registryKey;
    public String translationKey = "";
    public Component displayName;
    public boolean formattedDisplayName;
    public transient boolean dummyBuilder;
    public transient Set<ResourceLocation> defaultTags = new HashSet<>();
    protected T object;
    public BuilderBase(ResourceLocation id) { this.id = Objects.requireNonNull(id); }
    public abstract T createObject();
    public T transformObject(T value) { return value; }
    @Override public final T get() {
        if (object == null) throw new IllegalStateException("NEKO-4237: Builder has no registered object: " + id);
        return object;
    }
    public BuilderBase<T> tag(ResourceLocation[] tags) { defaultTags.addAll(Arrays.asList(tags)); return this; }
    public BuilderBase<T> translationKey(String key) { translationKey = Objects.requireNonNull(key); return this; }
    public BuilderBase<T> displayName(Component name) { displayName = name; return this; }
    public BuilderBase<T> formattedDisplayName() { formattedDisplayName = true; return this; }
    public BuilderBase<T> formattedDisplayName(Component name) { return formattedDisplayName().displayName(name); }
    public ResourceLocation newID(String pre, String post) { return id.withPath(pre + id.getPath() + post); }
    public String getTranslationKeyGroup() { return registryKey == null ? "unknown_registry" : registryKey.location().getPath().replace('/', '.'); }
    public T createTransformedObject() {
        object = Objects.requireNonNull(transformObject(createObject()));
        return object;
    }
    @Override public String toString() { return getClass().getSimpleName() + "[" + id + "]"; }
}
