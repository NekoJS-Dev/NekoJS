package dev.latvian.mods.kubejs.recipe.schema;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Identity and serializer metadata for a programmatically registered native profile. */
public class RecipeSchemaType {
    public final RecipeNamespace namespace;
    public final ResourceLocation id;
    public final RecipeSchema schema;
    public final ResourceKey<RecipeSerializer<?>> serializerKey;
    public final String serializerType;
    public RecipeSchemaType parent;

    public RecipeSchemaType(RecipeNamespace namespace, ResourceLocation id, RecipeSchema schema) {
        this.namespace = namespace;
        this.id = id;
        this.schema = schema;
        serializerKey = ResourceKey.create(Registries.RECIPE_SERIALIZER, schema.typeOverride == null ? id : schema.typeOverride);
        serializerType = serializerKey.location().toString();
    }

    public RecipeSerializer<?> getSerializer() {
        RecipeSerializer<?> serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(serializerKey);
        if (serializer == null) throw new IllegalStateException("[NEKO-5330] Recipe serializer is not registered: " + serializerType);
        return serializer;
    }

    @Override public String toString() { return id.toString(); }
}
