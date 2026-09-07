package com.tkisor.nekojs.wrapper.event.client;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.posteffect.PostEffectCatalog;
import com.tkisor.nekojs.api.posteffect.PostEffectDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declaration payload for {@code ClientEvents.postEffects}.
 *
 * <p>Each full CLIENT script reload must use a fresh payload. The lifecycle owner posts it after
 * script contexts are committed, reads {@link #catalog()}, and passes that immutable product to a
 * platform renderer adapter. This class deliberately contains no renderer or activation command.
 */
@Doc("Collects resource-backed and runtime post-effect definitions during a full CLIENT reload.")
public final class PostEffectEventJS {
    private final PostEffectCatalog.Builder declarations = PostEffectCatalog.builder();

    /** Adds a fully validated definition to this declaration pass. */
    @Doc("Adds one validated post-effect definition; duplicate ids are contribution errors.")
    @Param(name = "definition", value = "a PostEffectDefinition created by resource(...) or runtime(...)")
    public void define(PostEffectDefinition definition) {
        declarations.define(definition);
    }

    /** Declares a resource-backed post effect. */
    @Doc("Declares a resource-backed post effect by canonical id, such as 'minecraft:invert'.")
    @Param(name = "id", value = "canonical post-effect id")
    public void resource(String id) {
        declarations.resource(id);
    }

    /** Declares a resource-backed post effect with a desired initial active state. */
    @Doc("Declares a resource-backed post effect and optionally marks it as initially active.")
    @Param(name = "id", value = "canonical post-effect id")
    @Param(name = "desiredActive", value = "whether this is the desired initial active effect")
    public void resource(String id, boolean desiredActive) {
        define(PostEffectDefinition.resource(id, desiredActive));
    }

    /** Declares a resource-backed post effect using an options object. */
    @Doc("Declares a resource-backed post effect from options { desiredActive?: boolean }.")
    @Param(name = "id", value = "canonical post-effect id")
    @Param(name = "options", value = "{ desiredActive?: boolean }")
    public void resource(String id, Map<String, Object> options) {
        define(PostEffectDefinition.resource(id, desiredActive(options)));
    }

    /** Declares a runtime post effect from chain JSON. */
    @Doc("Declares a runtime post effect from validated JSON; renderer capability is checked at activation time.")
    @Param(name = "id", value = "canonical post-effect id")
    @Param(name = "chainJson", value = "post-chain JSON object")
    public void runtime(String id, String chainJson) {
        declarations.runtime(id, chainJson);
    }

    /** Declares a runtime post effect and a desired initial active state. */
    @Doc("Declares a runtime post effect and optionally marks it as initially active.")
    @Param(name = "id", value = "canonical post-effect id")
    @Param(name = "chainJson", value = "post-chain JSON object")
    @Param(name = "desiredActive", value = "whether this is the desired initial active effect")
    public void runtime(String id, String chainJson, boolean desiredActive) {
        define(PostEffectDefinition.runtime(id, chainJson, desiredActive));
    }

    /** Declares a runtime post effect with optional shader source tables. */
    @Doc("Declares a runtime post effect with optional fragmentShaders and vertexShaders source maps.")
    @Param(name = "id", value = "canonical post-effect id")
    @Param(name = "chainJson", value = "post-chain JSON object")
    @Param(name = "fragmentShaders", value = "shader id to GLSL source map")
    @Param(name = "vertexShaders", value = "shader id to GLSL source map")
    public void runtime(
            String id,
            String chainJson,
            Map<String, String> fragmentShaders,
            Map<String, String> vertexShaders
    ) {
        declarations.runtime(id, chainJson, fragmentShaders, vertexShaders);
    }

    /** Fully specified runtime declaration, including desired active state. */
    public void runtime(
            String id,
            String chainJson,
            Map<String, String> fragmentShaders,
            Map<String, String> vertexShaders,
            boolean desiredActive
    ) {
        define(PostEffectDefinition.runtime(id, chainJson, fragmentShaders, vertexShaders, desiredActive));
    }

    /** Runtime declaration using the script-friendly options shape. */
    @Doc("Declares a runtime post effect from options { chainJson, fragmentShaders?, vertexShaders?, desiredActive? }.")
    @Param(name = "id", value = "canonical post-effect id")
    @Param(name = "options", value = "{ chainJson: string, fragmentShaders?: object, vertexShaders?: object, desiredActive?: boolean }")
    public void runtime(String id, Map<String, Object> options) {
        if (options == null) {
            throw new IllegalArgumentException("Runtime post-effect options must not be null");
        }
        Object chainJson = options.get("chainJson");
        if (!(chainJson instanceof String json)) {
            throw new IllegalArgumentException("Runtime post-effect options require string chainJson");
        }
        define(PostEffectDefinition.runtime(
                id,
                json,
                sourceMap(options.get("fragmentShaders")),
                sourceMap(options.get("vertexShaders")),
                desiredActive(options)));
    }

    /** Marks one previously declared definition as the desired initial active effect. */
    @Doc("Selects one already declared effect as the desired initial active effect.")
    @Param(name = "id", value = "canonical id of a definition in this event")
    public void desiredActive(String id) {
        declarations.desiredActive(id);
    }

    /** Clears the desired initial active selection while retaining definitions. */
    @Doc("Removes the desired initial active selection from this declaration pass.")
    public void clearDesiredActive() {
        declarations.clearDesiredActive();
    }

    /** Seals this pass and returns its immutable catalog product. */
    @Doc("Returns the immutable catalog after this declaration pass; no further declarations are allowed.")
    public PostEffectCatalog catalog() {
        return declarations.build();
    }

    /** Returns a defensive catalog copy without sealing this event payload. */
    public PostEffectCatalog snapshot() {
        return declarations.snapshot();
    }

    public boolean isSealed() {
        return declarations.isSealed();
    }

    private static boolean desiredActive(Map<String, Object> options) {
        if (options == null) return false;
        Object value = options.containsKey("desiredActive")
                ? options.get("desiredActive")
                : options.get("active");
        if (value == null && !options.containsKey("desiredActive") && !options.containsKey("active")) {
            return false;
        }
        if (!(value instanceof Boolean b)) {
            throw new IllegalArgumentException("desiredActive must be a boolean");
        }
        return b;
    }

    private static Map<String, String> sourceMap(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Shader source table must be an object");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String source)) {
                throw new IllegalArgumentException("Shader source tables require string ids and sources");
            }
            result.put(key, source);
        }
        return result;
    }
}
