package com.tkisor.nekojs.core.posteffect;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One post-effect declaration collected from a client script during the declaration
 * lifecycle ({@code ClientEvents.postEffects}); Minecraft-free by construction so the
 * candidate plan can live in {@code common}.
 *
 * <p>A declaration is either an <b>install</b> (the effect id plus its chain JSON and
 * optional runtime GLSL sources) or a <b>retire</b> ({@code remove = true}): the id was
 * declared by a previous generation and the current generation no longer declares it.
 * Nothing here touches the live client renderer — the platform Adapter turns the
 * collected list into the active generation at the commit point.
 *
 * @param kind             declaration kind
 * @param id               effect id ({@code namespace:path})
 * @param chainJson        post-chain JSON for {@link Kind#INSTALL}; null for RETIRE
 * @param fragmentShaders  shader id to GLSL source (fragment stage)
 * @param vertexShaders    shader id to GLSL source (vertex stage)
 */
public record PostEffectDeclaration(Kind kind, String id, String chainJson,
                                    Map<String, String> fragmentShaders,
                                    Map<String, String> vertexShaders) {

    /** Declaration kind: install a definition or retire one that is no longer declared. */
    public enum Kind {
        INSTALL,
        RETIRE
    }

    public PostEffectDeclaration {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("post-effect declaration id must not be blank");
        }
        fragmentShaders = fragmentShaders == null ? Map.of() : Map.copyOf(fragmentShaders);
        vertexShaders = vertexShaders == null ? Map.of() : Map.copyOf(vertexShaders);
    }

    /** Install declaration. */
    public static PostEffectDeclaration install(String id, String chainJson,
                                                Map<String, String> fragmentShaders,
                                                Map<String, String> vertexShaders) {
        return new PostEffectDeclaration(Kind.INSTALL, id, chainJson, fragmentShaders, vertexShaders);
    }

    /** Retire declaration (the id is no longer declared by the current generation). */
    public static PostEffectDeclaration retire(String id) {
        return new PostEffectDeclaration(Kind.RETIRE, id, null, Map.of(), Map.of());
    }

    /**
     * Canonical text used for the plan fingerprint and for parity assertions: shader maps
     * are sorted by id, so two declarations that differ only in map iteration order are
     * the same declaration. The algorithm is intentionally a diagnostic/parity format,
     * not a persisted one.
     */
    public String canonicalForm() {
        StringBuilder sb = new StringBuilder("post-effect[v1]:").append(kind).append('|').append(id);
        if (chainJson != null) {
            sb.append("|json=").append(chainJson);
        }
        // TreeMap: same shader set in a different iteration order is the same declaration
        new TreeMap<>(fragmentShaders).forEach((key, value) ->
                sb.append("|f:").append(key).append('=').append(value));
        new TreeMap<>(vertexShaders).forEach((key, value) ->
                sb.append("|v:").append(key).append('=').append(value));
        return sb.toString();
    }
}
