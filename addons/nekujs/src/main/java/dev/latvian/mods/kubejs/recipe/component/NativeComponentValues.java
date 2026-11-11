package dev.latvian.mods.kubejs.recipe.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.latvian.mods.rhino.Wrapper;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared bridge from legacy input representations to existing native value converters. */
public final class NativeComponentValues {
    private NativeComponentValues() {}

    static Object scalar(Object input) {
        input = Wrapper.unwrapped(input);
        if (input instanceof Value value) {
            if (value.isHostObject()) return scalar(value.asHostObject());
            if (value.isNull()) return null;
            if (value.isString()) return value.asString();
            if (value.isBoolean()) return value.asBoolean();
            if (value.isNumber()) {
                if (value.fitsInInt()) return value.asInt();
                if (value.fitsInLong()) return value.asLong();
                return value.asDouble();
            }
        }
        if (input instanceof JsonPrimitive json) {
            if (json.isBoolean()) return json.getAsBoolean();
            if (json.isNumber()) return json.getAsNumber();
            return json.getAsString();
        }
        return input;
    }

    public static Value value(Object input) {
        input = scalar(input);
        if (input instanceof Value value) return value;
        Object structured = structured(input);
        if (structured instanceof ProxyObject || structured instanceof ProxyArray) {
            // Proxy members require the caller's entered context; a context-free Value is only a host wrapper.
            graal.graalvm.polyglot.Context context;
            try {
                context = graal.graalvm.polyglot.Context.getCurrent();
            } catch (IllegalStateException failure) {
                throw new IllegalStateException("[NEKO-5367] Structured recipe conversion requires the caller's active Graal context", failure);
            }
            return context.asValue(structured);
        }
        return Value.asValue(structured);
    }

    private static Object structured(Object input) {
        if (input instanceof JsonElement json) {
            if (json.isJsonNull()) return null;
            if (json.isJsonPrimitive()) return scalar(json);
            if (json.isJsonArray()) {
                Object[] values = new Object[json.getAsJsonArray().size()];
                for (int i = 0; i < values.length; i++) values[i] = structured(json.getAsJsonArray().get(i));
                return ProxyArray.fromArray(values);
            }
            Map<String, Object> values = new LinkedHashMap<>();
            json.getAsJsonObject().entrySet().forEach(entry -> values.put(entry.getKey(), structured(entry.getValue())));
            return ProxyObject.fromMap(values);
        }
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), structured(value)));
            return ProxyObject.fromMap(values);
        }
        return input;
    }
}
