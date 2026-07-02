package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public final class SizedFluidIngredientAdapter implements JSTypeAdapter<SizedFluidIngredient> {
    @Override
    public Class<SizedFluidIngredient> getTargetClass() {
        return SizedFluidIngredient.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                host(FluidStack.class),
                host(Fluid.class),
                host(NekoId.class),
                object(
                        Slot.opt("ingredient", self()),
                        Slot.opt("fluid", string()),
                        Slot.opt("tag", string()),
                        Slot.req("amount", number())));
    }

    @Override
    public boolean test(Value value) {
        if (value.isString() || value.hasMembers()) return true;
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof SizedFluidIngredient || obj instanceof FluidIngredient || obj instanceof FluidIngredientJS || obj instanceof FluidStack || obj instanceof Fluid || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public SizedFluidIngredient apply(Value value) {
        try {
            return FluidResolver.sizedFromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(SizedFluidIngredient.class, "sized fluid ingredient value", value, e.getMessage(), e);
        }
    }
}
