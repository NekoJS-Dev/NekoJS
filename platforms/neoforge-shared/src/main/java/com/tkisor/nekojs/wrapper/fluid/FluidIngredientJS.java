package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 流体配料包装：多个备选（「或」关系）叠加，{@code unwrap()} 合成单个
 * {@link FluidIngredient}；{@code withAmount} 附加数量得到 {@link SizedFluidIngredient}。
 */
@Doc("A fluid ingredient built from OR-combined alternatives.")
@Doc("Use withAmount(n) to turn it into a sized fluid ingredient requiring n millibuckets.")
public class FluidIngredientJS implements NekoWrapper<FluidIngredient> {
    private final List<FluidIngredient> alternatives = new ArrayList<>();

    public FluidIngredientJS() {}

    /** 由若干流体 id 构造（支持 {@code '#tag'} 等选择器语法）。 */
    @Doc("Creates a fluid ingredient matching any of the given fluid ids.")
    @Param(name = "ids", value = "fluid ids; '#tag', '@mod', '*', and '/regex/' selector strings also work")
    public FluidIngredientJS(String... ids) {
        // Inline instead of delegating to or(): calling an instance method from a
        // constructor can hand a partially initialized `this` out (this-escape).
        for (String id : ids) alternatives.add(FluidResolver.ingredientFromString(id));
    }

    /** 由现成配料构造。 */
    @Doc("Creates a fluid ingredient from an existing FluidIngredient.")
    @Param(name = "ingredient", value = "the ingredient to wrap")
    public FluidIngredientJS(FluidIngredient ingredient) {
        // Inline instead of delegating to or(): calling an instance method from a
        // constructor can hand a partially initialized `this` out (this-escape).
        alternatives.add(ingredient);
    }

    /** 追加一个备选：流体 id。 */
    @Doc("Adds a fluid id as another accepted alternative.")
    @Param(name = "id", value = "fluid id or selector string like '#tag'")
    @Return("this, for chaining")
    public FluidIngredientJS or(String id) {
        alternatives.add(FluidResolver.ingredientFromString(id));
        return this;
    }

    /** 追加一个备选：{@link NekoId}。 */
    @Doc("Adds a NekoId as another accepted alternative.")
    @Param(name = "id", value = "fluid id as a NekoId")
    @Return("this, for chaining")
    public FluidIngredientJS or(NekoId id) {
        alternatives.add(FluidResolver.ingredientFromString(id.toString()));
        return this;
    }

    /** 追加一个备选：流体。 */
    @Doc("Adds a fluid as another accepted alternative.")
    @Param(name = "fluid", value = "the fluid to accept")
    @Return("this, for chaining")
    public FluidIngredientJS or(Fluid fluid) {
        alternatives.add(FluidResolver.ingredientFromFluid(fluid));
        return this;
    }

    /** 追加一个备选：流体栈（匹配其中的流体）。 */
    @Doc("Adds the fluid of a stack as another accepted alternative.")
    @Param(name = "stack", value = "the fluid stack whose fluid is accepted")
    @Return("this, for chaining")
    public FluidIngredientJS or(FluidStack stack) {
        alternatives.add(FluidResolver.ingredientFromStack(stack));
        return this;
    }

    /** 追加一个备选：现成配料。 */
    @Doc("Adds an existing FluidIngredient as another accepted alternative.")
    @Param(name = "ingredient", value = "the ingredient to OR in")
    @Return("this, for chaining")
    public FluidIngredientJS or(FluidIngredient ingredient) {
        alternatives.add(ingredient);
        return this;
    }

    /** 追加一个备选：另一个包装对象。 */
    @Doc("Adds all alternatives of another FluidIngredientJS.")
    @Param(name = "other", value = "wrapper whose combined alternatives are OR-joined into this one")
    @Return("this, for chaining")
    public FluidIngredientJS or(FluidIngredientJS other) {
        alternatives.add(other.unwrap());
        return this;
    }

    /** 是否尚无备选（空配料）。 */
    @Doc("Checks whether no alternative has been added yet.")
    @Return("true when the wrapper holds no alternatives")
    public boolean isEmpty() {
        return alternatives.isEmpty();
    }

    /** 附加数量（mB）得到带数量流体配料。 */
    @Doc("Turns this ingredient into a sized one requiring the given amount.")
    @Param(name = "amount", value = "required amount in millibuckets")
    @Return("a SizedFluidIngredient matching the same fluids with the given amount")
    public SizedFluidIngredient withAmount(int amount) {
        return FluidResolver.sizedFromIngredient(unwrap(), amount);
    }

    /** {@link #withAmount(int)} 的短别名。 */
    @Doc("Alias of withAmount(amount).")
    @Param(name = "amount", value = "required amount in millibuckets")
    @Return("a SizedFluidIngredient matching the same fluids with the given amount")
    public SizedFluidIngredient sized(int amount) {
        return withAmount(amount);
    }

    /** 解包：多个备选合并为单个 {@link FluidIngredient}（单备选原样返回）。 */
    @Doc("Unwraps to a single FluidIngredient; throws when no alternative has been added.")
    @Return("the single alternative as-is, or a combined OR ingredient when several were added")
    @Override
    public FluidIngredient unwrap() {
        return FluidResolver.combine(alternatives);
    }
}
