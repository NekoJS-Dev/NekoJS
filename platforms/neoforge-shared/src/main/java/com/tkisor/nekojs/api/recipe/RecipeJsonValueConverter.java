package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS;
import com.tkisor.nekojs.wrapper.item.IngredientJS;
import com.tkisor.nekojs.wrapper.item.SizedIngredientJS;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.Map;

public final class RecipeJsonValueConverter {
    private RecipeJsonValueConverter() {}

    public static RecipeJsonValue wrap(Object value) {
        return new RecipeJsonValue(value);
    }

    public static JsonElement toJson(RecipeEventJS event, RecipeJsonValue value) {
        return convertObject(event, value.value());
    }

    public static JsonElement toJson(RecipeEventJS event, Object value) {
        return convertObject(event, value);
    }

    private static JsonElement convertObject(RecipeEventJS event, Object value) {
        switch (value) {
            case null -> {
                return JsonNull.INSTANCE;
            }
            case RecipeJsonValue recipeJsonValue -> {
                return toJson(event, recipeJsonValue);
            }
            case JsonElement json -> {
                return json;
            }
            case Number number -> {
                return new JsonPrimitive(number);
            }
            case String string -> {
                return new JsonPrimitive(string);
            }
            case Boolean bool -> {
                return new JsonPrimitive(bool);
            }
            case Character character -> {
                return new JsonPrimitive(character);
            }
            case Value jsValue -> {
                return convertValue(event, jsValue);
            }
            case IngredientJS ingredient -> {
                return event.serializeIngredient(ingredient.unwrap());
            }
            case Ingredient ingredient -> {
                return event.serializeIngredient(ingredient);
            }
            case SizedIngredientJS ingredient -> {
                return sizedIngredientToJson(event, ingredient.unwrap());
            }
            case SizedIngredient ingredient -> {
                return sizedIngredientToJson(event, ingredient);
            }
            case FluidIngredientJS ingredient -> {
                return event.serializeFluidIngredient(ingredient.unwrap());
            }
            case FluidIngredient ingredient -> {
                return event.serializeFluidIngredient(ingredient);
            }
            case SizedFluidIngredient ingredient -> {
                return event.serializeSizedFluidIngredient(ingredient);
            }
            case FluidStack stack -> {
                return event.serializeFluidStack(stack);
            }
            case ItemStack stack -> {
                return event.serializeResult(stack);
            }
            case List<?> list -> {
                JsonArray array = new JsonArray();
                for (Object item : list) {
                    array.add(convertObject(event, item));
                }
                return array;
            }
            case Map<?, ?> map -> {
                JsonObject json = new JsonObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    json.add(String.valueOf(entry.getKey()), convertObject(event, entry.getValue()));
                }
                return json;
            }
            default -> {
                return new JsonPrimitive(value.toString());
            }
        }
    }

    private static JsonElement convertValue(RecipeEventJS event, Value value) {
        if (value.isNull()) return JsonNull.INSTANCE;
        if (value.isHostObject()) return convertObject(event, value.asHostObject());
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
                array.add(convertValue(event, value.getArrayElement(i)));
            }
            return array;
        }

        if (value.hasMembers()) {
            JsonObject json = new JsonObject();
            for (String key : value.getMemberKeys()) {
                json.add(key, convertValue(event, value.getMember(key)));
            }
            return json;
        }

        return new JsonPrimitive(value.toString());
    }

    private static JsonElement sizedIngredientToJson(RecipeEventJS event, SizedIngredient ingredient) {
        JsonObject json = new JsonObject();
        json.add("ingredient", event.serializeIngredient(ingredient.ingredient()));
        json.addProperty("count", ingredient.count());
        return json;
    }
}
