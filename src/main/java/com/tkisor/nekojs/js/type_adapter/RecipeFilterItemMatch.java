package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Converts recipe item selectors once and retains native match values rather than guest DTOs. */
public final class RecipeFilterItemMatch {
    private static final Set<String> STACK_KEYS = Set.of("id", "item", "count", "components");
    private static final Set<String> INGREDIENT_KEYS = Set.of("item", "tag", "mod", "regex", "wildcard", "filter", "any", "all", "not");

    private RecipeFilterItemMatch() {}

    public static RecipeFilter input(Value value) {
        return parse(value, true, false);
    }

    public static RecipeFilter output(Value value) {
        return parse(value, false, false);
    }

    private static RecipeFilter parse(Value value, boolean input, boolean exact) {
        if (value == null || value.isNull()) throw invalid(value, "Item match must not be null");
        if (!value.isHostObject() && value.hasMembers() && (value.hasMember("match") || value.hasMember("exact"))) {
            keys(value, Set.of("match", "exact"));
            if (!value.hasMember("match")) throw invalid(value, "Item match info requires 'match'");
            if (value.hasMember("exact") && !value.getMember("exact").isBoolean()) {
                throw invalid(value, "Item match info 'exact' must be boolean");
            }
            return parse(value.getMember("match"), input, value.hasMember("exact") && value.getMember("exact").asBoolean());
        }
        if (value.hasArrayElements()) {
            List<RecipeFilter> filters = new ArrayList<>();
            for (long index = 0; index < value.getArraySize(); index++) {
                filters.add(parse(value.getArrayElement(index), input, exact));
            }
            return new RecipeFilter.Or(List.copyOf(filters));
        }
        if (value.isString()) {
            String text = value.asString().trim();
            if (!exact && (text.startsWith("#") || plainItemId(text))) {
                return input ? new RecipeFilter.ByInput(text) : new RecipeFilter.ByOutput(text);
            }
            if (!ingredientSyntax(text)) return stack(value, input, exact);
        }
        if (stackShape(value)) return stack(value, input, exact);
        validateIngredient(value);
        Ingredient expected = new IngredientAdapter().apply(value);
        if (exact && !input) throw invalid(value, "Exact output matching requires a concrete item stack");
        if (!input) {
            return (holder, registries) -> expected.test(RecipeEventJS.getRecipeOutputStack(holder.value(), registries));
        }
        return (holder, registries) -> {
            for (Ingredient actual : RecipeEventJS.getIngredients(holder.value())) {
                if (exact ? expected.equals(actual) : overlaps(expected, actual)) return true;
            }
            return false;
        };
    }

    private static RecipeFilter stack(Value value, boolean input, boolean exact) {
        if (!value.isHostObject() && value.hasMembers()) keys(value, STACK_KEYS);
        ItemStack expected = new ItemStackAdapter().apply(value).copy();
        Predicate<ItemStack> matches = actual -> matchesStack(expected, actual, exact);
        if (!input) {
            return (holder, registries) -> matches.test(RecipeEventJS.getRecipeOutputStack(holder.value(), registries));
        }
        return (holder, registries) -> {
            for (Ingredient ingredient : RecipeEventJS.getIngredients(holder.value())) {
                // A supplied stack is a concrete witness; native ingredient predicates still decide acceptance.
                if (matches.test(expected) && ingredient.test(expected)) return true;
                for (ItemStack candidate : RecipeEventJS.getIngredientStacks(ingredient)) {
                    if (matches.test(candidate) && ingredient.test(candidate)) return true;
                }
            }
            return false;
        };
    }

    private static boolean matchesStack(ItemStack expected, ItemStack actual, boolean exact) {
        if (actual == null || expected.isEmpty() || actual.isEmpty() || expected.getItem() != actual.getItem()) return false;
        if (exact) return ItemStack.isSameItemSameComponents(expected, actual);
        // Only the explicitly supplied patch constrains default matches; count is deliberately ignored.
        for (var entry : expected.getComponentsPatch().entrySet()) {
            Object actualValue = actual.get(entry.getKey());
            if (entry.getValue().isPresent()) {
                if (!Objects.equals(entry.getValue().get(), actualValue)) return false;
            } else if (actualValue != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlaps(Ingredient expected, Ingredient actual) {
        for (ItemStack candidate : RecipeEventJS.getIngredientStacks(expected)) {
            if (expected.test(candidate) && actual.test(candidate)) return true;
        }
        for (ItemStack candidate : RecipeEventJS.getIngredientStacks(actual)) {
            if (expected.test(candidate) && actual.test(candidate)) return true;
        }
        return false;
    }

    private static boolean stackShape(Value value) {
        if (value.isHostObject()) {
            Object host = value.asHostObject();
            return host instanceof ItemStack || host instanceof Item || host instanceof NekoId;
        }
        return value.hasMembers() && (value.hasMember("id") || value.hasMember("components") || value.hasMember("count")
                || value.hasMember("item") && value.getMemberKeys().stream().allMatch(STACK_KEYS::contains));
    }

    private static void validateIngredient(Value value) {
        if (value == null || value.isNull()) throw invalid(value, "Ingredient match must not be null");
        if (value.isHostObject() || value.isString()) return;
        if (value.hasArrayElements()) {
            for (long index = 0; index < value.getArraySize(); index++) validateIngredient(value.getArrayElement(index));
            return;
        }
        if (!value.hasMembers() || value.getMemberKeys().isEmpty()) throw invalid(value, "Expected an item or ingredient match");
        keys(value, INGREDIENT_KEYS);
        for (String child : List.of("item", "any", "all", "not")) {
            if (value.hasMember(child)) validateIngredient(value.getMember(child));
        }
    }

    private static void keys(Value value, Set<String> allowed) {
        for (String key : value.getMemberKeys()) {
            if (!allowed.contains(key)) throw invalid(value, "Unknown item match key '" + key + "'");
        }
    }

    private static boolean ingredientSyntax(String text) {
        return text.startsWith("#") || text.startsWith("@") || text.startsWith("/") || text.equals("*");
    }

    private static boolean plainItemId(String text) {
        return !text.isEmpty() && !ingredientSyntax(text) && text.indexOf('[') < 0 && text.indexOf(' ') < 0
                && !text.matches("^[0-9]+x.*");
    }

    private static ValueConversionException invalid(Value value, String detail) {
        return new ValueConversionException(RecipeFilter.class,
                "native item/ingredient, item stack DTO, item array, or {match, exact:boolean}", value, detail);
    }
}
