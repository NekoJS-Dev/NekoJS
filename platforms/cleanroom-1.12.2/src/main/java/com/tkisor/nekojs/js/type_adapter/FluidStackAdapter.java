package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 FluidStack 适配器。
 * 使用 {@link FluidRegistry} 进行流体查找，
 * 使用 {@code new FluidStack(fluid, amount)} 构造。
 *
 * <p>1.12.2 适配差异：
 * <li>无 {@code FluidStack.of()} 静态工厂</li>
 * <li>使用 {@code FluidRegistry.getFluid(name)} 替代 BuiltInRegistries 流体查找</li>
 * </p>
 */
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
    public Optional<String> syntaxDoc() {
        return Optional.of("fluid:id | RegistryTypes.Fluid | $Fluid | $NekoId | { fluid?|id?, amount? }");
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOW;
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
            return stackFromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(FluidStack.class, "fluid / fluid id / fluid object", value,
                e.getMessage(), e);
        }
    }

    public static FluidStack stackFromValue(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidStack stack) return stack.copy();
            if (obj instanceof Fluid fluid) return new FluidStack(fluid, 1000);
            if (obj instanceof NekoId id) {
                return fromFluidId(id.toString(), 1000);
            }
        }
        if (value.hasMembers()) return fromObject(value);
        throw new ValueConversionException(FluidStack.class, "fluid / fluid id / fluid object", value,
            "unsupported fluid value");
    }

    private static FluidStack fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = raw.trim();
        // 尝试 "1000x fluid:id" 格式
        String[] parts = s.split("x\\s+", 2);
        int amount = 1000;
        String fluidId;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                throw new ValueConversionException(FluidStack.class, "integer amount", parts[0],
                    "invalid fluid amount: " + parts[0]);
            }
            fluidId = parts[1].trim();
        } else {
            fluidId = s;
        }
        return fromFluidId(fluidId, amount);
    }

    private static FluidStack fromFluidId(String fluidId, int amount) {
        // 1.12.2: FluidRegistry.getFluid(name) 查找流体
        Fluid fluid = FluidRegistry.getFluid(fluidId);
        if (fluid == null) {
            throw new ValueConversionException(FluidStack.class, "registered fluid id", fluidId,
                "Fluid not found: " + fluidId);
        }
        // 1.12.2: new FluidStack(fluid, amount)
        return new FluidStack(fluid, amount);
    }

    private static FluidStack fromObject(Value value) {
        Value fluidValue = null;
        if (value.hasMember("fluid")) {
            fluidValue = value.getMember("fluid");
        } else if (value.hasMember("id")) {
            fluidValue = value.getMember("id");
        }
        if (fluidValue == null) {
            throw new ValueConversionException(FluidStack.class, "fluid object with 'fluid' or 'id'", value,
                "FluidStack object must contain 'fluid' or 'id'");
        }
        FluidStack stack = stackFromValue(fluidValue);
        if (stack == null) return null;
        if (value.hasMember("amount")) {
            Value amountVal = value.getMember("amount");
            if (!amountVal.isNumber() || !amountVal.fitsInInt()) {
                throw new ValueConversionException(FluidStack.class, "integer amount", amountVal,
                    "fluid amount must be an integer");
            }
            stack.amount = amountVal.asInt();
        }
        return stack;
    }
}
