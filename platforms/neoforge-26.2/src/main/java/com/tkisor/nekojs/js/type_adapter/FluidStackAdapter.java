package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidStackAdapter implements JSTypeAdapter<FluidStack> {
    @Override
    public Class<FluidStack> getTargetClass() {
        return FluidStack.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("Fluid"),
                host(Fluid.class),
                host(NekoId.class),
                object(
                        Slot.opt("fluid", registry("Fluid")),
                        Slot.opt("id", registry("Fluid")),
                        Slot.opt("amount", number())));
    }

    @Override
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return HostAccess.TargetMappingPrecedence.LOW;
    }

    @Override
    public boolean test(Value value) {
        if (value.isNull() || value.isString() || value.hasMembers()) return true;
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof FluidStack || obj instanceof Fluid || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public FluidStack apply(Value value) {
        try {
            return FluidResolver.stackFromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(FluidStack.class, "fluid / fluid id / fluid object", value,
                e.getMessage(), e);
        }
    }
}
