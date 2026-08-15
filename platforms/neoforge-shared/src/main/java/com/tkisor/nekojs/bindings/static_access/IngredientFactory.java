package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.crafting.Ingredient;

public class IngredientFactory {
    public com.tkisor.nekojs.wrapper.item.IngredientJS of(Object... values) {
        com.tkisor.nekojs.wrapper.item.IngredientJS wrapper = new com.tkisor.nekojs.wrapper.item.IngredientJS();
        if (values != null) {
            for (Object value : values) {
                Ingredient ingredient = IngredientResolver.fromValue(toValue(value));
                wrapper.or(ingredient);
            }
        }
        return wrapper;
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
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

    /** 匹配所有已注册物品的真 wildcard（live AnyHolderSet）。 */
    public com.tkisor.nekojs.wrapper.item.IngredientJS all() {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(IngredientResolver.wildcard());
    }

    /** 取反：返回匹配「除 ingredient 外所有物品」的 DifferenceIngredient。 */
    public com.tkisor.nekojs.wrapper.item.IngredientJS not(Ingredient ingredient) {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(IngredientResolver.not(ingredient));
    }
}

