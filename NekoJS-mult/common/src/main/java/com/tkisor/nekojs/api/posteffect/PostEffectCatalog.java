package com.tkisor.nekojs.api.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable result of one complete CLIENT post-effect declaration pass.
 *
 * <p>The map preserves declaration order for deterministic adapter reconciliation. The optional
 * desired active id is declaration state, not a renderer command; a lifecycle owner may apply it
 * after publishing this catalog.
 */
@Doc("Immutable catalog produced by a complete ClientEvents.postEffects declaration pass.")
public record PostEffectCatalog(
        Map<String, PostEffectDefinition> definitions,
        @Nullable String desiredActiveId
) {
    public PostEffectCatalog {
        Map<String, PostEffectDefinition> copy = new LinkedHashMap<>();
        String definitionActiveId = null;
        for (var entry : (definitions == null ? Map.<String, PostEffectDefinition>of() : definitions).entrySet()) {
            String key = PostEffectDefinition.normalizeId(entry.getKey());
            PostEffectDefinition definition = Objects.requireNonNull(entry.getValue(), "definition");
            if (!key.equals(definition.id())) {
                throw new IllegalArgumentException(
                        "Catalog key does not match post-effect definition id: " + key + " != " + definition.id());
            }
            if (copy.put(key, definition) != null) {
                throw new IllegalArgumentException("Duplicate post-effect definition: " + key);
            }
            if (definition.desiredActive()) {
                if (definitionActiveId != null && !definitionActiveId.equals(key)) {
                    throw new IllegalArgumentException(
                            "More than one post-effect is marked desired active: " + definitionActiveId + ", " + key);
                }
                definitionActiveId = key;
            }
        }

        String selected = desiredActiveId == null ? null : PostEffectDefinition.normalizeId(desiredActiveId);
        if (selected != null && !copy.containsKey(selected)) {
            throw new IllegalArgumentException("Desired active post effect is not defined: " + selected);
        }
        if (definitionActiveId != null && selected != null && !definitionActiveId.equals(selected)) {
            throw new IllegalArgumentException(
                    "Conflicting desired active post effects: " + definitionActiveId + ", " + selected);
        }
        if (selected == null) {
            selected = definitionActiveId;
        }

        definitions = Collections.unmodifiableMap(copy);
        desiredActiveId = selected;
    }

    public static PostEffectCatalog empty() {
        return new PostEffectCatalog(Map.of(), null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns the definition for a canonical id, or null when absent/invalid. */
    @Nullable
    public PostEffectDefinition definition(String id) {
        if (id == null) return null;
        try {
            return definitions.get(PostEffectDefinition.normalizeId(id));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean contains(String id) {
        return definition(id) != null;
    }

    public int size() {
        return definitions.size();
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(definitions.keySet()));
    }

    public List<PostEffectDefinition> values() {
        return List.copyOf(definitions.values());
    }

    @Nullable
    public PostEffectDefinition desiredActive() {
        return desiredActiveId == null ? null : definitions.get(desiredActiveId);
    }

    /** Mutable declaration accumulator used only during one event dispatch. */
    public static final class Builder {
        private final Map<String, PostEffectDefinition> definitions = new LinkedHashMap<>();
        private String desiredActiveId;
        private boolean sealed;

        private Builder() {
        }

        public Builder define(PostEffectDefinition definition) {
            checkOpen();
            Objects.requireNonNull(definition, "definition");
            String id = definition.id();
            if (definitions.containsKey(id)) {
                throw new IllegalArgumentException("Conflicting post-effect definitions for id: " + id);
            }
            if (definition.desiredActive()) {
                selectDesiredActive(id);
            }
            definitions.put(id, definition);
            return this;
        }

        public Builder resource(String id) {
            return define(PostEffectDefinition.resource(id));
        }

        public Builder runtime(String id, String chainJson) {
            return define(PostEffectDefinition.runtime(id, chainJson));
        }

        public Builder runtime(
                String id,
                String chainJson,
                Map<String, String> fragmentShaders,
                Map<String, String> vertexShaders
        ) {
            return define(PostEffectDefinition.runtime(id, chainJson, fragmentShaders, vertexShaders));
        }

        /** Marks one already declared definition as the desired initial active effect. */
        public Builder desiredActive(String id) {
            checkOpen();
            String normalized = PostEffectDefinition.normalizeId(id);
            if (!definitions.containsKey(normalized)) {
                throw new IllegalArgumentException("Desired active post effect is not defined: " + normalized);
            }
            selectDesiredActive(normalized);
            definitions.put(normalized, definitions.get(normalized).withDesiredActive(true));
            return this;
        }

        /** Removes the desired initial active state without removing definitions. */
        public Builder clearDesiredActive() {
            checkOpen();
            desiredActiveId = null;
            for (var entry : new ArrayList<>(definitions.entrySet())) {
                if (entry.getValue().desiredActive()) {
                    definitions.put(entry.getKey(), entry.getValue().withDesiredActive(false));
                }
            }
            return this;
        }

        /** Returns an immutable copy without sealing this accumulator. */
        public PostEffectCatalog snapshot() {
            return new PostEffectCatalog(definitions, desiredActiveId);
        }

        /** Seals this accumulator and returns its immutable catalog. */
        public PostEffectCatalog build() {
            checkOpen();
            sealed = true;
            return new PostEffectCatalog(definitions, desiredActiveId);
        }

        public boolean isSealed() {
            return sealed;
        }

        private void selectDesiredActive(String id) {
            if (desiredActiveId != null && !desiredActiveId.equals(id)) {
                throw new IllegalArgumentException(
                        "Conflicting desired active post effects: " + desiredActiveId + ", " + id);
            }
            desiredActiveId = id;
        }

        private void checkOpen() {
            if (sealed) {
                throw new IllegalStateException("Post-effect declaration accumulator is sealed");
            }
        }
    }
}
