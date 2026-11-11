package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.js.type_adapter.SizedFluidIngredientAdapter;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.FluidMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public record SizedFluidIngredientComponent(RecipeComponentType<?> type, Codec<SizedFluidIngredient> codec, boolean allowEmpty) implements RecipeComponent<SizedFluidIngredient> {
    private static final SizedFluidIngredientAdapter CONVERTER = new SizedFluidIngredientAdapter();
    public static final RecipeComponentType<SizedFluidIngredient> FLAT = create("flat_sized_fluid_ingredient", SizedFluidIngredient.FLAT_CODEC, false);
    public static final RecipeComponentType<SizedFluidIngredient> NESTED = create("nested_sized_fluid_ingredient", SizedFluidIngredient.NESTED_CODEC, false);
    public static final RecipeComponentType<SizedFluidIngredient> OPTIONAL_FLAT = create("optional_flat_sized_fluid_ingredient", SizedFluidIngredient.FLAT_CODEC, true);
    public static final RecipeComponentType<SizedFluidIngredient> OPTIONAL_NESTED = create("optional_nested_sized_fluid_ingredient", SizedFluidIngredient.NESTED_CODEC, true);
    private static RecipeComponentType<SizedFluidIngredient> create(String name, Codec<SizedFluidIngredient> codec, boolean optional) {
        return RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", name), type -> new SizedFluidIngredientComponent(type, codec, optional));
    }
    @Override public SizedFluidIngredient wrap(RecipeScriptContext cx, Object from) {
        return CONVERTER.apply(NativeComponentValues.value(from));
    }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(SizedFluidIngredient.class); }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) {
        return from instanceof SizedFluidIngredient || from instanceof FluidIngredient || from instanceof FluidStack || from instanceof Fluid;
    }
    @Override public boolean matches(RecipeMatchContext cx, SizedFluidIngredient value, ReplacementMatchInfo match) {
        return match.match() instanceof FluidMatch fluid && fluid.matches(cx, value.ingredient(), match.exact());
    }
    @Override public boolean isEmpty(SizedFluidIngredient value) { return value.amount() <= 0 || value.ingredient().isEmpty(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, SizedFluidIngredient value) {
        if (!value.ingredient().isEmpty()) {
            FluidStack[] stacks = value.ingredient().getStacks();
            if (stacks.length > 0) builder.append(BuiltInRegistries.FLUID.getKey(stacks[0].getFluid()));
        }
    }
    @Override public String toString() { return type.toString(); }
}
