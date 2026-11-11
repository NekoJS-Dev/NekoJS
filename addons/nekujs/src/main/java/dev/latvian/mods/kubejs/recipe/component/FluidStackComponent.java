package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.js.type_adapter.FluidStackAdapter;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.FluidMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

public record FluidStackComponent(RecipeComponentType<?> type, Codec<FluidStack> codec, boolean allowEmpty) implements RecipeComponent<FluidStack> {
    private static final FluidStackAdapter CONVERTER = new FluidStackAdapter();
    public static final RecipeComponentType<FluidStack> FLUID_STACK = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "fluid_stack"), type -> new FluidStackComponent(type, FluidStack.CODEC, false));
    public static final RecipeComponentType<FluidStack> OPTIONAL_FLUID_STACK = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "optional_fluid_stack"), type -> new FluidStackComponent(type, FluidStack.OPTIONAL_CODEC, true));
    @Override public TypeInfo typeInfo() { return TypeInfo.of(FluidStack.class); }
    @Override public FluidStack wrap(RecipeScriptContext cx, Object from) { return CONVERTER.apply(NativeComponentValues.value(from)); }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) { return CONVERTER.test(NativeComponentValues.value(from)); }
    @Override public boolean matches(RecipeMatchContext cx, FluidStack value, ReplacementMatchInfo match) {
        return match.match() instanceof FluidMatch fluid && fluid.matches(cx, value, match.exact());
    }
    @Override public boolean isEmpty(FluidStack value) { return value.isEmpty(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, FluidStack value) {
        if (!value.isEmpty()) builder.append(BuiltInRegistries.FLUID.getKey(value.getFluid()));
    }
    @Override public String toString() { return type.toString(); }
}
