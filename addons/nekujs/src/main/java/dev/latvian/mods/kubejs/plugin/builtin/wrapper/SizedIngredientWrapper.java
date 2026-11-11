package dev.latvian.mods.kubejs.plugin.builtin.wrapper;

import dev.latvian.mods.kubejs.recipe.component.SizedIngredientComponent;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** Native sized-ingredient conversion for legacy addon calls. */
public interface SizedIngredientWrapper {
    TypeInfo TYPE_INFO = TypeInfo.of(SizedIngredient.class);

    static SizedIngredient of(SizedIngredient ingredient) { return ingredient; }
    static SizedIngredient of(Ingredient ingredient, int count) { return new SizedIngredient(ingredient, count); }
    static SizedIngredient ofTag(TagKey<Item> tag, int count) { return SizedIngredient.of(tag, count); }

    static SizedIngredient wrap(Context context, Object input) {
        input = Wrapper.unwrapped(input);
        if (input instanceof SizedIngredient ingredient) return ingredient;
        if (input instanceof Ingredient ingredient) return of(ingredient, 1);
        if (input instanceof ItemStack stack) return of(Ingredient.of(stack), stack.getCount());
        if (input instanceof ItemLike item) return of(Ingredient.of(item), 1);
        return SizedIngredientComponent.FLAT.instance().wrap(() -> context, input);
    }
}
