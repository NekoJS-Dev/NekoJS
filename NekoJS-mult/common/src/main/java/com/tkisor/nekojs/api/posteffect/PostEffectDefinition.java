package com.tkisor.nekojs.api.posteffect;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.annotation.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, loader-neutral declaration of one client post effect.
 *
 * <p>Resource definitions identify a post-effect asset that the active resource packs provide.
 * Runtime definitions carry validated chain JSON and optional shader source tables. Native codec
 * validation remains an adapter concern because the supported Minecraft versions use different
 * post-chain formats; JSON syntax and the script-facing shape are validated here before a
 * definition enters a catalog.
 */
@Doc("Immutable post-effect definition collected by ClientEvents.postEffects.")
public record PostEffectDefinition(
        String id,
        Form form,
        @Nullable String chainJson,
        Map<String, String> fragmentShaders,
        Map<String, String> vertexShaders,
        boolean desiredActive
) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    /** The two declaration forms supported by the neutral catalog. */
    public enum Form {
        RESOURCE,
        RUNTIME
    }

    public PostEffectDefinition {
        id = normalizeId(id);
        form = Objects.requireNonNull(form, "form");
        fragmentShaders = immutableSources(fragmentShaders, "fragmentShaders");
        vertexShaders = immutableSources(vertexShaders, "vertexShaders");

        if (form == Form.RESOURCE) {
            if (chainJson != null) {
                throw new IllegalArgumentException("Resource-backed post effect must not carry chain JSON: " + id);
            }
            if (!fragmentShaders.isEmpty() || !vertexShaders.isEmpty()) {
                throw new IllegalArgumentException("Resource-backed post effect must not carry runtime shader sources: " + id);
            }
        } else {
            chainJson = validateChainJson(chainJson, id);
        }
    }

    /** Creates a resource-backed definition with no desired initial activation. */
    public static PostEffectDefinition resource(String id) {
        return resource(id, false);
    }

    /** Creates a resource-backed definition and optionally marks it as desired initially active. */
    public static PostEffectDefinition resource(String id, boolean desiredActive) {
        return new PostEffectDefinition(id, Form.RESOURCE, null, Map.of(), Map.of(), desiredActive);
    }

    /** Creates a runtime definition with validated chain JSON and no shader source overrides. */
    public static PostEffectDefinition runtime(String id, String chainJson) {
        return runtime(id, chainJson, Map.of(), Map.of(), false);
    }

    /** Creates a runtime definition with validated chain JSON and a desired initial state. */
    public static PostEffectDefinition runtime(String id, String chainJson, boolean desiredActive) {
        return runtime(id, chainJson, Map.of(), Map.of(), desiredActive);
    }

    /** Creates a runtime definition with optional fragment and vertex shader source tables. */
    public static PostEffectDefinition runtime(
            String id,
            String chainJson,
            Map<String, String> fragmentShaders,
            Map<String, String> vertexShaders
    ) {
        return runtime(id, chainJson, fragmentShaders, vertexShaders, false);
    }

    /** Creates a fully specified runtime definition. */
    public static PostEffectDefinition runtime(
            String id,
            String chainJson,
            Map<String, String> fragmentShaders,
            Map<String, String> vertexShaders,
            boolean desiredActive
    ) {
        return new PostEffectDefinition(
                id, Form.RUNTIME, chainJson, fragmentShaders, vertexShaders, desiredActive);
    }

    /** Returns a copy with the declared desired initial active state changed. */
    public PostEffectDefinition withDesiredActive(boolean value) {
        return new PostEffectDefinition(id, form, chainJson, fragmentShaders, vertexShaders, value);
    }

    /** Alias useful to adapter code that describes the form as a kind. */
    public Form kind() {
        return form;
    }

    public boolean isResourceBacked() {
        return form == Form.RESOURCE;
    }

    public boolean isRuntime() {
        return form == Form.RUNTIME;
    }

    /** Canonical id validation shared by event declarations and controller requests. */
    public static String normalizeId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Post-effect id must not be null");
        }
        String id = value.trim();
        if (!id.equals(value) || id.isEmpty()) {
            throw new IllegalArgumentException("Post-effect id must be a non-blank canonical id: " + value);
        }
        int separator = id.indexOf(':');
        if (separator <= 0 || separator != id.lastIndexOf(':') || separator == id.length() - 1) {
            throw new IllegalArgumentException(
                    "Post-effect id must use the canonical namespace:path form: " + value);
        }
        String namespace = id.substring(0, separator);
        String path = id.substring(separator + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()
                || path.startsWith("/") || path.endsWith("/") || path.contains("//")
                || path.contains("..")) {
            throw new IllegalArgumentException("Invalid post-effect id: " + value);
        }
        return id;
    }

    private static Map<String, String> immutableSources(Map<String, String> sources, String name) {
        if (sources == null || sources.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (var entry : sources.entrySet()) {
            String shaderId = normalizeId(entry.getKey());
            String source = entry.getValue();
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException(name + " source must not be blank: " + shaderId);
            }
            if (copy.put(shaderId, source) != null) {
                throw new IllegalArgumentException("Duplicate " + name + " id: " + shaderId);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String validateChainJson(@Nullable String chainJson, String id) {
        if (chainJson == null || chainJson.isBlank()) {
            throw new IllegalArgumentException("Runtime post effect requires non-blank chain JSON: " + id);
        }
        try {
            JsonElement parsed = JsonParser.parseString(chainJson);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Runtime post-effect chain must be a JSON object: " + id);
            }
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid runtime post-effect chain JSON: " + id, e);
        }
        return chainJson;
    }
}
