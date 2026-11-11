package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.type.TypeInfo;

/** Bounded component contract: codecs and metadata work; unsupported execution fails explicitly. */
public interface RecipeComponent<T> {
    RecipeComponentType<?> type();
    Codec<T> codec();
    TypeInfo typeInfo();

    default RecipeKey<T> key(String name, ComponentRole role) { return new RecipeKey<>(this, name, role); }
    default RecipeKey<T> inputKey(String name) { return key(name, ComponentRole.INPUT); }
    default RecipeKey<T> outputKey(String name) { return key(name, ComponentRole.OUTPUT); }
    default RecipeKey<T> otherKey(String name) { return key(name, ComponentRole.OTHER); }
    default ListRecipeComponent<T> asList() { return ListRecipeComponent.create(this, false, false); }
    default ListRecipeComponent<T> asListOrSelf() { return ListRecipeComponent.create(this, true, false); }
    default ListRecipeComponent<T> asConditionalList() { return ListRecipeComponent.create(this, false, true); }
    default ListRecipeComponent<T> asConditionalListOrSelf() { return ListRecipeComponent.create(this, true, true); }
    default RecipeComponent<T> orSelf() { return this; }
    default boolean hasPriority(RecipeMatchContext cx, Object from) { return false; }

    @SuppressWarnings("unchecked")
    default T wrap(RecipeScriptContext cx, Object from) {
        return (T) cx.cx().jsToJava(from, typeInfo());
    }

    default boolean matches(RecipeMatchContext cx, T value, ReplacementMatchInfo match) {
        throw new UnsupportedOperationException("NEKO-4230: Recipe component matching is unsupported for " + type());
    }
    default T replace(RecipeScriptContext cx, T original, ReplacementMatchInfo match, Object with) {
        throw new UnsupportedOperationException("NEKO-4231: Recipe component replacement is unsupported for " + type());
    }
    default boolean allowEmpty() { return false; }
    default boolean isEmpty(T value) {
        throw new UnsupportedOperationException("NEKO-4232: Recipe component emptiness is unsupported for " + type());
    }
    default void buildUniqueId(UniqueIdBuilder builder, T value) { builder.append(value.toString()); }
}
