package dev.latvian.mods.kubejs.recipe.schema;

import dev.latvian.mods.kubejs.util.RegistryAccessContainer;

@FunctionalInterface
public interface RegistryAwareSchema {
    RecipeSchema create(RegistryAccessContainer cx);
}
