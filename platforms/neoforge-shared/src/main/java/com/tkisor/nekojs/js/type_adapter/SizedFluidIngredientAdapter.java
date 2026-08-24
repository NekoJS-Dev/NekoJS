package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS;
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
                // 字符串语法逐个单列：裸 string 会吞掉联合里的 id 字面量补全
                registry("Fluid"),
                template("#", registryTag("Fluid")),
                template("@", namespace()),
                literal("*"),
                template("/", string()),
                host(FluidStack.class),
                host(Fluid.class),
                host(NekoId.class),
                object(
                        Slot.opt("ingredient", self()),
                        Slot.opt("fluid", registry("Fluid")),
                        Slot.opt("tag", registryTag("Fluid")),
                        Slot.opt("mod", namespace()),
                        Slot.opt("regex", string()),
                        Slot.opt("wildcard", bool()),
                        Slot.opt("filter", raw("((fluid: $FluidStack) => boolean)")),
                        Slot.opt("any", arrayOf(self())),
                        Slot.opt("all", arrayOf(self())),
                        Slot.opt("not", self()),
                        Slot.req("amount", number())));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("fluid:id | #tag | @mod | * | /regex/ | { ingredient|fluid|tag|mod|regex|wildcard|filter|any|all|not, amount }");
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
            throw new ValueConversionException(SizedFluidIngredient.class, "fluid / sized fluid ingredient object", value,
                e.getMessage(), e);
        }
    }
}
