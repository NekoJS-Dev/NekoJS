package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class RecipeJsonPath {
    private RecipeJsonPath() {}

    static void set(JsonObject root, String path, JsonElement value) {
        List<String> segments = segments(path);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Recipe JSON path cannot be empty");
        }

        JsonElement current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            String segment = segments.get(i);
            String next = segments.get(i + 1);
            current = childOrCreate(current, segment, next, path);
        }

        String last = segments.getLast();
        if (current.isJsonObject()) {
            current.getAsJsonObject().add(last, value);
            return;
        }
        if (current.isJsonArray()) {
            JsonArray array = current.getAsJsonArray();
            int index = index(last, path);
            grow(array, index, nextContainer(null));
            array.set(index, value);
            return;
        }
        throw new IllegalArgumentException("Recipe JSON path does not point into an object or array: " + path);
    }

    static void remove(JsonObject root, String path) {
        List<String> segments = segments(path);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Recipe JSON path cannot be empty");
        }

        JsonElement current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            current = child(current, segments.get(i), path);
        }

        String last = segments.getLast();
        if (current.isJsonObject()) {
            current.getAsJsonObject().remove(last);
            return;
        }
        if (current.isJsonArray()) {
            JsonArray array = current.getAsJsonArray();
            int index = index(last, path);
            if (index < 0 || index >= array.size()) {
                throw new IllegalArgumentException("Recipe JSON path index out of bounds: " + path);
            }
            array.remove(index);
            return;
        }
        throw new IllegalArgumentException("Recipe JSON path does not point into an object or array: " + path);
    }

    private static JsonElement childOrCreate(JsonElement parent, String segment, String next, String path) {
        if (parent.isJsonObject()) {
            JsonObject object = parent.getAsJsonObject();
            JsonElement child = object.get(segment);
            if (child == null || child.isJsonNull()) {
                child = nextContainer(next);
                object.add(segment, child);
            }
            return child;
        }
        if (parent.isJsonArray()) {
            JsonArray array = parent.getAsJsonArray();
            int index = index(segment, path);
            grow(array, index, nextContainer(next));
            JsonElement child = array.get(index);
            if (child.isJsonNull()) {
                child = nextContainer(next);
                array.set(index, child);
            }
            return child;
        }
        throw new IllegalArgumentException("Recipe JSON path cannot traverse primitive value: " + path);
    }

    private static JsonElement child(JsonElement parent, String segment, String path) {
        if (parent.isJsonObject()) {
            JsonElement child = parent.getAsJsonObject().get(segment);
            if (child == null) {
                throw new IllegalArgumentException("Recipe JSON path does not exist: " + path);
            }
            return child;
        }
        if (parent.isJsonArray()) {
            JsonArray array = parent.getAsJsonArray();
            int index = index(segment, path);
            if (index < 0 || index >= array.size()) {
                throw new IllegalArgumentException("Recipe JSON path index out of bounds: " + path);
            }
            return array.get(index);
        }
        throw new IllegalArgumentException("Recipe JSON path cannot traverse primitive value: " + path);
    }

    private static JsonElement nextContainer(String next) {
        return next != null && isInteger(next) ? new JsonArray() : new JsonObject();
    }

    private static void grow(JsonArray array, int index, JsonElement filler) {
        if (index < 0) {
            throw new IllegalArgumentException("Recipe JSON path array index cannot be negative: " + index);
        }
        while (array.size() <= index) {
            array.add(filler.deepCopy());
        }
    }

    private static List<String> segments(String path) {
        if (path == null || path.isBlank()) return List.of();

        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        boolean bracket = false;
        char quote = 0;
        boolean bracketQuoted = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (bracket) {
                if (c == '\'' || c == '"') {
                    quote = c;
                    bracketQuoted = true;
                } else if (c == ']') {
                    segments.add(current.toString());
                    current.setLength(0);
                    bracket = false;
                    bracketQuoted = false;
                } else if (!bracketQuoted || !Character.isWhitespace(c)) {
                    current.append(c);
                }
            } else if (c == '[') {
                if (!current.isEmpty()) {
                    segments.add(current.toString());
                    current.setLength(0);
                }
                bracket = true;
            } else if (c == '.') {
                if (!current.isEmpty()) {
                    segments.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        if (quote != 0 || bracket) {
            throw new IllegalArgumentException("Recipe JSON path has an unterminated bracket or quote: " + path);
        }
        if (!current.isEmpty()) segments.add(current.toString());
        return segments;
    }

    private static int index(String segment, String path) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Recipe JSON path segment is not an array index in " + path + ": " + segment, e);
        }
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i++) {
            if (i == 0 && value.charAt(i) == '-') continue;
            if (!Character.isDigit(value.charAt(i))) return false;
            hasDigit = true;
        }
        return hasDigit;
    }
}
