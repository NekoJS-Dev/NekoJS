package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.crafting.Ingredient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 SizedIngredient 适配器（passthrough 版）。
 * <b>注意：1.12.2 无 {@code SizedIngredient} 类。</b>
 * 本适配器将 ingredient + count 包装为长度为 2 的数组 [Ingredient, Integer]，
 * 供脚本侧手动解构使用。
 *
 * <p>接受：{ ingredient, count } 对象或 Ingredient 宿主对象（count 默认 1）。</p>
 */
@SuppressWarnings("unchecked")
public final class SizedIngredientAdapter implements JSTypeAdapter<Object[]> {

    @Override
    public Class<Object[]> getTargetClass() {
        return Object[].class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                host(Ingredient.class),
                object(
                        Slot.opt("ingredient", host(Ingredient.class)),
                        Slot.opt("count", number())));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("{ ingredient, count } | $Ingredient (count=1)");
    }

    @Override
    public boolean test(Value value) {
        if (value == null || value.isNull()) return false;
        if (value.isHostObject() && value.asHostObject() instanceof Ingredient) return true;
        return value.hasMembers() && value.hasMember("ingredient");
    }

    @Override
    public Object[] apply(Value value) {
        try {
            if (value.isHostObject()) {
                Object obj = value.asHostObject();
                if (obj instanceof Ingredient ingredient) {
                    return new Object[]{ingredient, 1};
                }
            }
            if (value.hasMembers()) {
                Ingredient ingredient;
                int count = 1;
                if (value.hasMember("ingredient")) {
                    Value ingVal = value.getMember("ingredient");
                    if (ingVal.isHostObject() && ingVal.asHostObject() instanceof Ingredient) {
                        ingredient = (Ingredient) ingVal.asHostObject();
                    } else {
                        throw new ValueConversionException(Object[].class, "Ingredient host object", ingVal,
                            "'ingredient' must be an Ingredient");
                    }
                } else {
                    throw new ValueConversionException(Object[].class, "object with 'ingredient'", value,
                        "SizedIngredient object must contain 'ingredient'");
                }
                if (value.hasMember("count")) {
                    Value countVal = value.getMember("count");
                    if (!countVal.isNumber() || !countVal.fitsInInt()) {
                        throw new ValueConversionException(Object[].class, "integer count", countVal,
                            "count must be an integer");
                    }
                    count = countVal.asInt();
                    if (count <= 0) {
                        throw new ValueConversionException(Object[].class, "positive integer count", count,
                            "count must be positive: " + count);
                    }
                }
                return new Object[]{ingredient, count};
            }
            throw new ValueConversionException(Object[].class, "Ingredient host object or { ingredient, count }", value,
                "unsupported sized ingredient value");
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(Object[].class, "sized ingredient", value,
                e.getMessage(), e);
        }
    }
}
