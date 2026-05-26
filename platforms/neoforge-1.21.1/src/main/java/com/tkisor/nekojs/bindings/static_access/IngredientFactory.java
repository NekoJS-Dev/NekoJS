package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.crafting.Ingredient;

public class IngredientFactory {
    public com.tkisor.nekojs.wrapper.item.IngredientJS of(Value... values) {
        com.tkisor.nekojs.wrapper.item.IngredientJS wrapper = new com.tkisor.nekojs.wrapper.item.IngredientJS();
        if (values != null) {
            for (Value value : values) {
                Ingredient ingredient = IngredientResolver.fromValue(value);
                wrapper.or(ingredient);
            }
        }
        return wrapper;
    }

    public com.tkisor.nekojs.wrapper.item.IngredientJS item(String id) {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(id);
    }

    public com.tkisor.nekojs.wrapper.item.IngredientJS tag(String id) {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(id.startsWith("#") ? id : "#" + id);
    }

    public com.tkisor.nekojs.wrapper.item.IngredientJS any(Ingredient... ingredients) {
        com.tkisor.nekojs.wrapper.item.IngredientJS wrapper = new com.tkisor.nekojs.wrapper.item.IngredientJS();
        for (Ingredient ingredient : ingredients) {
            wrapper.or(ingredient);
        }
        return wrapper;
    }

    public com.tkisor.nekojs.wrapper.item.IngredientJS not(Ingredient ingredient) {
        throw new UnsupportedOperationException("Ingredient.not requires a wildcard ingredient, which is not supported yet. Use base.except(ingredient) instead.");
    }
}

