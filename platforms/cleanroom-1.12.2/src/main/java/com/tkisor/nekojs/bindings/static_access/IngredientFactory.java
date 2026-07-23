package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.item.IngredientJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.crafting.Ingredient;

public class IngredientFactory {
    public IngredientJS of(Value... values) {
        IngredientJS wrapper = new IngredientJS();
        if (values != null) {
            for (Value value : values) {
                Ingredient ingredient = IngredientResolver.fromValue(value);
                wrapper.or(ingredient);
            }
        }
        return wrapper;
    }

    public IngredientJS item(String id) {
        return new IngredientJS(id);
    }

    public IngredientJS ore(String name) {
        return new IngredientJS("ore:" + name);
    }

    public IngredientJS any(Ingredient... ingredients) {
        IngredientJS wrapper = new IngredientJS();
        for (Ingredient ingredient : ingredients) {
            wrapper.or(ingredient);
        }
        return wrapper;
    }
}
