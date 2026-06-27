package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
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
        return FluidResolver.stackFromValue(value);
    }
}
