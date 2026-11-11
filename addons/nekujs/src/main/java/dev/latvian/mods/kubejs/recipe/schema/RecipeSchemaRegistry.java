package dev.latvian.mods.kubejs.recipe.schema;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public class RecipeSchemaRegistry implements KubeEvent {
    private final RecipeSchemaStorage storage;
    public RecipeSchemaRegistry(RecipeSchemaStorage storage) { this.storage = Objects.requireNonNull(storage); }
    public RecipeNamespace namespace(String namespace) {
        return new RecipeNamespace(storage, namespace);
    }
    public void register(ResourceLocation id, RecipeSchema schema) {
        storage.registerSchema(id, schema);
    }
    public void register(ResourceLocation id, RegistryAwareSchema schema) {
        throw new UnsupportedOperationException("NEKO-4250: Registry-aware recipe schema registration is unsupported: " + id);
    }
}
