package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.item.IngredientJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.crafting.Ingredient;

/**
 * 脚本侧的 Ingredient 工厂，绑定为全局 {@code Ingredient}。
 * 每个 {@code of}/{@code item}/{@code ore} 调用产生一个可链式组合的 {@link IngredientJS}。
 */
public class IngredientFactory {

    /** 把任意数量的物品类值（id 字符串、物品栈、ingredient 等）合并为一个 OR ingredient。 */
    @Doc("Combines any number of item-like values into one OR ingredient.")
    @Param(name = "values", value = "item id strings, item stacks, or ingredients; multiple values are OR-combined")
    @Return("a new IngredientJS wrapper, never null")
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

    /** 按物品 id 创建 ingredient。 */
    @Doc("Creates an ingredient matching a single item id.")
    @Param(name = "id", value = "item id like 'minecraft:stone'")
    @Return("a new IngredientJS wrapper")
    public IngredientJS item(String id) {
        return new IngredientJS(id);
    }

    /** 按 OreDictionary 名创建 ingredient（如 {@code "planks"}）。 */
    @Doc("Creates an ingredient matching an OreDictionary name.")
    @Param(name = "name", value = "OreDictionary name like 'planks'")
    @Return("a new IngredientJS wrapper")
    public IngredientJS ore(String name) {
        return new IngredientJS("ore:" + name);
    }

    /** 把多个已构建的 ingredient 合并为一个 OR ingredient。 */
    @Doc("OR-combines several already-built ingredients.")
    @Param(name = "ingredients", value = "ingredients to combine")
    @Return("a new IngredientJS wrapper wrapping the union")
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
    @Doc("Approximate wildcard matching every registered item.")
    @Doc("Enumerates a registry snapshot (no native wildcard on 1.12.2); items registered later by scripts are not included.")
    @Return("a new IngredientJS wrapper matching all items known at call time")
    public IngredientJS all() {
        return new IngredientJS(IngredientResolver.wildcard());
    }

    /**
     * 取反 ingredient。1.12.2 无原生 negation ingredient 类型（无 DifferenceIngredient），
     * 不提供有损近似，直接抛清晰异常。
     * 脚本可在 NeoForge（1.21.1/26.x）使用 Ingredient.not(...)，或在任意平台用
     * 基础 ingredient 的 .except() / .subtract()（NeoForge）。
     */
    @Doc("Negation ingredient — not supported on 1.12.2 (no native negation ingredient type).")
    @Param(name = "ingredient", value = "the ingredient to negate")
    @Return("never returns; always throws UnsupportedOperationException")
    public IngredientJS not(Ingredient ingredient) {
        throw new UnsupportedOperationException(
                "Ingredient.not() is not supported on 1.12.2 (no native negation ingredient type). " +
                "Available on NeoForge 1.21.1/26.x.");
    }
}
