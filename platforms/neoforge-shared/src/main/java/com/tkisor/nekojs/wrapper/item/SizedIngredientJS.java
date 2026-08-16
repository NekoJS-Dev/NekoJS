package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 带数量的物品配料包装（ingredient + count），用于配方中「N 个 X」的输入。
 */
@Doc("An item ingredient paired with a count, for recipe inputs like '4 sticks'.")
public class SizedIngredientJS implements NekoWrapper<SizedIngredient> {
    private final Ingredient ingredient;
    private final int count;

    /** 构造：数量必须为正（否则抛 {@link IllegalArgumentException}）。 */
    @Doc("Creates a sized ingredient from an ingredient and a count.")
    @Param(name = "ingredient", value = "the ingredient to match")
    @Param(name = "count", value = "required item count; must be positive")
    public SizedIngredientJS(Ingredient ingredient, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("SizedIngredient count must be positive: " + count);
        }
        this.ingredient = ingredient;
        this.count = count;
    }

    /** 底层配料。 */
    @Doc("Gets the underlying ingredient.")
    @Return("the wrapped Ingredient")
    public Ingredient ingredient() {
        return ingredient;
    }

    /** 所需数量。 */
    @Doc("Gets the required item count.")
    @Return("the count; always positive")
    public int count() {
        return count;
    }

    /** 解包为原版 {@link SizedIngredient}（每次调用新建）。 */
    @Doc("Unwraps to the NeoForge SizedIngredient.")
    @Return("a new SizedIngredient instance built from this wrapper's ingredient and count")
    @Override
    public SizedIngredient unwrap() {
        return new SizedIngredient(ingredient, count);
    }
}
