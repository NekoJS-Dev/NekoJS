package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.JSTypeAdapter;
import org.graalvm.polyglot.Value;

public final class JsonObjectAdapter implements JSTypeAdapter<JsonObject> {

    @Override
    public Class<JsonObject> getTargetClass() {
        return JsonObject.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.hasMembers();
    }

    @Override
    public JsonObject convert(Value value) {
        return convertValueToJsonObject(value);
    }

    public static JsonObject convertValueToJsonObject(Value value) {
        JsonObject obj = new JsonObject();
        for (String key : value.getMemberKeys()) {
            obj.add(key, convertValueToJson(value.getMember(key)));
        }
        return obj;
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
}