package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeMatchContext;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts native filter instances, ID expressions, context predicates and nested filter data.
 * Object fields intersect; plain arrays form a union. Unknown keys are rejected before
 * conversion so misspellings cannot silently broaden a destructive recipe operation.
 * The empty object intentionally matches all recipes.
 */
public final class RecipeFilterAdapter implements JSTypeAdapter<RecipeFilter> {
    /** Accepted object keys; unknown keys fail instead of being ignored. */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "not", "and", "or", "output", "input", "mod", "group",
            "id", "idStartsWith", "idEndsWith", "idContains", "type", "recipeType");

    @Override
    public Class<RecipeFilter> getTargetClass() { return RecipeFilter.class; }

    @Override
    public ConversionPrecedence getPrecedence() {
        // The generic SAM conversion supplies (holder, registries), not our script match context.
        return ConversionPrecedence.HIGH;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        AdapterInputShape expression = named("Expression", union(string(), raw("RegExp"), host(Pattern.class)));
        AdapterInputShape itemMatch = union(host(Ingredient.class), host(ItemStack.class));
        AdapterInputShape richInput = named("Input", union(itemMatch, object(
                Slot.req("match", itemMatch), Slot.opt("exact", bool()))));
        AdapterInputShape richOutput = named("Output", union(itemMatch,
                object(Slot.req("match", host(ItemStack.class)), Slot.opt("exact", bool())),
                object(Slot.req("match", host(Ingredient.class)), Slot.opt("exact", raw("false")))));
        return List.of(
                self(),
                expression,
                callable(List.of(Slot.req("match", host(RecipeMatchContext.class))), bool()),
                arrayOf(self()),
                named("Conditions", object(
                        Slot.opt("id", expression),
                        Slot.opt("mod", expression),
                        Slot.opt("group", expression),
                        Slot.opt("type", registry("RecipeSerializer")),
                        Slot.opt("recipeType", registry("RecipeType")),
                        Slot.opt("input", richInput),
                        Slot.opt("output", richOutput),
                        Slot.opt("not", self()),
                        Slot.opt("and", self()),
                        Slot.opt("or", self()),
                        Slot.opt("idStartsWith", string()),
                        Slot.opt("idEndsWith", string()),
                        Slot.opt("idContains", string()))));
    }

    @Override
    public boolean test(Value value) {
        return value != null && (value.isString() || value.canExecute()
                || RecipeFilterExpressions.isExpression(value) || value.hasMembers() || value.hasArrayElements());
    }

    @Override
    public RecipeFilter apply(Value value) {
        if (value == null || value.isNull()) return null;

        if (value.isHostObject() && value.asHostObject() instanceof RecipeFilter filter) return filter;
        if (RecipeFilterExpressions.isExpression(value)) return RecipeFilterExpressions.forId(value);
        if (value.canExecute()) return RecipeFilterExpressions.contextualCallback(value);
        if (value.isString()) return RecipeFilterExpressions.forId(value);

        if (value.hasArrayElements()) {
            List<RecipeFilter> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                RecipeFilter sub = apply(value.getArrayElement(i));
                if (sub != null) list.add(sub);
            }
            return new RecipeFilter.Or(list);
        }

        // Unsupported scalars must not fall through to an empty, match-all conjunction.
        if (!value.hasMembers()) {
            throw new ValueConversionException(RecipeFilter.class, "recipe filter string / array / object",
                    value, "unsupported recipe filter value");
        }

        // Validate every key before parsing any condition. An empty object intentionally matches all;
        // a misspelled condition must never become that empty conjunction.
        for (String key : value.getMemberKeys()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw new ValueConversionException(RecipeFilter.class, "recipe filter object with documented keys",
                        value, "[NEKO-5003] RecipeFilterAdapter: unknown key '" + key + "'; expected documented filter keys; conditions must not be silently discarded");
            }
        }

        List<RecipeFilter> andFilters = new ArrayList<>();

        if (value.hasMember("not")) {
            RecipeFilter sub = apply(value.getMember("not"));
            if (sub != null) andFilters.add(new RecipeFilter.Not(sub));
        }

        if (value.hasMember("and")) {
            Value group = value.getMember("and");
            if (group != null && group.hasArrayElements()) {
                // Explicit conjunction arrays must not inherit the top-level array OR rule.
                List<RecipeFilter> children = new ArrayList<>();
                for (long i = 0; i < group.getArraySize(); i++) {
                    RecipeFilter child = apply(group.getArrayElement(i));
                    if (child != null) children.add(child);
                }
                andFilters.add(new RecipeFilter.And(children));
            } else {
                RecipeFilter sub = apply(group);
                if (sub != null) andFilters.add(sub);
            }
        }

        if (value.hasMember("or")) {
            RecipeFilter sub = apply(value.getMember("or"));
            if (sub != null) andFilters.add(sub);
        }

        if (value.hasMember("output")) {
            andFilters.add(RecipeFilterItemMatch.output(value.getMember("output")));
        }

        if (value.hasMember("input")) {
            andFilters.add(RecipeFilterItemMatch.input(value.getMember("input")));
        }

        if (value.hasMember("mod")) {
            andFilters.add(RecipeFilterExpressions.forMod(value.getMember("mod")));
        }

        if (value.hasMember("group")) {
            andFilters.add(RecipeFilterExpressions.forGroup(value.getMember("group")));
        }

        if (value.hasMember("id")) {
            andFilters.add(RecipeFilterExpressions.forId(value.getMember("id")));
        }

        if (value.hasMember("idStartsWith")) {
            andFilters.add(new RecipeFilter.ByIdStartsWith(value.getMember("idStartsWith").asString()));
        }

        if (value.hasMember("idEndsWith")) {
            andFilters.add(new RecipeFilter.ByIdEndsWith(value.getMember("idEndsWith").asString()));
        }

        if (value.hasMember("idContains")) {
            andFilters.add(new RecipeFilter.ByIdContains(value.getMember("idContains").asString()));
        }

        if (value.hasMember("type")) {
            andFilters.add(new RecipeFilter.ByType(value.getMember("type").asString()));
        }
        if (value.hasMember("recipeType")) {
            andFilters.add(new RecipeFilter.ByRecipeType(value.getMember("recipeType").asString()));
        }

        if (andFilters.isEmpty()) {
            // An empty object means no constraints, so it matches everything: an And with an empty list is always
            // true, and apply never returns null where callers would NPE
            return new RecipeFilter.And(List.of());
        }
        return andFilters.size() == 1 ? andFilters.get(0) : new RecipeFilter.And(andFilters);
    }
}
