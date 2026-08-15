package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.wrapper.item.IngredientJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.crafting.Ingredient;

public class IngredientFactory {
    public IngredientJS of(Object... values) {
        IngredientJS wrapper = new IngredientJS();
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

    /**
     * 匹配所有已注册物品的近似 wildcard。
     * 1.12.2 枚举当前注册表快照（无原生 wildcard），脚本注册后新增的物品不包含在内。
     */
    public IngredientJS all() {
        return new IngredientJS(IngredientResolver.wildcard());
    }

    /**
     * 取反 ingredient。1.12.2 无原生 negation ingredient 类型（无 DifferenceIngredient），
     * 不提供有损近似，直接抛清晰异常。
     * 脚本可在 NeoForge（1.21.1/26.x）使用 Ingredient.not(...)，或在任意平台用
     * 基础 ingredient 的 .except() / .subtract()（NeoForge）。
     */
    public IngredientJS not(Ingredient ingredient) {
        throw new UnsupportedOperationException(
                "Ingredient.not() is not supported on 1.12.2 (no native negation ingredient type). " +
                "Available on NeoForge 1.21.1/26.x.");
    }
}
