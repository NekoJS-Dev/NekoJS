package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;

public class BooleanComponent implements RecipeComponent<Boolean> {
    public static final RecipeComponentType<Boolean> BOOLEAN = RecipeComponentType.unit(
            ResourceLocation.fromNamespaceAndPath("kubejs", "boolean"), new BooleanComponent());
    @Override public RecipeComponentType<?> type() { return BOOLEAN; }
    @Override public Codec<Boolean> codec() { return Codec.BOOL; }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(Boolean.class); }
    @Override public boolean isEmpty(Boolean value) { return false; }
    @Override public Boolean wrap(RecipeScriptContext cx, Object from) {
        from = NativeComponentValues.scalar(from);
        if (from instanceof Boolean value) return value;
        if (from instanceof Number number) return number.doubleValue() != 0;
        if (from instanceof CharSequence text) return Boolean.parseBoolean(text.toString());
        throw new IllegalArgumentException("[NEKO-5361] Expected a recipe boolean");
    }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) {
        return NativeComponentValues.scalar(from) instanceof Boolean;
    }
    @Override public String toString() { return "boolean"; }
}
