package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

public final class RecipeFilterAdapter implements JSTypeAdapter<RecipeFilter> {
    @Override
    public Class<RecipeFilter> getTargetClass() { return RecipeFilter.class; }

    @Override
    public boolean canConvert(Value value) {
        return value != null && (value.isString() || value.hasMembers() || value.hasArrayElements());
    }

    @Override
    public RecipeFilter convert(Value value) {
        if (value == null || value.isNull()) return null;

        if (value.isString()) {
            return new RecipeFilter.ById(value.asString());
        }

        if (value.hasArrayElements()) {
            List<RecipeFilter> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                RecipeFilter sub = convert(value.getArrayElement(i));
                if (sub != null) list.add(sub);
            }
            return new RecipeFilter.Or(list);
        }

        List<RecipeFilter> andFilters = new ArrayList<>();

        if (value.hasMember("not")) {
            RecipeFilter sub = convert(value.getMember("not"));
            if (sub != null) andFilters.add(new RecipeFilter.Not(sub));
        }

        if (value.hasMember("output")) {
            andFilters.add(new RecipeFilter.ByOutput(value.getMember("output").asString()));
        }

        if (value.hasMember("input")) {
            andFilters.add(new RecipeFilter.ByInput(value.getMember("input").asString()));
        }

        if (value.hasMember("mod")) {
            andFilters.add(new RecipeFilter.ByMod(value.getMember("mod").asString()));
        }

        if (value.hasMember("id")) {
            andFilters.add(new RecipeFilter.ById(value.getMember("id").asString()));
        }

        if (value.hasMember("type")) {
            andFilters.add(new RecipeFilter.ByType(value.getMember("type").asString()));
        }

        if (andFilters.isEmpty()) return null;
        return andFilters.size() == 1 ? andFilters.get(0) : new RecipeFilter.And(andFilters);
    }
}