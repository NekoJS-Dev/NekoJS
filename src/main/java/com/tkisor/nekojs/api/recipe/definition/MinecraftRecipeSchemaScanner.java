//? if neoforge {
//~ mc_legacy_api
// 本文件启用文件级 mc_legacy_api（26.x 活形态无 .identifier()，1.21.1 求值形态无 .identifier()）。
package com.tkisor.nekojs.api.recipe.definition;

import com.mojang.serialization.Codec;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import graal.graalvm.polyglot.Value;
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
                List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys = extractKeys(id, serializer);
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

    /** Known vanilla contracts use registered serializer identity, not a mod's similarly named path. */
    static List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> extractKeys(Identifier id, RecipeSerializer<?> serializer) {
        Identifier registeredId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
        if (registeredId != null && registeredId.getNamespace().equals("minecraft")) {
            if (registeredId.getPath().equals("crafting_shaped")) {
                return List.of(resultKey(),
                        new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey("pattern", RecipeFieldKind.STRING,
                                false, true, listCodec(RecipeFieldKind.STRING, ApiTypeRef.primitive("string"), AdapterInputShape.string())),
                        new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey("key", RecipeFieldKind.JSON,
                                false, false, ingredientMapCodec()));
            }
            if (registeredId.getPath().equals("crafting_shapeless")) {
                return List.of(resultKey(),
                        new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey("ingredients", RecipeFieldKind.INGREDIENT,
                                false, true, listCodec(RecipeFieldKind.INGREDIENT, nominal(Ingredient.class), AdapterInputShape.host(Ingredient.class))));
            }
        }
        return extractKeys(id, serializer.codec());
    }

    private static RecipeSchemaAutoDiscovery.DiscoveredRecipeKey resultKey() {
        var codec = new RecipeFieldValueCodec(AdapterInputShape.host(ItemStack.class), nominal(ItemStack.class),
                (value, host) -> host.encodeField(RecipeFieldKind.ITEM_STACK, value));
        return new RecipeSchemaAutoDiscovery.DiscoveredRecipeKey("result", RecipeFieldKind.ITEM_STACK, false, false, codec);
    }

    private static RecipeFieldValueCodec listCodec(RecipeFieldKind kind, ApiTypeRef elementType, AdapterInputShape elementShape) {
        return new RecipeFieldValueCodec(AdapterInputShape.arrayOf(elementShape), nominal(List.class, elementType), (value, host) -> {
            var array = new JsonArray();
            for (long index = 0, size = value.getArraySize(); index < size; index++) {
                array.add(host.encodeField(kind, value.getArrayElement(index)));
            }
            return array;
        });
    }

    private static RecipeFieldValueCodec ingredientMapCodec() {
        // Nominal metadata preserves dynamic key/value types beyond the structural model's fixed slots.
        return new RecipeFieldValueCodec(AdapterInputShape.object(),
                nominal(Map.class, ApiTypeRef.primitive("string"), nominal(Ingredient.class)), (value, host) -> {
            var object = new JsonObject();
            if (value.hasHashEntries()) {
                Value entries = value.getHashEntriesIterator();
                while (entries.hasIteratorNextElement()) {
                    Value entry = entries.getIteratorNextElement();
                    object.add(entry.getArrayElement(0).asString(), host.encodeField(RecipeFieldKind.INGREDIENT, entry.getArrayElement(1)));
                }
            } else {
                for (String key : value.getMemberKeys()) {
                    object.add(key, host.encodeField(RecipeFieldKind.INGREDIENT, value.getMember(key)));
                }
            }
            return object;
        });
    }

    private static ApiTypeRef nominal(Class<?> type, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", type.getName()), List.of(arguments));
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
            com.tkisor.nekojs.NekoJS.LOGGER.debug("MinecraftRecipeSchemaScanner: codec field introspection failed for " + id, ignored);
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
        if (path.contains("smelt") || path.contains("blast") || path.contains("smok") || path.contains("campfire")) {
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
//?}
