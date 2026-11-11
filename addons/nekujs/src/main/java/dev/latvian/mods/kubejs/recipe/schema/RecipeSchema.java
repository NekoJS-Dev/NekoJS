package dev.latvian.mods.kubejs.recipe.schema;

import dev.latvian.mods.kubejs.recipe.RecipeKey;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Programmatic schema metadata consumed by the canonical native recipe compiler. */
public class RecipeSchema {
    public KubeRecipeFactory recipeFactory = KubeRecipeFactory.DEFAULT;
    public ResourceLocation typeOverride;
    public final List<RecipeKey<?>> keys;
    public final List<RecipeKey<?>> includedKeys;
    public final Map<RecipeKey<?>, RecipeOptional<?>> keyOverrides;
    private final int minRequiredArguments;

    public RecipeSchema(Map<RecipeKey<?>, RecipeOptional<?>> keyOverrides, List<RecipeKey<?>> keys) {
        this.keys = List.copyOf(keys);
        this.keyOverrides = Map.copyOf(keyOverrides);
        this.includedKeys = this.keys.stream()
                .filter(key -> (!key.optional() || !key.excluded) && !this.keyOverrides.containsKey(key)).toList();
        HashSet<String> names = new HashSet<>();
        for (RecipeKey<?> key : this.keys) {
            if (!names.add(key.name)) throw new IllegalArgumentException("[NEKO-5322] Duplicate recipe schema key: " + key.name);
            if (key.alwaysWrite && key.optional() && key.optional.isDefault()) {
                throw new IllegalArgumentException("[NEKO-5324] Null-default recipe key cannot always write: " + key.name);
            }
        }
        for (RecipeKey<?> key : this.keyOverrides.keySet()) {
            if (!this.keys.contains(key)) throw new IllegalArgumentException("[NEKO-5327] Recipe key override is not declared: " + key.name);
        }
        int required = 0;
        boolean optional = false;
        for (RecipeKey<?> key : includedKeys) {
            if (key.optional()) optional = true;
            else {
                if (optional) throw new IllegalArgumentException("[NEKO-5323] Required recipe key follows an optional key: " + key.name);
                required++;
            }
        }
        minRequiredArguments = required;
    }

    public RecipeSchema(RecipeKey<?>... keys) { this(Map.of(), List.of(keys)); }
    public RecipeSchema factory(KubeRecipeFactory factory) { recipeFactory = Objects.requireNonNull(factory); return this; }
    public RecipeSchema typeOverride(ResourceLocation id) { typeOverride = Objects.requireNonNull(id); return this; }
    public int minRequiredArguments() { return minRequiredArguments; }

    @SuppressWarnings("unchecked")
    public <T> RecipeKey<T> getOptionalKey(String name) {
        for (RecipeKey<?> key : keys) if (key.name.equals(name)) return (RecipeKey<T>) key;
        return null;
    }

    public <T> RecipeKey<T> getKey(String name) {
        RecipeKey<T> key = getOptionalKey(name);
        if (key == null) throw new IllegalArgumentException("[NEKO-5328] Unknown recipe schema key: " + name);
        return key;
    }
}
