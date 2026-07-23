package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Map;

/**
 * 1.12.2 RecipeJsonValueConverter - converts JS values to JSON elements.
 * Self-contained; does not depend on RecipeEventJS for serialization helpers.
 */
public final class RecipeJsonValueConverter {
    private RecipeJsonValueConverter() {}

    public static RecipeJsonValue wrap(Object value) {
        return new RecipeJsonValue(value);
    }

    public static JsonElement toJson(RecipeJsonValue value) {
        return convertObject(value.value());
    }

    public static JsonElement toJson(Object value) {
        return convertObject(value);
    }

    private static JsonElement convertObject(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof RecipeJsonValue recipeJsonValue) {
            return toJson(recipeJsonValue);
        }
        if (value instanceof JsonElement json) {
            return json;
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof String string) {
            return new JsonPrimitive(string);
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Character character) {
            return new JsonPrimitive(character);
        }
        if (value instanceof Value jsValue) {
            return convertValue(jsValue);
        }
        if (value instanceof Ingredient ingredient) {
            return serializeIngredient(ingredient);
        }
        if (value instanceof FluidStack stack) {
            return serializeFluidStack(stack);
        }
        if (value instanceof ItemStack stack) {
            return serializeItemStack(stack);
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object item : list) {
                array.add(convertObject(item));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject json = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                json.add(String.valueOf(entry.getKey()), convertObject(entry.getValue()));
            }
            return json;
        }
        // Fallback
        return new JsonPrimitive(value.toString());
    }

    private static JsonElement convertValue(Value value) {
        if (value.isNull()) return JsonNull.INSTANCE;
        if (value.isHostObject()) return convertObject(value.asHostObject());
        if (value.isBoolean()) return new JsonPrimitive(value.asBoolean());

        if (value.isNumber()) {
            if (value.fitsInInt()) return new JsonPrimitive(value.asInt());
            if (value.fitsInLong()) return new JsonPrimitive(value.asLong());
            return new JsonPrimitive(value.asDouble());
        }

        if (value.isString()) return new JsonPrimitive(value.asString());

        if (value.hasArrayElements()) {
            JsonArray array = new JsonArray();
            for (long i = 0; i < value.getArraySize(); i++) {
                array.add(convertValue(value.getArrayElement(i)));
            }
            return array;
        }

        if (value.hasMembers()) {
            JsonObject json = new JsonObject();
            for (String key : value.getMemberKeys()) {
                json.add(key, convertValue(value.getMember(key)));
            }
            return json;
        }

        return new JsonPrimitive(value.toString());
    }

    private static JsonElement serializeIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient == Ingredient.EMPTY) return new JsonArray();
        JsonArray array = new JsonArray();
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            array.add(serializeItemStack(stack));
        }
        return array.size() == 1 ? array.get(0) : array;
    }

    private static JsonElement serializeItemStack(ItemStack stack) {
        JsonObject json = new JsonObject();
        ResourceLocation id = stack.getItem().getRegistryName();
        json.addProperty("item", id != null ? id.toString() : "minecraft:air");
        if (stack.getCount() > 1) json.addProperty("count", stack.getCount());
        if (stack.getMetadata() != 0) json.addProperty("data", stack.getMetadata());
        if (stack.hasTagCompound()) {
            json.addProperty("nbt", stack.getTagCompound().toString());
        }
        return json;
    }

    private static JsonElement serializeFluidStack(FluidStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("fluid", stack.getFluid().getName());
        json.addProperty("amount", stack.amount);
        return json;
    }
}
