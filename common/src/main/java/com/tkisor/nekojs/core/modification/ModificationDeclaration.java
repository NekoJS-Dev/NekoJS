package com.tkisor.nekojs.core.modification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single item or block property modification declaration. Produced by script callbacks during
 * candidate collection and normalized by platform adapters into <b>pure JVM</b> data. Targets are
 * identified by domain kind and normalized ID. Property values only permit String, Number, Boolean,
 * null, and nested Maps (with String keys and recursively matching values), carrying no Minecraft
 * or loader types. Applying modifications to Minecraft components or attributes occurs only at commit
 * through platform/version adapters.
 *
 * <p>Declarations replay in the order they appear in the plan: a later declaration for the same target
 * completely replaces earlier declarations (applying over the baseline without property merging),
 * without rejecting duplicate keys.
 *
 * <p>Immutable; {@link #properties()} returns an unmodifiable view.
 */
public final class ModificationDeclaration {

    /** Domain kind agreed upon with platform adapters: {@code "item"} or {@code "block"}. */
    private final String kind;
    /** Normalized target ID ({@code namespace:path}, with default namespace filled in). */
    private final String targetId;
    /** Normalized property values (preserves insertion order; later writes overwrite earlier ones for the same property). */
    private final Map<String, Object> properties;
    /** ID of the script where the declaration originated (used for diagnostics and failure attribution; can be null). */
    private final String scriptId;

    public ModificationDeclaration(String kind, String targetId, Map<String, Object> properties, String scriptId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        Map<String, Object> copy = new LinkedHashMap<>();
        if (properties != null) {
            properties.forEach((name, value) -> copy.put(
                    Objects.requireNonNull(name, "property name"),
                    requirePlainValue(value, name)));
        }
        this.properties = Collections.unmodifiableMap(copy);
        this.scriptId = scriptId;
    }

    public String kind() {
        return kind;
    }

    public String targetId() {
        return targetId;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public String scriptId() {
        return scriptId;
    }

    /** Restricts values to plain JVM types produced by adapter normalization; rejects Minecraft objects in common. */
    private static Object requirePlainValue(Object value, String name) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(
                            "property '" + name + "' map key must be a String: " + entry.getKey());
                }
                copy.put(key, requirePlainValue(entry.getValue(), name + "." + key));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException(
                "property '" + name + "' has a non-plain value type " + value.getClass().getName()
                        + "; normalized declarations must be String/Number/Boolean/null/Map");
    }

    @Override
    public String toString() {
        return kind + "(" + targetId + properties + ")"
                + (scriptId == null ? "" : " from " + scriptId);
    }
}
