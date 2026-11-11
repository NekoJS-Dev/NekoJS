package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.MapCodec;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeTypeRegistryContext;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.function.Function;

public abstract class RecipeComponentType<T> {
    public static class Unit<T> extends RecipeComponentType<T> {
        private final RecipeComponent<T> instance;
        private final MapCodec<RecipeComponent<?>> mapCodec;
        private Unit(ResourceLocation id, Function<RecipeComponentType<T>, RecipeComponent<T>> factory) {
            super(id);
            instance = Objects.requireNonNull(factory.apply(this));
            mapCodec = MapCodec.unit(instance);
        }
        @Override public boolean isUnit() { return true; }
        @Override public final RecipeComponent<T> instance() { return instance; }
        @Override public MapCodec<RecipeComponent<?>> mapCodec(RecipeTypeRegistryContext ctx) { return mapCodec; }
    }

    public static class Dynamic<T> extends RecipeComponentType<T> {
        private final RecipeComponentCodecFactory<?> factory;
        private Dynamic(ResourceLocation id, RecipeComponentCodecFactory<?> factory) {
            super(id);
            this.factory = Objects.requireNonNull(factory);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override public MapCodec<RecipeComponent<?>> mapCodec(RecipeTypeRegistryContext ctx) {
            return (MapCodec) Objects.requireNonNull(factory.create(this, ctx));
        }
    }

    public static <T> Unit<T> unit(ResourceLocation id, Function<RecipeComponentType<T>, RecipeComponent<T>> factory) {
        return new Unit<>(id, factory);
    }
    public static <T> Unit<T> unit(ResourceLocation id, RecipeComponent<T> instance) {
        return new Unit<>(id, type -> instance);
    }
    public static <CT extends RecipeComponent<?>> RecipeComponentType<?> dynamic(ResourceLocation id, RecipeComponentCodecFactory<CT> factory) {
        return new Dynamic<>(id, factory);
    }
    public static <CT extends RecipeComponent<?>> RecipeComponentType<?> dynamic(ResourceLocation id, MapCodec<CT> codec) {
        return dynamic(id, (type, ctx) -> codec);
    }

    private final ResourceLocation id;
    public RecipeComponentType(ResourceLocation id) { this.id = Objects.requireNonNull(id); }
    public final ResourceLocation id() { return id; }
    @Override public final int hashCode() { return id.hashCode(); }
    @Override public final boolean equals(Object obj) { return obj == this || obj instanceof RecipeComponentType<?> type && id.equals(type.id); }
    @Override public final String toString() { return id.getNamespace().equals("kubejs") ? id.getPath() : id.toString(); }
    public boolean isUnit() { return false; }
    public RecipeComponent<T> instance() {
        throw new UnsupportedOperationException("NEKO-4233: Dynamic recipe component has no unit instance: " + id);
    }
    public abstract MapCodec<RecipeComponent<?>> mapCodec(RecipeTypeRegistryContext ctx);
    public RecipeKey<T> key(String name, ComponentRole role) { return instance().key(name, role); }
    public RecipeKey<T> inputKey(String name) { return key(name, ComponentRole.INPUT); }
    public RecipeKey<T> outputKey(String name) { return key(name, ComponentRole.OUTPUT); }
    public RecipeKey<T> otherKey(String name) { return key(name, ComponentRole.OTHER); }
}
