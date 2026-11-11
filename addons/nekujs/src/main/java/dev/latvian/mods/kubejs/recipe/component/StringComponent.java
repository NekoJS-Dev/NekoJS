package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public record StringComponent(RecipeComponentType<?> type, Codec<String> codec, boolean allowEmpty) implements RecipeComponent<String> {
    public static final RecipeComponentType<String> STRING = create("string", ExtraCodecs.NON_EMPTY_STRING, false);
    public static final RecipeComponentType<String> OPTIONAL_STRING = create("optional_string", Codec.STRING, true);
    public static final RecipeComponentType<String> ID = create("id", Codec.STRING.validate(value -> ResourceLocation.read(value).map(ResourceLocation::toString)), false);
    private static RecipeComponentType<String> create(String name, Codec<String> codec, boolean allowEmpty) {
        return RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", name), type -> new StringComponent(type, codec, allowEmpty));
    }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(String.class); }
    @Override public String wrap(RecipeScriptContext cx, Object from) {
        return String.valueOf(NativeComponentValues.scalar(from));
    }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) {
        from = NativeComponentValues.scalar(from);
        return from instanceof Character || from instanceof CharSequence;
    }
    @Override public boolean isEmpty(String value) { return value.isEmpty(); }
    @Override public String toString() { return type.toString(); }
}
