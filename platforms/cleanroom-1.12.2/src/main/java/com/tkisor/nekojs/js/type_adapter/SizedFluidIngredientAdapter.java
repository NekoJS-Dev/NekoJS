package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;
import net.minecraftforge.fluids.FluidStack;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 SizedFluidIngredient 适配器（passthrough 版）。
 * <b>注意：1.12.2 无 {@code SizedFluidIngredient} 类。</b>
 * 本适配器将 FluidStack + amount 包装为长度为 2 的数组 [FluidStack, Integer]，
 * 供脚本侧手动解构使用。
 *
 * <p>接受：{ ingredient, amount } 对象或 FluidStack 宿主对象（amount 默认 1000）。</p>
 */
@SuppressWarnings("unchecked")
public final class SizedFluidIngredientAdapter implements JSTypeAdapter<Object[]> {

    @Override
    public Class<Object[]> getTargetClass() {
        return Object[].class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                host(FluidStack.class),
                object(
                        Slot.opt("ingredient", host(FluidStack.class)),
                        Slot.opt("amount", number())));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("{ ingredient, amount } | $FluidStack (amount=1000)");
    }

    @Override
    public boolean test(Value value) {
        if (value == null || value.isNull()) return false;
        if (value.isHostObject() && value.asHostObject() instanceof FluidStack) return true;
        return value.hasMembers() && value.hasMember("ingredient");
    }

    @Override
    public Object[] apply(Value value) {
        try {
            if (value.isHostObject()) {
                Object obj = value.asHostObject();
                if (obj instanceof FluidStack stack) {
                    return new Object[]{stack.copy(), stack.amount};
                }
            }
            if (value.hasMembers()) {
                FluidStack stack;
                int amount = 1000;
                if (value.hasMember("ingredient")) {
                    Value ingVal = value.getMember("ingredient");
                    if (ingVal.isHostObject() && ingVal.asHostObject() instanceof FluidStack) {
                        stack = ((FluidStack) ingVal.asHostObject()).copy();
                    } else {
                        throw new ValueConversionException(Object[].class, "FluidStack host object", ingVal,
                            "'ingredient' must be a FluidStack");
                    }
                } else {
                    throw new ValueConversionException(Object[].class, "object with 'ingredient'", value,
                        "SizedFluidIngredient object must contain 'ingredient'");
                }
                if (value.hasMember("amount")) {
                    Value amountVal = value.getMember("amount");
                    if (!amountVal.isNumber() || !amountVal.fitsInInt()) {
                        throw new ValueConversionException(Object[].class, "integer amount", amountVal,
                            "amount must be an integer");
                    }
                    amount = amountVal.asInt();
                    if (amount <= 0) {
                        throw new ValueConversionException(Object[].class, "positive integer amount", amount,
                            "amount must be positive: " + amount);
                    }
                }
                stack.amount = amount;
                return new Object[]{stack, amount};
            }
            throw new ValueConversionException(Object[].class, "FluidStack host object or { ingredient, amount }", value,
                "unsupported sized fluid ingredient value");
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(Object[].class, "sized fluid ingredient", value,
                e.getMessage(), e);
        }
    }
}
