package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.StableRecipeBuilder;
import com.tkisor.nekojs.api.recipe.StableRecipeEvent;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.js.type_adapter.RecipeFilterAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class StableRecipeEventProjection implements StableRecipeEvent {
    private static final Pattern COUNTED_INGREDIENT = Pattern.compile("^([0-9]+)x\\s+(.+)$");
    private static final int MAX_INGREDIENTS = 9;
    private static final RecipeFilterAdapter FILTER_ADAPTER = new RecipeFilterAdapter();

    private final RecipeEventJS event;

    public StableRecipeEventProjection(RecipeEventJS event) {
        this.event = event;
    }

    @Override
    public StableRecipeBuilder shapeless(String result, List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            throw invalidIngredients(ingredients);
        }
        List<Ingredient> resolved = new ArrayList<>();
        for (Object entry : ingredients) {
            if (!(entry instanceof String text)) {
                throw invalidIngredients(entry);
            }
            String ingredientId = text.trim();
            int count = 1;
            var counted = COUNTED_INGREDIENT.matcher(ingredientId);
            if (counted.matches()) {
                try {
                    count = Integer.parseInt(counted.group(1));
                } catch (NumberFormatException failure) {
                    throw invalidIngredients(text);
                }
                ingredientId = counted.group(2);
            }
            if (count < 1 || count > MAX_INGREDIENTS - resolved.size()) {
                throw invalidIngredients(text);
            }
            Ingredient ingredient = IngredientResolver.fromString(ingredientId);
            resolved.addAll(Collections.nCopies(count, ingredient));
        }
        return new Builder(event.shapeless(ItemStackAdapter.stringToItemStack(result), resolved));
    }

    @Override
    public int stage(Map<String, String> filter, String stage) {
        return event.stage(convertFilter(filter), stage);
    }

    @Override
    public int stage(Map<String, String> filter, String stage, String scope) {
        return event.stage(convertFilter(filter), stage, scope);
    }

    private static RecipeFilter convertFilter(Map<String, String> filter) {
        if (filter == null) {
            throw new ValueConversionException(Map.class, "recipe filter with string conditions", null,
                    "recipe filter is required");
        }
        Map<String, Object> conditions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : filter.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) {
                throw new ValueConversionException(Map.class, "recipe filter with string conditions", filter,
                        "recipe filter keys and values must be strings");
            }
            conditions.put(key, value);
        }
        return FILTER_ADAPTER.apply(Value.asValue(ProxyObject.fromMap(conditions)));
    }

    private static ValueConversionException invalidIngredients(Object value) {
        return new ValueConversionException(List.class, "one to nine ingredient slots", value,
                "expected ingredient strings with positive counts within the shapeless recipe limit");
    }

    private static final class Builder implements StableRecipeBuilder {
        private final RecipeJsonBuilder recipe;

        private Builder(RecipeJsonBuilder recipe) {
            this.recipe = recipe;
        }

        @Override
        public StableRecipeBuilder id(String id) {
            if (id != null) {
                int separator = id.indexOf(':');
                id = separator < 0 ? id.trim()
                        : id.substring(0, separator).trim() + ':' + id.substring(separator + 1).trim();
            }
            recipe.id(id);
            return this;
        }

        @Override
        public StableRecipeBuilder stage(String stage) {
            recipe.stage(stage);
            return this;
        }

        @Override
        public StableRecipeBuilder stage(String stage, String scope) {
            recipe.stage(stage, scope);
            return this;
        }
    }
}
