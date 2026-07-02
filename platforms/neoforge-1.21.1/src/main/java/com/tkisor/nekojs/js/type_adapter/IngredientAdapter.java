package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.item.IngredientJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class IngredientAdapter implements JSTypeAdapter<Ingredient> {

    @Override
    public Class<Ingredient> getTargetClass() {
        return Ingredient.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                arrayOf(string()),
                host(ItemStack.class),
                host(Item.class),
                host(NekoId.class),
                object(
                        Slot.opt("item", string()),
                        Slot.opt("tag", string()),
                        Slot.opt("ingredient", self())));
    }

    @Override
    public boolean test(Value value) {
        if (value.isNull() || value.isString() || value.hasArrayElements() || value.hasMembers()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof IngredientJS || obj instanceof Ingredient || obj instanceof ItemStack || obj instanceof Item || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public Ingredient apply(Value value) {
        try {
            return IngredientResolver.fromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(Ingredient.class, "ingredient value", value, e.getMessage(), e);
        }
    }
}
