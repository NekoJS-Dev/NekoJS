package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import com.tkisor.nekojs.wrapper.item.SizedIngredientJS;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public final class SizedIngredientAdapter implements JSTypeAdapter<SizedIngredient> {
    @Override
    public Class<SizedIngredient> getTargetClass() {
        return SizedIngredient.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                arrayOf(string()),
                host(Ingredient.class),
                object(
                        Slot.opt("ingredient", host(Ingredient.class)),
                        Slot.opt("item", string()),
                        Slot.opt("tag", string()),
                        Slot.req("count", number())));
    }

    @Override
    public boolean test(Value value) {
        if (value == null || value.isNull()) return false;
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof SizedIngredient || obj instanceof SizedIngredientJS || obj instanceof Ingredient;
        }
        return value.hasMembers() || value.isString() || value.hasArrayElements();
    }

    @Override
    public SizedIngredient apply(Value value) {
        try {
            if (value.isHostObject()) {
                Object obj = value.asHostObject();
                if (obj instanceof SizedIngredient sized) return sized;
                if (obj instanceof SizedIngredientJS wrapper) return wrapper.unwrap();
                if (obj instanceof Ingredient ingredient) return new SizedIngredient(ingredient, 1);
            }
            if (value.hasMembers() && value.hasMember("count")) {
                Value ingredientValue = value.hasMember("ingredient") ? value.getMember("ingredient") : value.hasMember("item") ? value.getMember("item") : value;
                return new SizedIngredient(IngredientResolver.fromValue(ingredientValue), parseCount(value.getMember("count")));
            }
            return new SizedIngredient(IngredientResolver.fromValue(value), 1);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(SizedIngredient.class, "sized ingredient value", value, e.getMessage(), e);
        }
    }

    private static int parseCount(Value value) {
        if (!value.isNumber() || !value.fitsInInt()) {
            throw new IllegalArgumentException("SizedIngredient count must be an integer");
        }
        int count = value.asInt();
        if (count <= 0) {
            throw new IllegalArgumentException("SizedIngredient count must be positive: " + count);
        }
        return count;
    }
}
