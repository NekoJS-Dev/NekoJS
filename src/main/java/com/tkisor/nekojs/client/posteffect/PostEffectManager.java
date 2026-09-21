// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
// 脚本面的 @Doc/@Param 文案两侧本就不同，探针类型 golden 会校验，别照抄到孪生文件。
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime client post-effect registry (feature 8b, client-side only; ported from Katton's
 * {@code ClientPostEffectManager}).
 *
 * <p><b>Runtime binding face (ticket 28).</b> {@link #set}/{@link #clear}/{@link #toggle}/
 * {@link #current} back the {@code PostEffects} runtime binding and keep their previous
 * caller-visible behaviour (client-thread renderer mutation, resource-backed ids only).
 *
 * <p><b>Declaration face (ticket 28).</b> Definitions are no longer registered straight from
 * a script binding: {@code ClientEvents.postEffects} declarations are collected into an inert
 * {@code PostEffectCandidatePlan} and only the commit point installs the active generation
 * through {@link #installGeneration}. A generation owns exactly the definitions it declared;
 * the previous generation's definitions, listeners and cached chains are released when the
 * new batch applies. Runtime-only ids still cannot be activated (that needs the
 * {@code ShaderManagerMixin} activation path), so {@link #set} refuses them explicitly
 * instead of silently rendering an empty frame.
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

    /**
     * An installed effect: validated chain config, the chain JSON it came from, and optional
     * custom GLSL sources. Only the active generation holds definitions.
     */
    public record Definition(PostChainConfig config, String chainJson,
                             Map<Identifier, String> fragmentShaders,
                             Map<Identifier, String> vertexShaders) {
    }

    // ---- declaration lifecycle (ClientEvents.postEffects；commit 点由 Adapter 调用) ----

    /**
     * Parses and validates a declaration payload without storing it (candidate preflight):
     * {@code null} is returned for a rejected payload so the Adapter can fail the whole batch
     * before any live state changes.
     */
    @Nullable
    public static Definition parseDefinition(Identifier id, String chainJson,
                                             Map<Identifier, String> fragmentShaders,
                                             Map<Identifier, String> vertexShaders) {
        PostChainConfig config = parseChainConfig(id, chainJson);
        if (config == null) return null;
        return new Definition(config, chainJson,
                Map.copyOf(fragmentShaders), Map.copyOf(vertexShaders));
    }

    /**
     * Installs one generation's definitions at the commit point and retires the rest: every
     * id in {@code retired} is dropped, its cached runtime chain closed. The previous
     * generation's definitions never survive the swap (no stale definition from an old
     * generation), and the swap is observable through {@link #activeGeneration()} /
     * {@link #definitionIds}.
     *
     * <p>Called by {@code PostEffectDomainOwner#publishPostEffects} on the client/owner thread.
     */
    public static void installGeneration(long generation, Map<Identifier, Definition> definitions,
                                         Set<Identifier> retired) {
        Set<Identifier> removed = new LinkedHashSet<>();
        for (Identifier id : List.copyOf(DEFINITIONS.keySet())) {
            if (!definitions.containsKey(id)) {
                removed.add(id);
            }
        }
        for (Identifier id : retired) {
            if (DEFINITIONS.containsKey(id)) {
                removed.add(id);
            }
        }
        for (Identifier id : removed) {
            DEFINITIONS.remove(id);
            dropCachedChains(id);
        }
        DEFINITIONS.putAll(definitions);
        ACTIVE_GENERATION = generation;
        LOGGER.info("Installed client post-effect generation {} ({} definition(s), {} retired)",
                generation, definitions.size(), removed.size());
    }

    /** Definitions currently installed (read-only; the declaration query face reads this). */
    public static Map<Identifier, Definition> installedDefinitions() {
        return Map.copyOf(DEFINITIONS);
    }

    /** Active declaration generation number ({@code -1} before the first commit). */
    public static long activeGeneration() {
        return ACTIVE_GENERATION;
    }

    /** Whether a definition is installed by the active generation. */
    public static boolean hasDefinition(Identifier id) {
        return DEFINITIONS.containsKey(id);
    }

    // ---- activation (vanilla-resource-backed effects; client-thread execution) ----

    /**
     * Activates the post effect {@code id}. Returns {@code false} when the id is only known
     * as a declaration without a resource (needs the shader-manager activation path) or the
     * id cannot be found.
     */
    public static boolean set(Identifier id) {
        if (isDeclarationOnly(id)) {
            LOGGER.warn("Post effect {} was declared without a post_effect resource and cannot be activated;"
                    + " add a resource-pack effect id instead", id);
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

    static boolean isDeclarationOnly(Identifier id) {
        return !isResourceAvailable(id) && DEFINITIONS.containsKey(id);
    }

    // ---- shader source serving (called by the ShaderManagerMixin) ----

    /**
     * Runtime GLSL source for a shader id installed by the active declaration generation.
     * The shader manager mixin injects at {@code ShaderManager#getShader} HEAD and returns
     * this when non-null.
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
    public static Map<Identifier, String> parseShaderMap(Map<String, String> raw) {
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

    // ---- post chain cache (keyed by the definition, reused across frames) ----

    private record CacheKey(Identifier id, Set<Identifier> allowedTargets) {
    }

    private static final Map<CacheKey, PostChain> POST_CHAIN_CACHE = new ConcurrentHashMap<>();

    /** Declaration generation whose definitions are installed ({@code -1} = none yet). */
    private static volatile long ACTIVE_GENERATION = -1L;

    /**
     * Loads (and caches) the runtime {@link PostChain} for an installed definition. The
     * shader manager mixin injects at {@code ShaderManager#getPostChain} HEAD and returns
     * this when non-null.
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

    /** Drops every cached chain for one id (retire path — releases the old generation's GPU resources). */
    private static void dropCachedChains(Identifier id) {
        for (Map.Entry<CacheKey, PostChain> entry : POST_CHAIN_CACHE.entrySet()) {
            if (!entry.getKey().id().equals(id)) continue;
            if (POST_CHAIN_CACHE.remove(entry.getKey()) != null) {
                closeQuietly(entry.getValue());
            }
        }
    }

    /** Drops cached runtime chains (resource reload / shutdown — the shader manager mixin calls this). */
    public static void invalidatePostChainCache() {
        for (PostChain chain : POST_CHAIN_CACHE.values()) {
            closeQuietly(chain);
        }
        POST_CHAIN_CACHE.clear();
    }

    private static void closeQuietly(PostChain chain) {
        try {
            chain.close();
        } catch (Exception ignored) {
        }
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
//?}
