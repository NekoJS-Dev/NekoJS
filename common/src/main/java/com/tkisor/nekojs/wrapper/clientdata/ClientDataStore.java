package com.tkisor.nekojs.wrapper.clientdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store for server-to-client key-value data ({@code ClientData.sync} /
 * {@code clientData.get}).
 *
 * <p>Plain common code with no Minecraft dependency. The platform network layer receives a
 * {@code ClientDataSyncPacket} and calls {@link #accept(String, JsonElement)} on the main thread
 * to write it; scripts read the data through the {@code clientData} binding, read-only. Values
 * are kept as gson {@link JsonElement}s, so the network carries a JSON string and all three
 * platforms share one codec. On read, {@link #jsonToObject} turns them back into
 * Map/List/String/Number/Boolean/null for GraalJS.
 *
 * <p>v1 semantics: a value with the same key is overwritten. On disconnect and world change the
 * platform client hook calls {@link #clear()}; on NeoForge it is hooked to level unload, the same
 * point as {@code PDataSyncService.clearClientMirrors}.
 *
 * <p>JSON types (string/number/bool/object/array/null) round-trip unchanged. Any other Java type
 * is rejected by {@link #toJsonElement(Object)}, which throws {@link IllegalArgumentException}.
 */
public final class ClientDataStore {

    /** Key length cap: 256, wider than the 64 used for script network channels, and enough to name
     * HUD and GUI data slots. */
    public static final int MAX_KEY_LENGTH = 256;

    /** Process-wide shared instance. Packet handling on the client and the {@code clientData}
     * bindings of every ScriptType all read and write it. */
    public static final ClientDataStore SHARED = new ClientDataStore();

    private final Map<String, JsonElement> data = new ConcurrentHashMap<>();

    /** Writes a value received in a sync packet, overwriting the old one. v1 carries no version
     * number, so the last write for a key wins. */
    public void accept(String key, JsonElement value) {
        data.put(requireKey(key), requireValue(value));
    }

    /** Returns the value for the key converted back into a script-friendly object, or {@code null}
     * when the key is missing or its value is JSON null. */
    public Object get(String key) {
        JsonElement element = data.get(requireKey(key));
        return element == null ? null : jsonToObject(element);
    }

    /** Tells whether the key exists. This also returns {@code true} when the value is JSON null,
     * which distinguishes it from a {@code null} returned by {@link #get}. */
    public boolean has(String key) {
        return data.containsKey(requireKey(key));
    }

    /** Returns a snapshot of all current keys, in no particular order. */
    public Set<String> keys() {
        return Set.copyOf(data.keySet());
    }

    /** Returns the current number of entries. */
    public int size() {
        return data.size();
    }

    /** Drops all data. Called by the disconnect and world-change hooks. */
    public void clear() {
        data.clear();
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "client data key must be non-blank and at most " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }

    private static JsonElement requireValue(JsonElement value) {
        if (value == null) {
            throw new IllegalArgumentException("client data value must not be null (use JsonNull for JSON null)");
        }
        return value;
    }

    /**
     * Converts any value coming from a script into a gson {@link JsonElement}.
     *
     * <p>By default GraalJS maps an {@code Object} parameter like this: a JS object becomes a
     * {@code Map}, an array becomes a {@code List}, and a primitive becomes its boxed type. A Graal
     * {@link Value} passed directly goes through {@link JsonObjectAdapter#convertValueToJson}. A
     * value that is already a {@link JsonElement} passes through unchanged. Anything else, such as a
     * function or a host object, throws {@link IllegalArgumentException}.
     */
    public static JsonElement toJsonElement(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement element) return element;
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof String string) return new JsonPrimitive(string);
        if (value instanceof Character character) return new JsonPrimitive(character);
        if (value instanceof Number number) {
            // Keep int/long precision for integer types and use double for the rest, matching
            // JsonObjectAdapter.convertValueToJson.
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return new JsonPrimitive(number.longValue());
            }
            return new JsonPrimitive(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), toJsonElement(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Collection<?> collection) {
            JsonArray array = new JsonArray();
            for (Object element : collection) {
                array.add(toJsonElement(element));
            }
            return array;
        }
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.add(toJsonElement(java.lang.reflect.Array.get(value, i)));
            }
            return array;
        }
        if (value instanceof Value graalValue) {
            return JsonObjectAdapter.convertValueToJson(graalValue);
        }
        throw new IllegalArgumentException(
                "client data values must be JSON types (string/number/bool/object/array), got "
                        + value.getClass().getName());
    }

    /**
     * Converts a gson {@link JsonElement} back into a script-friendly object: an object becomes a
     * {@code LinkedHashMap}, an array an {@code ArrayList}, a primitive its boxed type, null stays
     * {@code null}. Integers become {@link Long} and fractions {@link Double}, because JSON does not
     * distinguish int from double; values keep their numeric value on the round trip.
     */
    public static Object jsonToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isString()) return primitive.getAsString();
            if (primitive.isNumber()) {
                // gson hands back LazilyParsedNumber, which does not distinguish int from double, so decide on
                // the text form: without '.'/'e'/'E' treat it as an integer and use Long, otherwise use Double.
                // This is the inverse of serialization (integers stored as long, the rest as double), so
                // numbers round-trip without loss.
                String raw = primitive.getAsString();
                if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                    return primitive.getAsLong();
                }
                return primitive.getAsDouble();
            }
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                list.add(jsonToObject(child));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), jsonToObject(entry.getValue()));
            }
            return map;
        }
        return null;
    }
}
