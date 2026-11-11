package com.tkisor.nekujs.recipe.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeBuilderSession;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.crafting.Ingredient;

/** Applies supported shaped-parent cleanup in the native event's final encode lifecycle. */
final class NativeShapedRecipeExecution implements RecipeExecutionFactory {
    private final RegistryAccessContainer registries;

    NativeShapedRecipeExecution(RegistryAccessContainer registries) {
        this.registries = registries;
    }

    @Override
    public Object create(RecipeBuilder builder, RecipeSchemaHost host, RecipeBuilderSession session) {
        session.beforeEncode(() -> {
            JsonObject prepared = prepare(session.snapshot());
            builder.setPath("pattern", new RecipeJsonValue(prepared.get("pattern")));
            builder.setPath("key", new RecipeJsonValue(prepared.get("key")));
        });
        return null;
    }

    @Override
    public void validatePreparedFields(Map<String, JsonElement> fields) {
        JsonObject recipe = new JsonObject();
        fields.forEach((name, value) -> recipe.add(name, value.deepCopy()));
        prepare(recipe);
    }

    private JsonObject prepare(JsonObject recipe) {
        for (String control : List.of("kubejs:mirror", "kubejs:shrink")) {
            if (recipe.has(control) && (!recipe.get(control).isJsonPrimitive()
                    || !recipe.get(control).getAsJsonPrimitive().isBoolean() || !recipe.get(control).getAsBoolean())) {
                throw invalid("Non-default mirror/shrink controls require a serializer capability not supplied by this native parent");
            }
        }
        if (!recipe.has("pattern") || !recipe.get("pattern").isJsonArray()
                || recipe.getAsJsonArray("pattern").isEmpty()
                || !recipe.has("key") || !recipe.get("key").isJsonObject()
                || recipe.getAsJsonObject("key").isEmpty()) {
            throw invalid("Shaped recipes require a nonempty pattern and key map");
        }
        List<String> rows = new ArrayList<>();
        int width = -1;
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            if (!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isString()
                    || row.getAsString().isEmpty()) throw invalid("Pattern rows must be nonempty strings");
            String text = row.getAsString();
            if (width >= 0 && text.length() != width) throw invalid("Pattern rows must have equal width");
            width = text.length();
            rows.add(text);
        }
        JsonObject available = new JsonObject();
        Set<Character> empty = new LinkedHashSet<>();
        for (var entry : recipe.getAsJsonObject("key").entrySet()) {
            String key = entry.getKey();
            if (key.length() != 1 || key.charAt(0) == ' ') throw invalid("Pattern keys must be single non-space characters");
            Ingredient ingredient = Ingredient.CODEC.parse(registries.json(), entry.getValue())
                    .getOrThrow(message -> invalid("Invalid pattern ingredient: " + message));
            if (ingredient.isEmpty()) empty.add(key.charAt(0));
            else available.add(key, entry.getValue().deepCopy());
        }
        JsonArray pattern = new JsonArray();
        JsonObject used = new JsonObject();
        for (String row : rows) {
            StringBuilder normalized = new StringBuilder(row.length());
            for (int index = 0; index < row.length(); index++) {
                char symbol = row.charAt(index);
                if (empty.contains(symbol)) symbol = ' ';
                normalized.append(symbol);
                if (symbol == ' ') continue;
                String key = Character.toString(symbol);
                if (!available.has(key)) throw invalid("Pattern symbol has no ingredient: " + key);
                used.add(key, available.get(key).deepCopy());
            }
            pattern.add(normalized.toString());
        }
        if (used.isEmpty()) throw invalid("A shaped recipe must retain at least one nonempty ingredient");
        recipe.add("pattern", pattern);
        recipe.add("key", used);
        return recipe;
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("[NEKO-5309] Native shaped recipe preparation failed: " + detail);
    }
}
