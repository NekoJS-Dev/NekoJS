package dev.latvian.mods.kubejs.recipe;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.kubejs.recipe.schema.RecipeOptional;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;

/** Key metadata only; this class does not install recipe constructors or functions. */
public final class RecipeKey<T> {
    public final RecipeComponent<T> component;
    public final TypeInfo typeInfo;
    public final Codec<T> codec;
    public final String name;
    public final ComponentRole role;
    public final SequencedSet<String> names = new LinkedHashSet<>();
    public RecipeOptional<T> optional;
    public boolean excluded;
    public List<String> functionNames;
    public boolean alwaysWrite;
    public RecipeKey(RecipeComponent<T> component, String name, ComponentRole role) {
        this.component = Objects.requireNonNull(component);
        this.typeInfo = component.typeInfo();
        this.codec = component.codec();
        this.name = Objects.requireNonNull(name);
        this.role = Objects.requireNonNull(role);
        names.add(name);
    }
    public RecipeKey<T> alt(String name) { names.add(Objects.requireNonNull(name)); return this; }
    public RecipeKey<T> alt(String... names) { for (String name : names) alt(name); return this; }
    public RecipeKey<T> optional(T value) { return optional(RecipeOptional.unit(value)); }
    public RecipeKey<T> optional(RecipeOptional<T> value) { optional = Objects.requireNonNull(value); return this; }
    public RecipeKey<T> defaultOptional() { return optional(RecipeOptional.unit(null)); }
    public boolean optional() { return optional != null; }
    public RecipeKey<T> exclude() { excluded = true; return this; }
    public RecipeKey<T> noFunctions() { functionNames = List.of(); return this; }
    public RecipeKey<T> functionNames(List<String> names) { functionNames = List.copyOf(names); return this; }
    public RecipeKey<T> functionNames(String... names) { return functionNames(List.of(names)); }
    public RecipeKey<T> alwaysWrite() { alwaysWrite = true; return this; }

    public List<String> getValidFunctionNames() {
        List<String> valid = new ArrayList<>();
        for (String name : functionNames == null ? names : functionNames) {
            StringBuilder converted = new StringBuilder();
            for (String part : name.split("[:_/]")) {
                if (part.isEmpty()) continue;
                if (converted.isEmpty()) converted.append(part);
                else converted.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
            }
            if (converted.isEmpty() || !Character.isJavaIdentifierStart(converted.charAt(0))) continue;
            boolean identifier = true;
            for (int i = 1; i < converted.length(); i++) {
                if (!Character.isJavaIdentifierPart(converted.charAt(i))) { identifier = false; break; }
            }
            if (identifier) valid.add(converted.toString());
        }
        return List.copyOf(valid);
    }

    public String getPrimaryFunctionName() {
        List<String> valid = getValidFunctionNames();
        return valid.isEmpty() ? "" : valid.getFirst();
    }
    @Override public int hashCode() { return name.hashCode(); }
    @Override public String toString() { return name + ": " + component + (optional() ? "?" : ""); }
}
