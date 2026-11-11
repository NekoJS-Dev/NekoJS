package dev.latvian.mods.kubejs.recipe.schema;

public class RecipeNamespace {
    private final RecipeSchemaStorage storage;
    private final String namespace;

    public RecipeNamespace() { this(null, null); }
    public RecipeNamespace(RecipeSchemaStorage storage, String namespace) {
        this.storage = storage;
        this.namespace = namespace;
    }

    public void register(String id, RecipeSchema schema) {
        if (storage == null) throw new IllegalStateException("NEKO-4246: Recipe namespace has no native schema catalog: " + id);
        storage.registerSchema(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, id), schema);
    }
    public void register(String id, RegistryAwareSchema schema) {
        throw new UnsupportedOperationException("NEKO-4247: Registry-aware recipe namespace registration is unsupported: " + id);
    }
}
