// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.client.posteffect;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime client post-effect registry (feature 8b, client-side only; ported from Katton's
 * {@code ClientPostEffectManager}).
 *
 * <p><b>v1 split (no mixins in this batch):</b> vanilla's shader pipeline only knows post
 * chains declared by resource packs ({@code assets/<ns>/shaders/post/<path>.json}) at
 * resource-reload time, so definitions registered here at runtime <i>cannot be activated
 * yet</i>. v1 therefore ships full set/clear/toggle/current for any effect that exists as
 * a resource, plus {@link #register} which stores validated chain JSON so a pending
 * 1.21.1 post-chain mixin can activate it later without script changes.
 *
 * <p>All renderer mutations run on the client thread via {@code Minecraft#execute}.
 */
public final class PostEffectManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS PostEffects");

    /** 1.21.1 post-chain resources live at {@code assets/<ns>/shaders/post/<path>.json}. */
    private static final FileToIdConverter POST_EFFECT_FILES = FileToIdConverter.json("shaders/post");

    private static final Map<ResourceLocation, Definition> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile ResourceLocation lastSetId;

    private PostEffectManager() {
    }

    /** A runtime-registered effect: validated chain JSON (no GLSL source override on 1.21.1). */
    record Definition(String chainJson) {
    }

    // ---- registration (stored for the future post-chain mixin; not activatable in v1) ----

    public static boolean register(ResourceLocation id, String chainJson) {
        if (chainJson == null || chainJson.isBlank()) return false;
        try {
            JsonParser.parseString(chainJson);
        } catch (JsonParseException e) {
            NekoJS.LOGGER.warn("Failed to parse runtime client post effect {}", id, e);
            return false;
        }
        DEFINITIONS.put(id, new Definition(chainJson));
        LOGGER.info("Registered runtime client post effect {} (activation requires a pending 1.21.1 post-chain mixin)", id);
        return true;
    }

    public static boolean unregister(ResourceLocation id) {
        boolean removed = DEFINITIONS.remove(id) != null;
        if (removed && id.equals(current())) {
            clear();
        }
        return removed;
    }

    /** Clears every runtime-registered definition (script reload teardown). */
    public static void clearRegistered() {
        ResourceLocation current = current();
        DEFINITIONS.clear();
        if (current != null) {
            clear();
        }
    }

    public static boolean hasDefinition(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    // ---- activation (vanilla-resource-backed effects; client-thread execution) ----

    static ResourceLocation chainLocation(ResourceLocation id) {
        return id.withPath("shaders/post/" + id.getPath() + ".json");
    }

    /**
     * Activates the post effect {@code id}. Returns {@code false} when the id is only known
     * as a runtime definition (needs the pending mixin) or the id cannot be found.
     */
    public static boolean set(ResourceLocation id) {
        if (isRuntimeOnly(id)) {
            LOGGER.warn("Post effect {} was registered at runtime and cannot be activated until the 1.21.1 post-chain mixin lands; use a resource-pack effect id instead", id);
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

    static boolean isRuntimeOnly(ResourceLocation id) {
        return !isResourceAvailable(id) && DEFINITIONS.containsKey(id);
    }
}
