package com.tkisor.nekojs.api.stage.gate;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discriminator for declarative stage gate categories supported by {@code StageEvents.gates}.
 */
@Doc("Discriminator for declarative stage gate categories supported by StageEvents.gates.")
public enum StageGateKind {
    ITEM,
    BLOCK,
    RECIPE,
    CONTAINER,
    DIMENSION,
    REGION,
    ENTITY,
    EFFECT,
    ENCHANT,
    LOOT;

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    static NekoId requireRuleId(NekoId id) {
        if (id == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate id cannot be null");
        }
        return id;
    }

    static String requireStage(String stage) {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate stage name cannot be null or blank");
        }
        return stage;
    }

    static StageScope requireScope(StageScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate scope cannot be null");
        }
        return scope;
    }

    static StageScope parseScope(String scope) {
        return StageScope.fromString(scope);
    }

    static String normalizeText(String text) {
        return text == null ? "" : text;
    }

    static void addIds(Set<NekoId> target, NekoId[] ids, String label) {
        if (ids == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate " + label + " array cannot be null");
        }
        for (NekoId item : ids) {
            if (item == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate " + label + " entry cannot be null");
            }
            target.add(item);
        }
    }

    static void addTagIds(Set<NekoId> target, NekoId[] tags) {
        if (tags == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate tags array cannot be null");
        }
        for (NekoId tag : tags) {
            if (tag == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate tag entry cannot be null");
            }
            target.add(normalizeTagId(tag));
        }
    }

    static NekoId normalizeTagId(NekoId tag) {
        if (tag.namespace().startsWith("#")) {
            String ns = tag.namespace().substring(1);
            if (ns.isBlank()) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate tag namespace cannot be blank: " + tag);
            }
            return NekoId.of(ns, tag.path());
        }
        if (NekoId.DEFAULT_NAMESPACE.equals(tag.namespace()) && tag.path().startsWith("#")) {
            String path = tag.path().substring(1);
            if (path.isBlank()) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate tag path cannot be blank: " + tag);
            }
            return NekoId.of(tag.namespace(), path);
        }
        return tag;
    }

    static void addNamespaces(Set<String> target, String[] modIds) {
        if (modIds == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate namespaces array cannot be null");
        }
        for (String modId : modIds) {
            validateNamespace(modId);
            target.add(modId);
        }
    }

    static void validateNamespace(String modId) {
        if (modId == null || modId.isBlank() || !NAMESPACE_PATTERN.matcher(modId).matches()) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Stage gate namespace must match [a-z0-9_.-]+, got: " + modId);
        }
    }

    static Set<NekoId> immutableIdSet(Set<NekoId> values, String label) {
        if (values == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate " + label + " set cannot be null");
        }
        LinkedHashSet<NekoId> copy = new LinkedHashSet<>(values.size());
        for (NekoId value : values) {
            if (value == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate " + label + " entry cannot be null");
            }
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }

    static Set<NekoId> immutableTagSet(Set<NekoId> values) {
        if (values == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate tags set cannot be null");
        }
        LinkedHashSet<NekoId> copy = new LinkedHashSet<>(values.size());
        for (NekoId value : values) {
            if (value == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate tag entry cannot be null");
            }
            copy.add(normalizeTagId(value));
        }
        return Collections.unmodifiableSet(copy);
    }

    static Set<String> immutableNamespaceSet(Set<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate namespaces set cannot be null");
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>(values.size());
        for (String value : values) {
            validateNamespace(value);
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }

    static void requireTarget(NekoId id, boolean hasTarget) {
        if (!hasTarget) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Stage gate '" + id + "' must declare at least one target");
        }
    }
}
