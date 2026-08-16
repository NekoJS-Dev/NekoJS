package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * 脚本静态绑定 {@code FluidIngredient}：流体配料工厂。
 */
@Doc("Static binding 'FluidIngredient': factory helpers for fluid ingredients.")
public class FluidIngredientJS {
    /** 由多个流体值构造「或」关系的流体配料。 */
    @Doc("Builds a fluid ingredient matching any of the given fluid-like values.")
    @Doc("Accepts fluid ids and selector strings: '#tag', '@mod', '*' (any fluid), and '/regex/'.")
    @Param(name = "values", value = "fluid ids, '#tag'/'@mod'/'*'/'/regex/' strings, fluids, stacks, or ingredients")
    @Return("a FluidIngredientJS combining all values with OR; empty wrapper when no value is given")
    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS of(Object... values) {
        com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS wrapper = new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS();
        if (values != null) {
            for (Object value : values) {
                wrapper.or(FluidResolver.ingredientFromValue(toValue(value)));
            }
        }
        return wrapper;
    }

    /** 单流体配料。 */
    @Doc("Creates a fluid ingredient matching a single fluid.")
    @Param(name = "id", value = "fluid id like 'minecraft:water'")
    @Return("a FluidIngredientJS matching only that fluid")
    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS fluid(String id) {
        return new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS(id);
    }

    /** 流体标签配料（自动补 {@code '#'} 前缀）。 */
    @Doc("Creates a fluid ingredient matching a fluid tag.")
    @Param(name = "id", value = "tag id; a leading '#' is added automatically when missing")
    @Return("a FluidIngredientJS matching all fluids in the tag")
    public com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS tag(String id) {
        return new com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS(id.startsWith("#") ? id : "#" + id);
    }

    /** 从流体值解析带数量的流体配料（值本身可带 amount）。 */
    @Doc("Creates a sized fluid ingredient from a fluid-like value carrying its own amount.")
    @Param(name = "value", value = "fluid id, stack, or {fluid|id, amount} JS object")
    @Return("the resolved SizedFluidIngredient")
    public SizedFluidIngredient sized(Object value) {
        return FluidResolver.sizedFromValue(toValue(value));
    }

    /** 从流体值 + 显式数量（mB）解析带数量的流体配料。 */
    @Doc("Creates a sized fluid ingredient with an explicit amount in millibuckets.")
    @Param(name = "value", value = "fluid id, stack, or ingredient-like value (its own amount is ignored)")
    @Param(name = "amount", value = "required amount in millibuckets")
    @Return("the resolved SizedFluidIngredient with the given amount")
    public SizedFluidIngredient sized(Object value, int amount) {
        return FluidResolver.sizedFromIngredient(FluidResolver.ingredientFromValue(toValue(value)), amount);
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }
}
