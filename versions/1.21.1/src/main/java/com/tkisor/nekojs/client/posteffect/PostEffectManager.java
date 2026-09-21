// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.client.posteffect;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
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
 * the previous generation's definitions and resources are released when the new batch
 * applies. 1.21.1 has no shader-source override, so a declaration is validated chain JSON
 * resolved through {@code assets/<ns>/shaders/post/<path>.json}.
 *
 * <p>All renderer mutations run on the client thread via {@code Minecraft#execute}.
 */
public final class PostEffectManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS PostEffects");

    private static final Map<ResourceLocation, Definition> DEFINITIONS = new ConcurrentHashMap<>();

    /** Declaration generation whose definitions are installed ({@code -1} = none yet). */
    private static volatile long ACTIVE_GENERATION = -1L;

    /** 1.21.1 无公开的 current effect getter，运行时用最后一次 set 的 id 记账。 */
    private static volatile ResourceLocation lastSetId;

    private PostEffectManager() {
    }

    /** An installed effect: validated chain JSON (no GLSL source override on 1.21.1). */
    public record Definition(String chainJson) {
    }

    // ---- declaration lifecycle (ClientEvents.postEffects；commit 点由 Adapter 调用) ----

    /**
     * Parses and validates a declaration payload without storing it (candidate preflight):
     * {@code null} is returned for a rejected payload so the Adapter can fail the whole batch
     * before any live state changes.
     */
    @Nullable
    public static Definition parseDefinition(ResourceLocation id, String chainJson) {
        if (chainJson == null || chainJson.isBlank()) return null;
        try {
            JsonParser.parseString(chainJson);
        } catch (JsonParseException e) {
            NekoJS.LOGGER.warn("Failed to parse runtime client post effect {}", id, e);
            return null;
        }
        return new Definition(chainJson);
    }

    /**
     * Installs one generation's definitions at the commit point and retires the rest: every
     * id in {@code retired} is dropped. The previous generation's definitions never survive
     * the swap, and the swap is observable through {@link #activeGeneration()} /
     * {@link #installedDefinitions()}.
     *
     * <p>Called by {@code PostEffectDomainOwner#publishPostEffects} on the client/owner thread.
     */
    public static void installGeneration(long generation, Map<ResourceLocation, Definition> definitions,
                                         Set<ResourceLocation> retired) {
        Set<ResourceLocation> removed = new LinkedHashSet<>();
        for (ResourceLocation id : List.copyOf(DEFINITIONS.keySet())) {
            if (!definitions.containsKey(id)) {
                removed.add(id);
            }
        }
        for (ResourceLocation id : retired) {
            if (DEFINITIONS.containsKey(id)) {
                removed.add(id);
            }
        }
        for (ResourceLocation id : removed) {
            DEFINITIONS.remove(id);
        }
        DEFINITIONS.putAll(definitions);
        ACTIVE_GENERATION = generation;
        boolean activeRetired = lastSetId != null && removed.contains(lastSetId);
        if (activeRetired) {
            clear();
        }
        LOGGER.info("Installed client post-effect generation {} ({} definition(s), {} retired)",
                generation, definitions.size(), removed.size());
    }

    /** Definitions currently installed (read-only; the declaration query face reads this). */
    public static Map<ResourceLocation, Definition> installedDefinitions() {
        return Map.copyOf(DEFINITIONS);
    }

    /** Active declaration generation number ({@code -1} before the first commit). */
    public static long activeGeneration() {
        return ACTIVE_GENERATION;
    }

    /** Whether a definition is installed by the active generation. */
    public static boolean hasDefinition(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    // ---- activation (vanilla-resource-backed effects; client-thread execution) ----

    static ResourceLocation chainLocation(ResourceLocation id) {
        return id.withPath("shaders/post/" + id.getPath() + ".json");
    }

    /**
     * Activates the post effect {@code id}. Returns {@code false} when the id is only known
     * as a declaration without a resource or the id cannot be found.
     */
    public static boolean set(ResourceLocation id) {
        if (isDeclarationOnly(id)) {
            LOGGER.warn("Post effect {} was declared without a shaders/post chain resource and cannot be"
                    + " activated; add a resource-pack effect id instead", id);
            return false;
        }
        if (!isResourceAvailable(id)) {
            LOGGER.warn("Post effect {} has no shaders/post chain resource; refusing to activate", id);
            return false;
        }
        ResourceLocation location = chainLocation(id);
        lastSetId = id;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.loadEffect(location));
        return true;
    }

    /** Deactivates any active post effect. */
    public static boolean clear() {
        lastSetId = null;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.shutdownEffect());
        return true;
    }

    /**
     * Toggles between {@code id} and no effect: activates when idle/different, clears when
     * {@code id} is currently active.
     */
    public static boolean toggle(ResourceLocation id) {
        if (id.equals(current())) {
            return clear();
        }
        return set(id);
    }

    /** Currently active effect id, or null (tracked via {@code lastSetId}; no public getter). */
    @Nullable
    public static ResourceLocation current() {
        if (lastSetId == null) return null;
        return Minecraft.getInstance().gameRenderer.currentEffect() != null ? lastSetId : null;
    }

    /**
     * Whether an effect is currently applied. {@code effectActive} is a private
     * {@code GameRenderer} field (no public getter), read via reflection.
     */
    public static boolean isActive() {
        Object gameRenderer = Minecraft.getInstance().gameRenderer;
        try {
            Field field = gameRenderer.getClass().getDeclaredField("effectActive");
            field.setAccessible(true);
            return Boolean.TRUE.equals(field.get(gameRenderer));
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Could not read GameRenderer.effectActive", e);
            return Minecraft.getInstance().gameRenderer.currentEffect() != null;
        }
    }

    /** True when the effect exists as a {@code shaders/post} resource in the active packs. */
    public static boolean isResourceAvailable(ResourceLocation id) {
        return Minecraft.getInstance().getResourceManager().getResource(chainLocation(id)).isPresent();
    }

    static boolean isDeclarationOnly(ResourceLocation id) {
        return !isResourceAvailable(id) && DEFINITIONS.containsKey(id);
    }

    /**
     * Loads (and caches) the runtime {@link PostChain} for an installed definition. Present
     * for parity with the 26.x shader-manager hook; the 1.21.1 post-chain mixin is still
     * pending, so nothing calls this yet.
     */
    @Nullable
    public static PostChain getOrCreatePostChain(ResourceLocation id) {
        return null;
    }
}
