package dev.latvian.mods.kubejs.plugin.builtin.wrapper;

import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.world.item.crafting.Ingredient;

/** Native ingredient conversion for legacy addon calls. */
public interface IngredientWrapper {
    TypeInfo TYPE_INFO = TypeInfo.of(Ingredient.class);

    static Ingredient wrap(Context context, Object input) {
        input = Wrapper.unwrapped(input);
        if (input instanceof Ingredient ingredient) return ingredient;
        return IngredientComponent.INGREDIENT.instance().wrap(() -> context, input);
    }
}
