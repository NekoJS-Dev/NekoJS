package dev.latvian.mods.kubejs.recipe.schema;

/** A typed default; the distinguished null default is omitted unless explicitly assigned. */
@FunctionalInterface
public interface RecipeOptional<T> {
    RecipeOptional<?> DEFAULT = new Unit.Impl<>(null);

    T getDefaultValue(RecipeSchemaType type);
    default T getInformativeValue() { return null; }
    default boolean isDefault() { return this == DEFAULT; }

    @SuppressWarnings("unchecked")
    static <T> RecipeOptional<T> unit(T value) {
        return value == null ? (RecipeOptional<T>) DEFAULT : new Unit.Impl<>(value);
    }

    interface Unit<T> extends RecipeOptional<T> {
        T value();
        @Override default T getDefaultValue(RecipeSchemaType type) { return value(); }
        @Override default T getInformativeValue() { return value(); }
        record Impl<T>(T value) implements Unit<T> { }
    }

    record Constant<T>(T value) implements Unit<T> { }
}
