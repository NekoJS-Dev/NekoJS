package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.core.posteffect.PostEffectChainJson;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side post-effect binding ({@code PostEffects}, feature 8b). Client scripts only.
 *
 * <p><b>v1 (this batch, no mixins):</b> {@link #set}/{@link #clear}/{@link #toggle} work with
 * any effect that exists as a resource — the vanilla presets {@code minecraft:invert},
 * {@code minecraft:spider}, {@code minecraft:creeper}, {@code minecraft:blur},
 * {@code minecraft:entity_outline}, {@code minecraft:transparency} (see {@link #PRESETS}) and
 * any {@code post_effect} JSON supplied by resource packs or other mods. {@link #register}
 * validates and stores runtime chains (including custom GLSL); activating runtime-only ids
 * additionally requires the pending {@code ShaderManagerMixin} and logs a warning until then.
 *
 * <pre>
 * // client_scripts
 * if (PostEffects.isAvailable('minecraft:invert')) {
 *   PostEffects.set('minecraft:invert');
 *   // ... later ...
 *   PostEffects.clear();
 * }
 * </pre>
 */
@Doc("Client-side full-screen post effects: set/clear/toggle resource-backed effects and register runtime chains.")
public final class PostEffectsJS implements Binding {

    /** Vanilla preset ids usable with {@link #set} on every supported version. */
    @Doc("Vanilla preset effect ids usable with set/toggle: minecraft:invert, minecraft:spider, minecraft:creeper, minecraft:blur, minecraft:entity_outline, minecraft:transparency.")
    public static final List<String> PRESETS = List.of(
            "minecraft:invert",
            "minecraft:spider",
            "minecraft:creeper",
            "minecraft:blur",
            "minecraft:entity_outline",
            "minecraft:transparency"
    );

    @Override
    public String name() {
        return "PostEffects";
    }

    @Override
    public Object value() {
        return this;
    }

    /** Script reload teardown: forget runtime-registered definitions (matching binding close semantics). */
    @Override
    public void close(ScriptType scriptType) {
        if (scriptType == ScriptType.CLIENT) {
            PostEffectManager.clearRegistered();
        }
    }

    /**
     * Registers a runtime post-effect chain. Without the pending {@code ShaderManagerMixin}
     * the definition is validated and stored but cannot be activated via {@link #set};
     * effects that exist as resources stay activatable.
     *
     * <p>Options (all optional except one shader reference):
     * <ul>
     *   <li>{@code chainJson} — full 26.x post-chain JSON ({@code assets/<ns>/post_effect/<path>.json} format);</li>
     *   <li>{@code fragmentShader} — GLSL source; a two-pass blit chain
     *       ({@code main -> swap -> main}) is generated around it;</li>
     *   <li>{@code fragmentShaderId} — shader id for the generated chain (default {@code <ns>:post/<path>});</li>
     *   <li>{@code uniformsJson} — JSON object literal merged into pass-1 uniforms;</li>
     *   <li>{@code blurRadius} / {@code blurRounds} — generate a vanilla box-blur chain instead.</li>
     * </ul>
     */
    @Doc("Registers a runtime post-effect chain from options { chainJson?, fragmentShader?, fragmentShaderId?, uniformsJson?, blurRadius?, blurRounds? }; returns true when the chain JSON is valid.")
    @Param(name = "id", value = "effect id, e.g. 'nekojs:my_invert'")
    @Param(name = "options", value = "{ chainJson?: string, fragmentShader?: string, fragmentShaderId?: string, uniformsJson?: string, blurRadius?: number, blurRounds?: number }")
    @Return("true when registered (runtime-only ids need the pending ShaderManager mixin to activate)")
    public boolean register(String id, Map<String, Object> options) {
        Identifier effectId = Identifier.tryParse(id);
        if (effectId == null) return false;
        Map<String, Object> opts = options == null ? Map.of() : options;

        String chainJson = asString(opts.get("chainJson"));
        Map<Identifier, String> fragmentShaders = PostEffectManager.parseShaderMap(asStringMap(opts.get("fragmentShaders")));
        Map<Identifier, String> vertexShaders = PostEffectManager.parseShaderMap(asStringMap(opts.get("vertexShaders")));

        if (chainJson == null) {
            String fragmentShader = asString(opts.get("fragmentShader"));
            Number blurRadius = asNumber(opts.get("blurRadius"));
            if (fragmentShader != null) {
                String shaderId = asString(opts.get("fragmentShaderId"));
                if (shaderId == null) {
                    shaderId = effectId.getNamespace() + ":post/" + effectId.getPath();
                }
                Identifier parsedShaderId = Identifier.tryParse(shaderId);
                if (parsedShaderId == null) {
                    return false;
                }
                fragmentShaders = new LinkedHashMap<>(fragmentShaders);
                fragmentShaders.put(parsedShaderId, fragmentShader);
                chainJson = PostEffectChainJson.simpleBlitChainModern(shaderId, asString(opts.get("uniformsJson")));
            } else if (blurRadius != null) {
                Number rounds = asNumber(opts.get("blurRounds"));
                chainJson = PostEffectChainJson.boxBlurChainModern(
                        blurRadius.doubleValue(), rounds == null ? 1 : rounds.intValue());
            } else {
                return false;
            }
        }

        return PostEffectManager.register(effectId, chainJson, fragmentShaders, vertexShaders);
    }

    /** Removes a runtime-registered definition; clears it first when active. */
    @Doc("Unregisters a runtime post-effect definition, clearing it first when it is active.")
    @Param(name = "id", value = "effect id")
    @Return("true when a definition was removed")
    public boolean unregister(String id) {
        Identifier effectId = Identifier.tryParse(id);
        return effectId != null && PostEffectManager.unregister(effectId);
    }

    /** Activates the effect on the client thread. Runtime-only ids warn and return false (v1). */
    @Doc("Activates a post effect (e.g. 'minecraft:invert'); executed on the client thread.")
    @Param(name = "id", value = "effect id; must exist as a post_effect resource in v1")
    @Return("true when accepted")
    public boolean set(String id) {
        Identifier effectId = Identifier.tryParse(id);
        return effectId != null && PostEffectManager.set(effectId);
    }

    /** Deactivates the active post effect. */
    @Doc("Clears the active post effect.")
    @Return("true")
    public boolean clear() {
        return PostEffectManager.clear();
    }

    /** Toggles: activates {@code id} when idle or different, clears when {@code id} is active. */
    @Doc("Toggles a post effect: set when idle/different, clear when it is already active.")
    @Param(name = "id", value = "effect id")
    @Return("true when accepted")
    public boolean toggle(String id) {
        Identifier effectId = Identifier.tryParse(id);
        return effectId != null && PostEffectManager.toggle(effectId);
    }

    /** Currently active effect id, or null. */
    @Doc("Returns the currently active effect id, or null when none is active.")
    @Return("effect id string or null")
    @Nullable
    public String current() {
        Identifier current = PostEffectManager.current();
        return current == null ? null : current.toString();
    }

    /** Whether an effect is currently applied (reads GameRenderer#effectActive reflectively). */
    @Doc("Returns whether a post effect is currently applied.")
    @Return("true when GameRenderer.effectActive is set")
    public boolean isActive() {
        return PostEffectManager.isActive();
    }

    /** Whether a runtime definition exists for the id. */
    @Doc("Returns whether a runtime-registered definition exists for the id.")
    @Param(name = "id", value = "effect id")
    @Return("true when registered through PostEffects.register")
    public boolean has(String id) {
        Identifier effectId = Identifier.tryParse(id);
        return effectId != null && PostEffectManager.hasDefinition(effectId);
    }

    /** Whether the effect exists as a post_effect resource (activatable in v1). */
    @Doc("Returns whether the effect exists as a post_effect resource in the active resource packs (activatable without mixins).")
    @Param(name = "id", value = "effect id")
    @Return("true when resource-backed")
    public boolean isAvailable(String id) {
        Identifier effectId = Identifier.tryParse(id);
        return effectId != null && PostEffectManager.isResourceAvailable(effectId);
    }

    /** Vanilla preset ids usable with set/toggle. */
    @Doc("Lists vanilla preset ids usable with set/toggle.")
    @Return("preset id list")
    public List<String> presets() {
        return new ArrayList<>(PRESETS);
    }

    @Nullable
    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    @Nullable
    private static Number asNumber(Object value) {
        return value instanceof Number n ? n : null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, String> asStringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                    result.put(k, v);
                }
            }
            return result;
        }
        return null;
    }
}
