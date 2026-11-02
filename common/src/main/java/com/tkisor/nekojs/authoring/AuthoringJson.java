package com.tkisor.nekojs.authoring;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Strict JSON input and canonical identities shared by the authoring transports and query compiler. */
public final class AuthoringJson {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private AuthoringJson() {}

    public static JsonObject parse(String text) {
        JsonElement value = parseElement(text);
        if (!value.isJsonObject()) throw invalid("/", "Expected one JSON object.");
        return value.getAsJsonObject();
    }

    static JsonElement parseElement(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("/", "Expected one JSON value.");
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw invalid("/", "Malformed JSON request.");
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw invalid("/", "JSON nesting exceeds the limit.");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (result.has(name)) throw invalid("/", "Duplicate JSON object key.");
                    result.add(name, read(reader, depth + 1));
                }
                reader.endObject();
                yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) result.add(read(reader, depth + 1));
                reader.endArray();
                yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("/", "Unexpected JSON token.");
        };
    }

    static AuthoringException invalid(String path, String message) {
        return new AuthoringException(400, "INVALID_REQUEST", path, message);
    }

    static void keys(JsonObject object, String path, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : object.keySet()) if (!names.contains(key)) throw invalid(path + "/" + key.replace("~", "~0").replace("/", "~1"), "Unknown request field.");
    }

    static String string(JsonObject object, String key, String path) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw invalid(path + "/" + key, "Expected a non-empty string.");
        }
        return value.getAsString();
    }

    static JsonObject object(JsonObject object, String key, String path) {
        if (!object.has(key) || !object.get(key).isJsonObject()) throw invalid(path + "/" + key, "Expected an object.");
        return object.getAsJsonObject(key);
    }

    static int integer(JsonElement value, String path, int min, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(path, "Expected an integer.");
        try {
            int number = value.getAsBigDecimal().intValueExact();
            if (number >= min && number <= max) return number;
        } catch (ArithmeticException | NumberFormatException ignored) { }
        throw invalid(path, "Integer is outside the supported range.");
    }

    public static JsonObject obj(Object... fields) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) result.add((String) fields[i], GSON.toJsonTree(fields[i + 1]));
        return result;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /** Hashes compact JSON as UTF-8 without retaining a full serialized copy of the tree. */
    public static String hashJson(JsonElement value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var writer = new java.io.OutputStreamWriter(
                    new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest),
                    StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new AssertionError(e);
        }
    }

    static String canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            new TreeSet<>(value.getAsJsonObject().keySet()).forEach(key -> sorted.add(key,
                    JsonParser.parseString(canonical(value.getAsJsonObject().get(key)))));
            return sorted.toString();
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            value.getAsJsonArray().forEach(item -> array.add(JsonParser.parseString(canonical(item))));
            return array.toString();
        }
        return value.toString();
    }
}
