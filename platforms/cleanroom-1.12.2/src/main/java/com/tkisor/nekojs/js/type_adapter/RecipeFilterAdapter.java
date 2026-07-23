package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 simplified RecipeFilter adapter.
 *
 * <p>Parses filter criteria from JS objects into structured data maps.
 * Since 1.12.2 lacks RecipeHolder/HolderLookup/Registries APIs,
 * the filter objects are returned as structured data for downstream use.
 */
public final class RecipeFilterAdapter implements JSTypeAdapter<Object> {

    @Override
    public Class<Object> getTargetClass() {
        return Object.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                string(),
                arrayOf(self()),
                object(
                        Slot.opt("not", self()),
                        Slot.opt("and", self()),
                        Slot.opt("or", self()),
                        Slot.opt("output", string()),
                        Slot.opt("input", string()),
                        Slot.opt("mod", string()),
                        Slot.opt("group", string()),
                        Slot.opt("id", string()),
                        Slot.opt("idStartsWith", string()),
                        Slot.opt("idEndsWith", string()),
                        Slot.opt("idContains", string()),
                        Slot.opt("type", string())));
    }

    @Override
    public boolean test(Value value) {
        return value != null && (value.isString() || value.hasMembers() || value.hasArrayElements());
    }

    @Override
    public Object apply(Value value) {
        if (value == null || value.isNull()) return null;

        if (value.isString()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", value.asString());
            return result;
        }

        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                Object sub = apply(value.getArrayElement(i));
                if (sub != null) list.add(sub);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("or", list);
            return result;
        }

        Map<String, Object> filter = new LinkedHashMap<>();

        if (value.hasMember("not")) {
            Object sub = apply(value.getMember("not"));
            if (sub != null) filter.put("not", sub);
        }

        if (value.hasMember("and")) {
            Object sub = apply(value.getMember("and"));
            if (sub != null) filter.put("and", sub);
        }

        if (value.hasMember("or")) {
            Object sub = apply(value.getMember("or"));
            if (sub != null) filter.put("or", sub);
        }

        copyStringMember(value, filter, "output");
        copyStringMember(value, filter, "input");
        copyStringMember(value, filter, "mod");
        copyStringMember(value, filter, "group");
        copyStringMember(value, filter, "id");
        copyStringMember(value, filter, "idStartsWith");
        copyStringMember(value, filter, "idEndsWith");
        copyStringMember(value, filter, "idContains");
        copyStringMember(value, filter, "type");

        return filter.isEmpty() ? null : filter;
    }

    private void copyStringMember(Value value, Map<String, Object> filter, String key) {
        if (value.hasMember(key)) {
            filter.put(key, value.getMember(key).asString());
        }
    }
}
