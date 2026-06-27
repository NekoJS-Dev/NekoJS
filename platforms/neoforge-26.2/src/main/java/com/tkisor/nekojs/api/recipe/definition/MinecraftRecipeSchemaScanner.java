package com.tkisor.nekojs.api.recipe.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MinecraftRecipeSchemaScanner {

    public static RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes scan() {
        Map<String, List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey>> types = new LinkedHashMap<>();

        for (var entry : BuiltInRegistries.RECIPE_SERIALIZER.entrySet()) {
            Identifier id = entry.getKey().identifier();
            RecipeSerializer<?> serializer = entry.getValue();
            String typeKey = id.toString();

            try {
                MapCodec<?> codec = serializer.codec();
                List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys = extractKeys(id, codec);
                if (!keys.isEmpty()) {
                    types.put(typeKey, keys);
                }
            } catch (Exception ignored) {
                // Codec introspection failed; skip this type
                types.put(typeKey, List.of(
                        RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("json", RecipeFieldKind.JSON)
                ));
            }
        }

        return RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes.of(types);
    }

    private static List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> extractKeys(Identifier id, MapCodec<?> codec) {
        List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys = new ArrayList<>();

        try {
            // Try to access the codec's internal fields via reflection
            for (Field field : codec.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(codec);
                if (val instanceof Codec<?> inner) {
                    // Check if it's a field codec with Optional
                    Type genericType = field.getGenericType();
                    String name = field.getName();
                    boolean optional = isOptional(genericType);

                    RecipeFieldKind kind = inferKind(inner);
                    if (kind != null) {
                        keys.add(new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey(name, kind, optional, false));
                    }
                } else if (val instanceof List) {
                    // Might be a list codec
                    Type genericType = field.getGenericType();
                    String name = field.getName();
                    if (genericType instanceof ParameterizedType pt && pt.getRawType() == Codec.class) {
                        Type arg = pt.getActualTypeArguments()[0];
                        RecipeFieldKind kind = inferKindFromType(arg);
                        if (kind != null) {
                            keys.add(new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey(name, kind,
                                    false, true));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Reflection failed
        }

        // Fallback: if no keys were extracted, try common field name patterns
        if (keys.isEmpty()) {
            keys.addAll(inferFromCommonPatterns(id));
        }

        return keys;
    }

    private static List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> inferFromCommonPatterns(Identifier id) {
        List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys = new ArrayList<>();
        String path = id.getPath();

        // Common recipe type patterns
        if (path.contains("shaped") || path.contains("shapeless")) {
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("result", RecipeFieldKind.ITEM_STACK));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("ingredients", RecipeFieldKind.JSON));
        } else if (path.contains("smelt") || path.contains("blast") || path.contains("smok") || path.contains("campfire")) {
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("ingredient", RecipeFieldKind.INGREDIENT));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("result", RecipeFieldKind.ITEM_STACK));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.optional("experience", RecipeFieldKind.NUMBER));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.optional("cookingtime", RecipeFieldKind.INT));
        } else if (path.contains("stonecutting")) {
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("ingredient", RecipeFieldKind.INGREDIENT));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("result", RecipeFieldKind.ITEM_STACK));
        } else if (path.contains("smithing")) {
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("template", RecipeFieldKind.INGREDIENT));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("base", RecipeFieldKind.INGREDIENT));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("addition", RecipeFieldKind.INGREDIENT));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("result", RecipeFieldKind.ITEM_STACK));
        } else {
            // Generic: single input + output
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("ingredient", RecipeFieldKind.INGREDIENT));
            keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required("result", RecipeFieldKind.ITEM_STACK));
        }

        return keys;
    }

    private static RecipeFieldKind inferKind(Codec<?> codec) {
        // Check known codec types
        String className = codec.getClass().getName();

        if (className.contains("Ingredient")) return RecipeFieldKind.INGREDIENT;
        if (className.contains("ItemStack")) return RecipeFieldKind.ITEM_STACK;
        if (className.contains("FluidStack")) return RecipeFieldKind.FLUID_STACK;
        if (className.contains("FluidIngredient") || className.contains("SizedFluid")) return RecipeFieldKind.FLUID_INGREDIENT;

        // Fallback to common number/string codecs
        if (className.contains("Int") || className.contains("Integer")) return RecipeFieldKind.INT;
        if (className.contains("Float") || className.contains("Double")) return RecipeFieldKind.NUMBER;
        if (className.contains("Bool")) return RecipeFieldKind.BOOLEAN;
        if (className.contains("String")) return RecipeFieldKind.STRING;

        return RecipeFieldKind.JSON;
    }

    private static RecipeFieldKind inferKindFromType(Type type) {
        if (type instanceof Class<?> cls) {
            if (Ingredient.class.isAssignableFrom(cls)) return RecipeFieldKind.INGREDIENT;
            if (ItemStack.class.isAssignableFrom(cls)) return RecipeFieldKind.ITEM_STACK;
            if (FluidStack.class.isAssignableFrom(cls)) return RecipeFieldKind.FLUID_STACK;
            if (FluidIngredient.class.isAssignableFrom(cls)) return RecipeFieldKind.FLUID_INGREDIENT;
            if (SizedFluidIngredient.class.isAssignableFrom(cls)) return RecipeFieldKind.SIZED_FLUID_INGREDIENT;
            if (String.class == cls) return RecipeFieldKind.STRING;
            if (int.class == cls || Integer.class == cls) return RecipeFieldKind.INT;
            if (float.class == cls || double.class == cls || Float.class == cls || Double.class == cls)
                return RecipeFieldKind.NUMBER;
            if (boolean.class == cls || Boolean.class == cls) return RecipeFieldKind.BOOLEAN;
        }
        return RecipeFieldKind.JSON;
    }

    private static boolean isOptional(Type type) {
        return type instanceof ParameterizedType pt && pt.getRawType() == Optional.class;
    }
}
