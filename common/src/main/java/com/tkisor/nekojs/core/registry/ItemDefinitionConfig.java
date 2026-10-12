package com.tkisor.nekojs.core.registry;

import com.google.gson.JsonElement;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Owns detached, deterministic JSON configuration for a startup item definition. */
public final class ItemDefinitionConfig {
    private Map<String, Object> components = Map.of();
    private Map<String, String> textures = Map.of();
    private String parentModel;
    private String jukeboxPlayable;
    private String trimMaterial;
    private boolean frozen;

    public Map<String, Object> components() { return components; }
    public Map<String, String> textures() { return textures; }
    public String parentModel() { return parentModel; }
    public String jukeboxPlayable() { return jukeboxPlayable; }
    public String trimMaterial() { return trimMaterial; }

    public void component(String id, Object value) {
        Map<String, Object> replacement = new TreeMap<>(components);
        replacement.put(resourceId(id), value);
        components(replacement);
    }

    public void components(Map<String, Object> values) {
        writable();
        if (values == null) throw invalid("components must be an object");
        Map<String, Object> replacement = new TreeMap<>();
        for (var entry : values.entrySet()) {
            String id = resourceId(entry.getKey());
            if (entry.getValue() == null) throw invalid("component '" + id + "' cannot be null");
            if (id.equals("minecraft:jukebox_playable") && jukeboxPlayable != null
                    || id.equals("minecraft:provides_trim_material") && trimMaterial != null) {
                throw invalid("component '" + id + "' conflicts with its named property");
            }
            Object copied = jsonValue(entry.getValue(), Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            if (copied == null) throw invalid("component '" + id + "' cannot be null");
            replacement.put(id, copied);
        }
        components = Collections.unmodifiableMap(replacement);
    }

    public void jukeboxPlayable(String id) {
        writable();
        jukeboxPlayable = namedComponent(id, "minecraft:jukebox_playable");
    }

    public void trimMaterial(String id) {
        writable();
        trimMaterial = namedComponent(id, "minecraft:provides_trim_material");
    }

    private String namedComponent(String id, String component) {
        if (id != null && components.containsKey(component)) throw invalid("component '" + component + "' conflicts with its named property");
        return id == null ? null : resourceId(id);
    }

    public void parentModel(String id) {
        writable();
        parentModel = id == null ? null : resourceId(id);
    }

    public void texture(String slot, String id) {
        Map<String, String> replacement = new TreeMap<>(textures);
        replacement.put(slot, id);
        textures(replacement);
    }

    public void textures(Map<String, String> values) {
        writable();
        if (values == null) throw invalid("textures must be an object");
        Map<String, String> replacement = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String slot) || !slot.matches("[a-zA-Z0-9_]+")) {
                throw invalid("texture slots must be nonempty alphanumeric names");
            }
            if (!(entry.getValue() instanceof String texture)) throw invalid("texture values must be resource id strings");
            replacement.put(slot, resourceId(texture));
        }
        textures = Collections.unmodifiableMap(replacement);
    }

    public boolean hasAssets() { return parentModel != null || !textures.isEmpty(); }

    public ItemAssetDefinition assets(String itemId) {
        return hasAssets() ? new ItemAssetDefinition(itemId,
                parentModel == null ? "minecraft:item/generated" : parentModel,
                textures.isEmpty() ? Map.of("layer0", resourceId(itemId).replace(":", ":item/")) : textures) : null;
    }

    public void freeze() { frozen = true; }
    public void writable() {
        if (frozen) throw new IllegalStateException("[NEKO-4035] Item definition is frozen after collection");
    }

    public static String resourceId(String id) {
        if (id == null) throw invalid("resource id cannot be null");
        String normalized = id.contains(":") ? id : "minecraft:" + id;
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid("invalid resource id '" + id + "'");
        String path = normalized.substring(normalized.indexOf(':') + 1);
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw invalid("invalid resource path '" + id + "'");
        }
        return normalized;
    }

    /** Rejects executable/host values, cycles and excessive nesting before retaining any guest data. */
    private static Object jsonValue(Object input, Set<Object> seen, int depth) {
        if (depth > 64) throw invalid("component JSON exceeds 64 nesting levels");
        if (input != null && Value.asValue(input).canExecute()) throw invalid("component values cannot be functions");
        if (input instanceof Value value) {
            if (value.canExecute() || value.isHostObject()) throw invalid("component values must be JSON data");
            input = value.as(Object.class);
        }
        if (input instanceof JsonElement element) {
            input = new com.google.gson.Gson().fromJson(element, Object.class);
        }
        if (input == null || input instanceof String || input instanceof Boolean) return input;
        if (input instanceof Number number) {
            if (!Double.isFinite(number.doubleValue())) throw invalid("component numbers must be finite");
            return new java.math.BigDecimal(number.toString()).stripTrailingZeros();
        }
        if (!seen.add(input)) throw invalid("component JSON contains a cycle");
        try {
            if (input instanceof Map<?, ?> map) {
                Map<String, Object> copy = new TreeMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw invalid("component object keys must be strings");
                    copy.put(key, jsonValue(entry.getValue(), seen, depth + 1));
                }
                return Collections.unmodifiableMap(copy);
            }
            if (input instanceof List<?> list) {
                List<Object> copy = new ArrayList<>();
                for (Object element : list) copy.add(jsonValue(element, seen, depth + 1));
                return Collections.unmodifiableList(copy);
            }
            throw invalid("component values must be JSON objects, arrays or primitives");
        } finally {
            seen.remove(input);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("[NEKO-4034] Item configuration rejected: " + message);
    }
}
