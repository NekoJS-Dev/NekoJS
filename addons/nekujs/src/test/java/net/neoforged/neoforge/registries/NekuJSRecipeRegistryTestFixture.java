package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;

/** Invokes genuine package-private registry lifecycle operations only in an isolated test JVM. */
public final class NekuJSRecipeRegistryTestFixture {
    private NekuJSRecipeRegistryTestFixture() {}

    public static void registerIngredientTypes() {
        var id = ResourceLocation.fromNamespaceAndPath("neoforge", "compound");
        if (NeoForgeRegistries.INGREDIENT_TYPES.containsKey(id)) return;
        if (!net.minecraft.core.registries.BuiltInRegistries.REGISTRY
                .containsKey(NeoForgeRegistries.INGREDIENT_TYPES.key().location())) {
            var event = new NewRegistryEvent();
            event.register(NeoForgeRegistries.INGREDIENT_TYPES);
            event.fill();
        }
        Registry.register(NeoForgeRegistries.INGREDIENT_TYPES, id,
                new net.neoforged.neoforge.common.crafting.IngredientType<>(
                        net.neoforged.neoforge.common.crafting.CompoundIngredient.CODEC));
    }

    public static void registerFluidIngredientTypes() {
        if (!net.minecraft.core.registries.BuiltInRegistries.REGISTRY
                .containsKey(NeoForgeRegistries.FLUID_INGREDIENT_TYPES.key().location())) {
            var event = new NewRegistryEvent();
            event.register(NeoForgeRegistries.FLUID_INGREDIENT_TYPES);
            event.fill();
        }
        var single = ResourceLocation.fromNamespaceAndPath("neoforge", "single");
        var tag = ResourceLocation.fromNamespaceAndPath("neoforge", "tag");
        if (!NeoForgeRegistries.FLUID_INGREDIENT_TYPES.containsKey(single)) {
            Registry.register(NeoForgeRegistries.FLUID_INGREDIENT_TYPES, single, new FluidIngredientType<>(SingleFluidIngredient.CODEC));
        }
        if (!NeoForgeRegistries.FLUID_INGREDIENT_TYPES.containsKey(tag)) {
            Registry.register(NeoForgeRegistries.FLUID_INGREDIENT_TYPES, tag, new FluidIngredientType<>(TagFluidIngredient.CODEC));
        }
        org.junit.jupiter.api.Assertions.assertSame(SingleFluidIngredient.CODEC,
                NeoForgeRegistries.FLUID_INGREDIENT_TYPES.get(single).codec(), "Recipe fixture requires the genuine single-fluid codec");
        org.junit.jupiter.api.Assertions.assertSame(TagFluidIngredient.CODEC,
                NeoForgeRegistries.FLUID_INGREDIENT_TYPES.get(tag).codec(), "Recipe fixture requires the genuine tag-fluid codec");
    }
}
