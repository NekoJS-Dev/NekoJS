package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.js.type_adapter.SizedIngredientAdapter;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ItemMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public record SizedIngredientComponent(RecipeComponentType<?> type, Codec<SizedIngredient> codec, boolean allowEmpty) implements RecipeComponent<SizedIngredient> {
    private static final SizedIngredientAdapter CONVERTER = new SizedIngredientAdapter();
    public static final RecipeComponentType<SizedIngredient> SIZED_INGREDIENT = create("sized_ingredient", SizedIngredient.NESTED_CODEC, false);
    public static final RecipeComponentType<SizedIngredient> OPTIONAL_SIZED_INGREDIENT = create("optional_sized_ingredient", SizedIngredient.NESTED_CODEC, true);
    public static final RecipeComponentType<SizedIngredient> FLAT = create("flat_sized_ingredient", SizedIngredient.FLAT_CODEC, false);
    public static final RecipeComponentType<SizedIngredient> OPTIONAL_FLAT = create("optional_flat_sized_ingredient", SizedIngredient.FLAT_CODEC, true);
    private static RecipeComponentType<SizedIngredient> create(String name, Codec<SizedIngredient> codec, boolean allowEmpty) {
        return RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", name), type -> new SizedIngredientComponent(type, codec, allowEmpty));
    }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(SizedIngredient.class); }
    @Override public SizedIngredient wrap(RecipeScriptContext cx, Object from) { return CONVERTER.apply(NativeComponentValues.value(from)); }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) { return CONVERTER.test(NativeComponentValues.value(from)); }
    @Override public boolean matches(RecipeMatchContext cx, SizedIngredient value, ReplacementMatchInfo match) {
        return match.match() instanceof ItemMatch item && item.matches(cx, value.ingredient(), match.exact());
    }
    @Override public boolean isEmpty(SizedIngredient value) { return value.count() <= 0 || value.ingredient().isEmpty(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, SizedIngredient value) {
        IngredientComponent.INGREDIENT.instance().buildUniqueId(builder, value.ingredient());
    }
    @Override public String toString() { return type.toString(); }
}
