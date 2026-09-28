package com.tkisor.nekojs.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import graal.graalvm.polyglot.Value;

public final class JsonObjectAdapter implements JSTypeAdapter<JsonObject> {

    @Override
    public Class<JsonObject> getTargetClass() {
        return JsonObject.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                object());
    }

    @Override
    public boolean test(Value value) {
        return value.hasMembers();
    }

    @Override
    public JsonObject apply(Value value) {
        return convertValueToJsonObject(value);
    }

    public static JsonObject convertValueToJsonObject(Value value) {
        JsonObject obj = new JsonObject();
        for (String key : value.getMemberKeys()) {
            obj.add(key, convertValueToJson(value.getMember(key)));
        }
        return obj;
    }

    /**
     * 把字符串包装为 gson {@link JsonPrimitive}，供需要走 JsonElement 路径的适配器
     * （如 {@code CodecAdapter} 的 string 分支）复用。
     */
    public static JsonElement primitiveJson(String s) {
        return new JsonPrimitive(s);
    }

    public static JsonElement convertValueToJson(Value value) {
        if (value.isNull()) return JsonNull.INSTANCE;
        if (value.isBoolean()) return new JsonPrimitive(value.asBoolean());

        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return new JsonPrimitive(value.asInt());
            } else if (value.fitsInLong()) {
                return new JsonPrimitive(value.asLong());
            } else {
                return new JsonPrimitive(value.asDouble());
            }
        }

        if (value.isString()) return new JsonPrimitive(value.asString());

        if (value.hasArrayElements()) {
            JsonArray array = new JsonArray();
            for (long i = 0; i < value.getArraySize(); i++) {
                array.add(convertValueToJson(value.getArrayElement(i)));
            }
            return array;
        }

        if (value.hasMembers()) {
            return convertValueToJsonObject(value);
        }

        return new JsonPrimitive(value.toString());
    }

    /**
     * Converts a host-side value received on an {@code Object}-typed parameter into a Gson tree.
     *
     * <p>On the real engine, guest values proxied into an {@code Object} parameter never arrive
     * as {@link Value} — GraalJS maps them to host proxies instead: JS objects (and any guest
     * object with members) arrive as {@code Map} ({@code PolyglotMap}), JS arrays as
     * {@code List} ({@code PolyglotList}), primitives as boxed types, {@code null}/{@code
     * undefined} as Java {@code null}. A {@link Value} argument (from Java-side callers or
     * explicit {@code Value} parameters) and pre-built {@link JsonElement} trees pass through.
     *
     * <p>Anything else is rejected instead of being stringified: silent {@code String.valueOf}
     * fallbacks previously turned unknown shapes into literal {@code [object Object]} files.
     */
    public static JsonElement convertHostValueToJson(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Value graalValue) return convertValueToJson(graalValue);
        if (value instanceof JsonElement element) return element;
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), convertHostValueToJson(entry.getValue()));
            }
            return object;
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object element : list) {
                array.add(convertHostValueToJson(element));
            }
            return array;
        }
        throw new IllegalArgumentException(
                "Cannot serialize value of type " + value.getClass().getName()
                        + " to JSON; expected a JS object, array, string, number, or boolean");
    }
}