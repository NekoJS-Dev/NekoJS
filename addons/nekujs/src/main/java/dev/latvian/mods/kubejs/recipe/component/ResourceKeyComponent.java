package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

public record ResourceKeyComponent<T>(RecipeComponentType<?> typeOverride, ResourceKey<? extends Registry<T>> registryKey,
        Codec<ResourceKey<T>> codec, TypeInfo typeInfo) implements RecipeComponent<ResourceKey<T>> {
    public static final RecipeComponentType<ResourceKey<Level>> DIMENSION = create("dimension_resource_key", Registries.DIMENSION, Level.class);
    public static final RecipeComponentType<ResourceKey<LootTable>> LOOT_TABLE = create("loot_table_resource_key", Registries.LOOT_TABLE, LootTable.class);
    private static <T> RecipeComponentType<ResourceKey<T>> create(String name, ResourceKey<? extends Registry<T>> registry, Class<T> valueType) {
        return RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", name), type -> new ResourceKeyComponent<>(type, registry,
                ResourceKey.codec(registry), TypeInfo.of(ResourceKey.class).withParams(TypeInfo.of(valueType))));
    }
    @Override public RecipeComponentType<?> type() { return typeOverride; }
    @Override public ResourceKey<T> wrap(RecipeScriptContext cx, Object from) {
        from = NativeComponentValues.scalar(from);
        if (from instanceof ResourceKey<?> key) {
            return key.cast(registryKey).orElseThrow(() -> new IllegalArgumentException("[NEKO-5364] Recipe resource key belongs to a different registry: " + key));
        }
        if (from instanceof ResourceLocation location) return ResourceKey.create(registryKey, location);
        if (!(from instanceof CharSequence text)) throw new IllegalArgumentException("[NEKO-5365] Expected a recipe resource identifier");
        return ResourceKey.create(registryKey, ResourceLocation.parse(text.toString()));
    }
    @Override public boolean isEmpty(ResourceKey<T> value) { return false; }
    @Override public void buildUniqueId(UniqueIdBuilder builder, ResourceKey<T> value) { builder.append(value.location()); }
    @Override public String toString() { return typeOverride.toString(); }
}
