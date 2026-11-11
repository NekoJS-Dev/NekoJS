package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.js.type_adapter.IngredientAdapter;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ItemMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record IngredientComponent(RecipeComponentType<?> type, Codec<Ingredient> codec, boolean allowEmpty) implements RecipeComponent<Ingredient> {
    private static final IngredientAdapter CONVERTER = new IngredientAdapter();
    public static final RecipeComponentType<Ingredient> INGREDIENT = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "ingredient"), type -> new IngredientComponent(type, Ingredient.CODEC_NONEMPTY, false));
    public static final RecipeComponentType<Ingredient> OPTIONAL_INGREDIENT = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "optional_ingredient"), type -> new IngredientComponent(type, Ingredient.CODEC, true));
    @Override public TypeInfo typeInfo() { return TypeInfo.of(Ingredient.class); }
    @Override public Ingredient wrap(RecipeScriptContext cx, Object from) { return CONVERTER.apply(NativeComponentValues.value(from)); }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) { return CONVERTER.test(NativeComponentValues.value(from)); }
    @Override public boolean matches(RecipeMatchContext cx, Ingredient value, ReplacementMatchInfo match) {
        return match.match() instanceof ItemMatch item && !value.isEmpty() && item.matches(cx, value, match.exact());
    }
    @Override public boolean isEmpty(Ingredient value) { return value.isEmpty() || value.hasNoItems(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, Ingredient value) {
        for (Ingredient.Value entry : value.getValues()) {
            if (entry instanceof Ingredient.TagValue tag) { builder.append(tag.tag().location()); return; }
        }
        ItemStack[] stacks = value.getItems();
        if (stacks.length > 0 && !stacks[0].isEmpty()) builder.append(BuiltInRegistries.ITEM.getKey(stacks[0].getItem()));
    }
    @Override public String toString() { return type.toString(); }
}
