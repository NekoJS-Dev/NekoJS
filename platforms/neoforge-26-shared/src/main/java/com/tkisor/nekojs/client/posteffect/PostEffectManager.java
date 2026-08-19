package com.tkisor.nekojs.client.posteffect;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime client post-effect registry (feature 8b, client-side only; ported from Katton's
 * {@code ClientPostEffectManager}).
 *
 * <p><b>v1 split (no mixins in this batch):</b> vanilla's {@code ShaderManager} only knows
 * post chains declared by resource packs ({@code assets/<ns>/post_effect/<path>.json}) at
 * resource-reload time, so definitions registered here at runtime <i>cannot be activated
 * yet</i>. v1 therefore ships:
 * <ul>
 *   <li>full set/clear/toggle/current for any effect that exists as a resource — the six
 *       vanilla presets ({@code minecraft:invert}, {@code minecraft:spider},
 *       {@code minecraft:creeper}, {@code minecraft:blur}, {@code minecraft:entity_outline},
 *       {@code minecraft:transparency}) plus anything provided by resource packs/mods;</li>
 *   <li>{@link #register} which parses + validates chain JSON via {@code PostChainConfig.CODEC}
 *       and stores the definition (custom GLSL sources included) so the planned
 *       {@code ShaderManagerMixin} can activate it later without script changes.</li>
 * </ul>
 *
 * <p>All renderer mutations run on the client thread via {@code Minecraft#execute}.
 */
public final class PostEffectManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 26.x post-chain resources live at {@code assets/<ns>/post_effect/<path>.json}. */
    private static final FileToIdConverter POST_EFFECT_FILES = FileToIdConverter.json("post_effect");

    private static final Map<Identifier, Definition> DEFINITIONS = new ConcurrentHashMap<>();

    private PostEffectManager() {
    }

    /** A runtime-registered effect: validated chain config plus optional custom GLSL sources. */
    record Definition(PostChainConfig config, String chainJson,
                      Map<Identifier, String> fragmentShaders, Map<Identifier, String> vertexShaders) {
    }

    // ---- registration (stored for the future ShaderManagerMixin; not activatable in v1) ----

    public static boolean register(Identifier id, String chainJson,
                                   Map<Identifier, String> fragmentShaders,
                                   Map<Identifier, String> vertexShaders) {
        PostChainConfig config = parseChainConfig(id, chainJson);
        if (config == null) return false;
        DEFINITIONS.put(id, new Definition(config, chainJson,
                Map.copyOf(fragmentShaders), Map.copyOf(vertexShaders)));
        LOGGER.info("Registered runtime client post effect {} (activation requires the pending ShaderManager mixin)", id);
        return true;
    }

    public static boolean unregister(Identifier id) {
        boolean removed = DEFINITIONS.remove(id) != null;
        if (removed && id.equals(current())) {
            clear();
        }
        return removed;
    }

    /** Clears every runtime-registered definition (script reload teardown). */
    public static void clearRegistered() {
        Identifier current = current();
        DEFINITIONS.clear();
        if (current != null) {
            clear();
        }
    }

    public static boolean hasDefinition(Identifier id) {
        return DEFINITIONS.containsKey(id);
    }

    // ---- activation (vanilla-resource-backed effects; client-thread execution) ----

    /**
     * Activates the post effect {@code id}. Returns {@code false} when the id is only known
     * as a runtime definition (needs the pending mixin) or the id cannot be found.
     */
    public static boolean set(Identifier id) {
        if (isRuntimeOnly(id)) {
            LOGGER.warn("Post effect {} was registered at runtime and cannot be activated until the ShaderManager mixin lands; use a resource-pack effect id instead", id);
            return false;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.setPostEffect(id));
        return true;
    }

    /** Deactivates any active post effect. */
    public static boolean clear() {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.clearPostEffect());
        return true;
    }

    /**
     * Toggles between {@code id} and no effect: activates when idle/different, clears when
     * {@code id} is currently active.
     */
    public static boolean toggle(Identifier id) {
        if (id.equals(current())) {
            return clear();
        }
        return set(id);
    }

    /** Currently active effect id ({@code GameRenderer#currentPostEffect()}), or null. */
    @Nullable
    public static Identifier current() {
        return Minecraft.getInstance().gameRenderer.currentPostEffect();
    }

    /**
     * Whether an effect is currently applied. {@code effectActive} is a private
     * {@code GameRenderer} field (no public getter), read via reflection.
     */
    public static boolean isActive() {
        GameRendererFields fields = GameRendererFields.read();
        return fields != null && fields.effectActive();
    }

    /** True when the effect exists as a {@code post_effect} resource in the active packs. */
    public static boolean isResourceAvailable(Identifier id) {
        return Minecraft.getInstance().getResourceManager().getResource(POST_EFFECT_FILES.idToFile(id)).isPresent();
    }

    static boolean isRuntimeOnly(Identifier id) {
        return !isResourceAvailable(id) && DEFINITIONS.containsKey(id);
    }

    // ---- shader source serving (called by the pending ShaderManagerMixin) ----

    /**
     * Runtime GLSL source for a shader id, registered through {@code PostEffects.register}.
     * The pending mixin injects at {@code ShaderManager#getShader} HEAD and returns this when
     * non-null — until then the method is public but unused (vanilla-only shaders work).
     */
    @Nullable
    public static String getRuntimeShaderSource(Identifier id, ShaderType type) {
        for (Definition definition : DEFINITIONS.values()) {
            Map<Identifier, String> sources = switch (type) {
                case FRAGMENT -> definition.fragmentShaders();
                case VERTEX -> definition.vertexShaders();
            };
            String source = sources.get(id);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    @Nullable
    private static PostChainConfig parseChainConfig(Identifier id, String chainJson) {
        try {
            var json = JsonParser.parseString(chainJson);
            return PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(IllegalArgumentException::new);
        } catch (JsonParseException | IllegalArgumentException e) {
            NekoJS.LOGGER.warn("Failed to parse runtime client post effect {}", id, e);
            return null;
        }
    }

    /** Parses and normalizes a {@code Map<String, String>} shader-source table from JS. */
    static Map<Identifier, String> parseShaderMap(Map<String, String> raw) {
        Map<Identifier, String> parsed = new LinkedHashMap<>();
        if (raw == null) return parsed;
        for (var entry : raw.entrySet()) {
            Identifier shaderId = Identifier.tryParse(entry.getKey());
            if (shaderId != null && entry.getValue() != null) {
                parsed.put(shaderId, entry.getValue());
            }
        }
        return parsed;
    }

    // ---- post chain cache (pre-wired for the pending ShaderManagerMixin) ----

    private record CacheKey(Identifier id, Set<Identifier> allowedTargets) {
    }

    private static final Map<CacheKey, PostChain> POST_CHAIN_CACHE = new ConcurrentHashMap<>();

    /**
     * Loads (and caches) the runtime {@link PostChain} for a registered definition. The pending
     * mixin injects at {@code ShaderManager#getPostChain} HEAD and returns this when non-null.
     */
    @Nullable
    public static PostChain getOrCreatePostChain(Identifier id, Set<Identifier> allowedTargets,
                                                 TextureManager textureManager,
                                                 Projection projection,
                                                 ProjectionMatrixBuffer projectionMatrixBuffer) {
        Definition definition = DEFINITIONS.get(id);
        if (definition == null) return null;
        CacheKey key = new CacheKey(id, new LinkedHashSet<>(allowedTargets));
        PostChain cached = POST_CHAIN_CACHE.get(key);
        if (cached != null) return cached;

        PostChain chain;
        try {
            chain = PostChain.load(definition.config(), textureManager, key.allowedTargets(),
                    id, projection, projectionMatrixBuffer);
        } catch (Exception e) {
            LOGGER.warn("Failed to load runtime client post effect {}", id, e);
            return null;
        }
        PostChain winner = POST_CHAIN_CACHE.putIfAbsent(key, chain);
        if (winner != null) {
            chain.close();
            return winner;
        }
        return chain;
    }

    /** Drops cached runtime chains (resource reload / shutdown — the pending mixin calls this). */
    public static void invalidatePostChainCache() {
        for (PostChain chain : POST_CHAIN_CACHE.values()) {
            try {
                chain.close();
            } catch (Exception ignored) {
            }
        }
        POST_CHAIN_CACHE.clear();
    }

    /**
     * Reflection view over the private {@code GameRenderer} post-effect fields. On 26.x both
     * {@code postEffectId} and {@code effectActive} exist but only the pair of public
     * accessors {@code currentPostEffect()}/{@code togglePostEffect()} expose them, so
     * {@code effectActive} is read reflectively (no AT/mixin needed for a plain read).
     */
    private record GameRendererFields(boolean effectActive) {
        @Nullable
        static GameRendererFields read() {
            Object gameRenderer = Minecraft.getInstance().gameRenderer;
            try {
                Field field = gameRenderer.getClass().getDeclaredField("effectActive");
                field.setAccessible(true);
                return new GameRendererFields((Boolean) field.get(gameRenderer));
            } catch (ReflectiveOperationException e) {
                LOGGER.debug("Could not read GameRenderer.effectActive", e);
                return null;
            }
        }
    }
}
